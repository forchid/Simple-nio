package io.simple.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.util.IoUtil;
import io.simple.util.ReflectUtil;

public class EventLoop {
	final static Logger log = LoggerFactory.getLogger(EventLoop.class);
	
	protected final Configuration config;
	// state
	private volatile boolean shutdown;
	final SelectLoop selLoop;
	private final Queue<SocketAddress> connReqQueue;
	{
		connReqQueue = new ConcurrentLinkedQueue<SocketAddress>();
	}
	
	public EventLoop(final Configuration config) {
		ServerSocketChannel ssChan = null;
		Selector selector = null;
		boolean failed = true;
		this.config = config;
		try {
			ssChan   = openServerChan(config);
			selector = openSelector(config);
			final String name = config.getName();
			this.selLoop = new SelectLoop(this, selector, ssChan);
			final Thread selThread   = new Thread(selLoop, name);
			selThread.setDaemon(config.isDaemon());
			selThread.start();
			failed = false;
		}finally {
			if(failed) {
				IoUtil.close(selector);
				IoUtil.close(ssChan);
			}
		}
	}
	
	public Configuration getConfig() {
		return config;
	}
	
	public boolean isShutdown() {
		return shutdown;
	}
	
	public EventLoop shutdown() {
		this.shutdown = true;
		return this;
	}
	
	/**
	 * Connect to remote host using the host and port of the configuration.
	 */
	public void connect() {
		connect(config.getHost(), config.getPort());
	}
	
	public void connect(final String remoteHost, int remotePort) {
		connect(new InetSocketAddress(remoteHost, remotePort));
	}
	
	public void connect(final SocketAddress remote) {
		connReqQueue.add(remote);
		selLoop.selector.wakeup();
	}
	
	protected static ServerSocketChannel openServerChan(final Configuration config) {
		if(config.getServerHandlers().size() == 0) {
			return null;
		}
		ServerSocketChannel chan = null;
		boolean failed = true;
		try {
			chan = ServerSocketChannel.open();
			chan.configureBlocking(false);
			final String host = config.getHost();
			final int port = config.getPort();
			final SocketAddress local = new InetSocketAddress(host, port);
			chan.bind(local, config.getBacklog());
			log.info("listen on {}", local);
			failed = false;
			return chan;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			if(failed) {
				IoUtil.close(chan);
			}
		}
	}
	
	final static SocketChannel openSocketChan(Selector selector, final SocketAddress remote) {
		SocketChannel chan = null;
		boolean failed = true;
		try {
			chan = SocketChannel.open();
			chan.configureBlocking(false);
			chan.register(selector, SelectionKey.OP_CONNECT);
			chan.connect(remote);
			failed = false;
			return chan;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			if(failed) {
				IoUtil.close(chan);
			}
		}
	}
	
	protected static Selector openSelector(final Configuration config) {
		try {
			return Selector.open();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	// The selector execute loop.
	final static class SelectLoop implements Runnable {
		
		final EventLoop eventLoop;
		final Configuration config;
		
		private ServerSocketChannel ssChan;
		private Selector selector;
		
		private Session sessions[];
		private long nextSessionId;
		private int maxIndex;
		
		SelectLoop(EventLoop eventLoop, Selector selector, ServerSocketChannel ssChan){
			this.eventLoop = eventLoop;
			this.config    = eventLoop.config;
			this.ssChan    = ssChan;
			this.selector  = selector;
			this.sessions  = new Session[eventLoop.config.getMaxConns()];
		}

		public void run() {
			final long ts = System.currentTimeMillis();
			initChans();
			log.info("Started");
			
			try {
				for(;;) {
					if(eventLoop.shutdown) {
						// shutdown normally
						if(ssChan.isOpen()) {
							IoUtil.close(ssChan);
							log.info("Shutdown");
						}
						if(isOver()) {
							break;
						}
					}
					handleConnReqs();
					// do-select
					final int events = selector.select();
					if(events > 0) {
						final Iterator<SelectionKey> i = selector.selectedKeys().iterator();
						for(; i.hasNext(); i.remove()) {
							final SelectionKey key = i.next();
							
							if(!key.isValid()) {
								continue;
							}
							
							if(key.isAcceptable()) {
								onConnect(ssChan.accept(), true);
								continue;
							}
							
							try {
								if(key.isConnectable()) {
									onConnect((SocketChannel)key.channel(), false);
									continue;
								}
								
								if(key.isReadable()) {
									onRead(key);
								}
								
								if(key.isWritable()) {
									onWrite(key);
								}
							}catch(final Throwable cause) {
								onUncaught(key, cause);
							}
						}
					}
				}
			} catch(final IOException e) {
				log.error("Selector loop error", e);
			}
			
			log.info("Stopped: uptime %ds", (System.currentTimeMillis() -ts)/1000);
		}
		
		void initChans() {
			try {
				if(ssChan != null) {
					final int accOp = SelectionKey.OP_ACCEPT;
					ssChan.register(selector, accOp);
				}
			} catch (ClosedChannelException e) {}
		}
		
		final void handleConnReqs() {
			final Queue<SocketAddress> queue = eventLoop.connReqQueue;
			for(;;) {
				final SocketAddress remote = queue.poll();
				if(remote == null) {
					break;
				}
				openSocketChan(selector, remote);
			}
		}
		
		boolean isOver() {
			for(int i = 0; i < maxIndex; ++i) {
				final Session sess = sessions[i];
				if(sess != null && sess.isOpen()) {
					return false;
				}
				sessions[i] = null;
			}
			return true;
		}
		
		void onUncaught(SelectionKey selKey, final Throwable cause) {
			final Session sess = (Session)selKey.attachment();
			final StackTraceElement[] stack = cause.getStackTrace();
			for(int j = 0, size = stack.length; j < size; ++j) {
				final StackTraceElement e = stack[j];
				final boolean isSub = Session.class.isAssignableFrom(e.getClass());
				if(isSub && Session.ON_CAUSE.equals(e.getMethodName())){
					log.warn("Event handler error: close session", cause);
					IoUtil.close(sess);
					break;
				}
			}
			try {
				if(sess.isOpen()) {
					sess.fireCause(cause);
				}
			} catch(final Throwable e) {
				log.warn("onCause() handler error: close session", e);
				IoUtil.close(sess);
			}
		}
		
		void onConnect(SocketChannel chan, final boolean server) {
			final int maxConns = sessions.length;
			if(maxIndex >= maxConns) {
				IoUtil.close(chan);
				log.warn("Reject a new connection: Conns exceeds maxConns "+maxConns);
				return;
			}
			Session sess = null;
			for(int i = 0; i < maxConns; ++i) {
				sess = sessions[i];
				if(sess == null || !sess.isOpen()) {
					sessions[i] = sess = new Session(chan, eventLoop, nextSessionId++);
					sess.setSelector(selector);
					if(i >= maxIndex) {
						++maxIndex;
					}
					break;
				}
			}
			try {
				if(server) {
					chan.configureBlocking(false);
				}
				if(config.isAutoRead()) {
					sess.enableRead();
				}
				if(!server) {
					chan.finishConnect();
				}
			} catch (final IOException cause) {
				log.warn("Init session error: close session", cause);
				IoUtil.close(sess);
				return;
			}
			
			try {
				final List<Class<? extends EventHandler>> handlers;
				if(server) {
					handlers = config.getServerHandlers();
				}else {
					handlers = config.getClientHandlers();
				}
				for(final Class<? extends EventHandler> c : handlers) {
					final EventHandler handler = ReflectUtil.newObject(c);
					sess.addHandler(handler);
				}
			}catch(final Throwable cause) {
				log.warn("Add event handler error", cause);
				IoUtil.close(sess);
				return;
			}
			
			sess.fireConnected();
		}
		
		void onRead(SelectionKey key) {
			final Session sess = (Session)key.attachment();
			sess.fireRead();
		}
		
		void onWrite(SelectionKey key) {
			final Session sess = (Session)key.attachment();
			sess.fireWrite();
		}
		
	}

}
