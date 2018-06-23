package io.simple.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.util.IoUtil;

/**
 * A wrapper of the socket channel, IO buffers and IO event source.
 * 
 * @author little-pan
 * @since 2018-06-17
 *
 */
public class Session implements Closeable {
	final static Logger log = LoggerFactory.getLogger(Session.class);
	
	public final static String ON_CAUSE = "onCause";
	
	// Base states
	public final long id;
	public final String name;
	
	// Base configurations
	protected final EventLoop eventLoop;
	protected final Configuration config;
	
	// Base resources
	protected Selector selector;
	protected SelectionKey selectKey;
	protected SocketChannel chan;
	protected final BufferInputStream  in;
	protected final BufferOutputStream out;
	private boolean flushing;
	
	// Handler chain
	private final HandlerContext head, tail;
	
	public Session(String namePrefix, long id, SocketChannel chan, EventLoop eventLoop) {
		this.chan = chan;
		this.eventLoop = eventLoop;
		this.config = eventLoop.getConfig();
		this.id     = id;
		this.name   = String.format("%s-%d", namePrefix, id);
		
		// chain
		this.head   = new HeadContext(this);
		this.tail   = new TailContext(this);
		this.head.next = this.tail;
		this.tail.prev = this.head;
		
		// buffers
		final BufferPool pool = config.getBufferPool();
		this.in     = new BufferInputStream(chan, pool);
		this.out    = new BufferOutputStream(chan, pool);
	}
	
	public boolean isOpen() {
		return (chan != null && chan.isOpen());
	}
	
	public void close() {
		if(isOpen()) {
			log.debug("{}: closing", this);
			IoUtil.close(in);
			IoUtil.close(out);
			IoUtil.close(chan);
			chan = null;
			log.debug("{}: closed", this);
		}
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public Session addHandler(final EventHandler handler) {
		final HandlerContext prev = tail.prev;
		final HandlerContext cur  = new HandlerContext(this, handler);
		prev.next = cur;
		cur.prev  = prev;
		cur.next  = tail;
		tail.prev = cur;
		return this;
	}
	
	public Session addHandlers(final List<EventHandler> handlers) {
		for(final EventHandler handler : handlers){
			addHandler(handler);
		}
		return this;
	}
	
	public Session removeHandler(final EventHandler handler) {
		HandlerContext ctx = this.head.next;
		for(; ctx != this.tail; ctx = ctx.next) {
			final EventHandler h = ctx.handler();
			if(h.equals(handler)) {
				final HandlerContext prev = ctx.prev;
				final HandlerContext next = ctx.next;
				prev.next = next;
				next.prev = prev;
				ctx.prev  = null;
				ctx.next  = null;
				break;
			}
		}
		return this;
	}
	
	public Buffer alloc()throws BufferAllocateException {
		return config.getBufferPool().allocate();
	}

	public Selector getSelector() {
		return selector;
	}

	public void setSelector(Selector selector) {
		this.selector = selector;
	}

	public SelectionKey getSelectKey() {
		return selectKey;
	}

	public void setSelectKey(SelectionKey selectKey) {
		this.selectKey = selectKey;
	}

	public Session enableRead() {
		return registerOps(SelectionKey.OP_READ, false);
	}
	
	public Session enableWrite() {
		return registerOps(SelectionKey.OP_WRITE, false);
	}
	
	public Session disableRead() {
		return registerOps(SelectionKey.OP_READ, true);
	}
	
	public Session disableWrite() {
		return registerOps(SelectionKey.OP_WRITE, true);
	}
	
	protected Session registerOps(int ops, boolean disable) {
		try {
			if(selectKey == null) {
				if(disable) {
					return this;
				}
			}else {
				if(disable) {
					ops = (~ops) & selectKey.interestOps();
				}else {
					ops = (ops)  | selectKey.interestOps();
				}
			}
			selectKey = chan.register(selector, ops, this);
		} catch (ClosedChannelException e) {
			log.warn("Channel closed", e);
		}
		return this;
	}
	
	public Session fireConnected(){
		head.fireConnected();
		return this;
	}
	
	public Session fireRead() {
		head.fireRead(in);
		return this;
	}
	
	public Session fireWrite(){
		tail.fireWrite(out);
		return this;
	}
	
	public Session fireCause(Throwable cause) {
		head.fireCause(cause);
		return this;
	}
	
	/**
	 * Write byte array into output stream.
	 * @param b
	 * @return this session
	 */
	public Session write(byte b[]) {
		out.write(b);
		return this;
	}
	
	/**
	 * Write byte array into output stream.
	 * 
	 * @param b
	 * @param off
	 * @param len
	 * 
	 * @return this session
	 */
	public Session write(byte b[], int off, int len) {
		out.write(b, off, len);
		return this;
	}
	
	/**
	 * Write byte buffer into output buffer stream.
	 * 
	 * @param buf
	 * @return this session
	 */
	public Session write(ByteBuffer buf) {
		for(;buf.hasRemaining();) {
			out.write(buf.get());
		}
		return this;
	}
	
	/**
	 * Write byte buffer into output buffer stream.
	 * 
	 * @param buf
	 * @param off
	 * @param len
	 * 
	 * @return this session
	 */
	public Session write(ByteBuffer buf, int off, int len) {
		for(;off < len;) {
			out.write(buf.get(off++));
		}
		return this;
	}
	
	/**
	 * <p>
	 * Flush output buffer stream into the socket channel. First enable channel write, then
	 * flush stream, and disable channel write after flushing completely.
	 * </p>
	 */
	public final void flush() {
		flushing = true;
		if(out.hasRemaining()) {
			try {
				enableWrite();
				out.flush();
			} catch (IOException e) {
				disableWrite();
				head.fireCause(e);
			}
			return;
		}
		disableWrite();
		flushing = false;
		head.fireFlushed();
	}
	
	static class HeadContext extends HandlerContext {
		
		final Session session;

		public HeadContext(Session session) {
			super(session, new EventHandlerAdapter());
			this.session = session;
		}
		
		public void fireRead(final Object in){
			final EventHandler handler = next.handler;
			if(in == null){
				handler.onRead(next, session.in);
				return;
			}
			handler.onRead(next, in);
		}
		
	}
	
	static class TailContext extends HandlerContext {
		
		final Session session;
		
		public TailContext(Session session){
			super(session, new TailHandler());
			this.session = session;
		}
		
		public void fireWrite(final Object out){
			if(session.flushing){
				session.flush();
				return;
			}
			final EventHandler handler = prev.handler;
			if(out == null){
				handler.onWrite(prev, session.out);
				return;
			}
			handler.onWrite(prev, out);
		}
		
	}

	static class TailHandler extends EventHandlerAdapter {
		
		final static Logger log = LoggerFactory.getLogger(TailHandler.class);

		public TailHandler() {
			
		}
		
		public void onCause(HandlerContext ctx, final Throwable cause) {
			log.warn("Close session for uncaught exception", cause);
			ctx.close();
		}
		
	}

}
