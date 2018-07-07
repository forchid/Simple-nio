package io.simple.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.EventLoop.SessionManager;
import io.simple.nio.store.FileStore;
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
	
	private final SessionManager sessManager;
	private int sessIndex = -1;
	
	// Base resources
	protected Selector selector;
	protected SelectionKey selectKey;
	protected SocketChannel chan;
	protected final BufferInputStream  in;
	protected final BufferOutputStream out;
	private boolean flushing;
	
	// Handler chain
	private final HeadContext head;
	private final TailContext tail;
	
	// time task queue
	private final LinkedList<TimeTask> timeTasks;
	private IdleStateHandler timeoutHandler;
	
	public Session(final String namePrefix, final long id, 
			SessionManager sessManager, SocketChannel chan, EventLoop eventLoop) {
		this.chan = chan;
		this.eventLoop = eventLoop;
		this.config = eventLoop.getConfig();
		this.id     = id;
		this.name   = String.format("%s-%d", namePrefix, id);
		this.sessManager = sessManager;
		
		// chain
		this.head   = new HeadContext(this);
		this.tail   = new TailContext(this);
		this.head.next = this.tail;
		this.tail.prev = this.head;
		
		// buffers
		this.in  = new BufferInputStream(this);
		this.out = new BufferOutputStream(this);
		
		this.timeTasks = new LinkedList<TimeTask>();
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
			sessManager.releaseSession(this, sessIndex);
			for(final TimeTask t : timeTasks) {
				t.cancel();
			}
			log.debug("{}: closed", this);
		}
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	final Session sessionIndex(int sessIndex){
		this.sessIndex = sessIndex;
		return this;
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
		if(handler == timeoutHandler) {
			throw new UnsupportedOperationException("Can't remove timeout handler");
		}
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
	
	public boolean isShutdown() {
		return eventLoop.isShutdown();
	}
	
	public EventLoop eventLoop() {
		return eventLoop;
	}
	
	public Configuration config(){
		return config;
	}
	
	public BufferPool bufferPool(){
		return config.getBufferPool();
	}
	
	public FileStore bufferStore(){
		return config.getBufferStore();
	}
	
	public SocketChannel channel(){
		return chan;
	}

	public Selector selector() {
		return selector;
	}

	public void selector(Selector selector) {
		this.selector = selector;
	}

	public SelectionKey selectKey() {
		return selectKey;
	}

	public void selectKey(SelectionKey selectKey) {
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
	
	final Session fireConnected() {
		head.fireConnected();
		return this;
	}
	
	final Session fireRead() throws Exception {
		head.fireRead(in);
		return this;
	}
	
	final Session fireReadComplete() throws Exception {
		head.fireReadComplete();
		return this;
	}
	
	final Session fireWrite() throws Exception {
		tail.fireWrite(out);
		return this;
	}
	
	final Session fireCause(Throwable cause) {
		head.fireCause(cause);
		return this;
	}
	
	/**
	 * Write byte array into output stream.
	 * @param b
	 * @return this session
	 * @throws IOException 
	 */
	public Session write(byte b[]) throws IOException {
		ensureOpen();
		out.write(b);
		return this;
	}
	
	final void ensureOpen() throws IOException {
		if(!isOpen()) {
			throw new ClosedChannelException();
		}
	}
	
	/**
	 * Write byte array into output stream.
	 * 
	 * @param b
	 * @param off
	 * @param len
	 * 
	 * @return this session
	 * @throws IOException 
	 */
	public Session write(byte b[], int off, int len) throws IOException {
		ensureOpen();
		out.write(b, off, len);
		return this;
	}
	
	/**
	 * Write byte buffer into output buffer stream.
	 * 
	 * @param buf
	 * @return this session
	 * @throws IOException 
	 */
	public Session write(ByteBuffer buf) throws IOException {
		ensureOpen();
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
	 * @throws IOException 
	 */
	public Session write(ByteBuffer buf, int off, int len) throws IOException {
		ensureOpen();
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
	 * @throws Exception 
	 */
	public final void flush() throws Exception {
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
	
	public Session cancel(final TimeTask task) {
		try {
			final Iterator<TimeTask> i = timeTasks.iterator();
			for(;i.hasNext();) {
				final TimeTask t = i.next();
				if(t == task) {
					i.remove();
					break;
				}
			}
			return this;
		}finally {
			task.cancel();
		}
	}
	
	public Session schedule(TimeTask task) {
		eventLoop.schedule(task);
		timeTasks.offer(task);
		return this;
	}
	
	public long readTimeout() {
		return timeoutHandler.readIdleTime();
	}
	
	public Session readTimeout(long readTimeout) {
		timeoutHandler.readIdleTime(readTimeout);
		return this;
	}
	
	public long writeTimeout() {
		return timeoutHandler.writeIdleTime();
	}
	
	public Session writeTimeout(long writeTimeout) {
		timeoutHandler.writeIdleTime(writeTimeout);
		return this;
	}
	
	final void setTimeoutHandler(IdleStateHandler timeoutHandler) {
		if(this.timeoutHandler != null) {
			throw new IllegalStateException(this+": timeout handler existing");
		}
		this.timeoutHandler = timeoutHandler;
		addHandler(timeoutHandler);
	}
	
	static class HeadContext extends HandlerContext {
		
		final Session session;

		public HeadContext(Session session) {
			super(session, new EventHandlerAdapter());
			this.session = session;
		}
		
		@Override
		public void fireConnected() {
			try {
				next.handler.onConnected(next);
			} catch (final Throwable cause) {
				next.handler.onCause(next, cause);
			}
		}
		
		@Override
		public void fireRead(final Object in) throws Exception {
			final EventHandler handler = next.handler;
			if(in == null){
				handler.onRead(next, session.in);
				return;
			}
			handler.onRead(next, in);
		}
		
		@Override
		public void fireCause(final Throwable cause) {
			try {
				next.handler.onCause(next, cause);
			}catch(final Throwable e) {
				log.warn("Uncaught exception", e);
				session.close();
			}
		}
		
	}
	
	static class TailContext extends HandlerContext {
		
		final Session session;
		
		public TailContext(Session session){
			super(session, new TailHandler());
			this.session = session;
		}
		
		@Override
		public void fireWrite(final Object out) throws Exception {
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
		
		@Override
		public void onCause(HandlerContext ctx, final Throwable cause) {
			log.warn("Close session for uncaught exception", cause);
			ctx.close();
		}
		
	}

}
