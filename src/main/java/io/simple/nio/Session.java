package io.simple.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
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
	protected final SocketChannel chan;
	protected final BufferInputStream  in;
	protected final BufferOutputStream out;
	private boolean flushing;
	
	final List<EventHandler> chain;
	
	public Session(SocketChannel chan, EventLoop eventLoop, long id) {
		this.chan = chan;
		this.eventLoop = eventLoop;
		this.config = eventLoop.getConfig();
		this.id     = id;
		this.name   = "session-"+id;
		this.chain  = new ArrayList<EventHandler>();
		final BufferPool pool = config.getBufferPool();
		this.in     = new BufferInputStream(chan, pool);
		this.out    = new BufferOutputStream(chan, pool);
	}
	
	public boolean isOpen() {
		return chan.isOpen();
	}
	
	public void close() {
		if(isOpen()) {
			log.debug("{}: closing", this);
			IoUtil.close(in);
			IoUtil.close(out);
			IoUtil.close(chan);
			log.debug("{}: closed", this);
		}
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public Session addHandler(final EventHandler handler) {
		chain.add(handler);
		return this;
	}
	
	public Session addHandlers(final List<EventHandler> handlers) {
		chain.addAll(handlers);
		return this;
	}
	
	public Session removeHandler(final EventHandler handler) {
		for(int i = 0, size = chain.size(); i < size; ++i) {
			final EventHandler h = chain.get(i);
			if(h.equals(handler)) {
				chain.remove(i);
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
	
	public final void flush() {
		flushing = true;
		if(out.hasRemaining()) {
			try {
				enableWrite();
				out.flush();
			} catch (IOException e) {
				disableWrite();
				onCause(e);
			}
			return;
		}
		flushing = false;
		onFlushed();
	}
	
	public void onConnected() {
		try {
			for(final EventHandler h : chain) {
				if(!h.onConnected(this)) {
					break;
				}
			}
		}catch(final Throwable cause) {
			onCause(cause);
		}
	}

	public void onRead() {
		for(final EventHandler h : chain) {
			if(!h.onRead(this, in)) {
				break;
			}
		}
	}

	public final void onWrite() {
		if(flushing) {
			flush();
			return;
		}
		for(final EventHandler h : chain) {
			if(!h.onWrite(this, out)) {
				break;
			}
		}
	}
	
	public void onFlushed() {
		for(final EventHandler h : chain) {
			if(!h.onFlushed(this)) {
				break;
			}
		}
	}
	
	public void onCause(Throwable cause) {
		for(final EventHandler h : chain) {
			if(!h.onCause(this, cause)) {
				break;
			}
		}
	}

}
