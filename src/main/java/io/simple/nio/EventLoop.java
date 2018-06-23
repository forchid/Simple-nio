package io.simple.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
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
	
	// state management
	private volatile boolean shutdown;
	private volatile boolean stop;
	private final SelectorLoop selLoop;
	private final Thread selThread;
	
	// connection queue
	private final Queue<SocketAddress> externConnReqQueue;
	{
		externConnReqQueue = new ConcurrentLinkedQueue<SocketAddress>();
	}
	private final Queue<SocketAddress> internConnReqQueue;
	{
		internConnReqQueue = new LinkedList<SocketAddress>();
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
			this.selLoop = new SelectorLoop(this, selector, ssChan);
			this.selThread = new Thread(selLoop, name);
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
	
	public boolean isStop(){
		return stop;
	}
	
	public EventLoop shutdown() {
		this.shutdown = true;
		return this;
	}
	
	public final boolean inEventLoop(){
		return (Thread.currentThread() == selThread);
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
		if(inEventLoop()){
			internConnReqQueue.add(remote);
			return;
		}
		externConnReqQueue.add(remote);
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
		} catch (final IOException e) {
			log.warn("Can't open channel to "+remote, e);
		} finally {
			if(failed) {
				IoUtil.close(chan);
			}
		}
		return chan;
	}
	
	protected static Selector openSelector(final Configuration config) {
		try {
			return Selector.open();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	// Selector execute loop.
	final static class SelectorLoop implements Runnable {
		
		final EventLoop eventLoop;
		final Configuration config;
		
		private ServerSocketChannel ssChan;
		private Selector selector;
		
		private SessionManager serverSessManager;
		private SessionManager clientSessManager;
		
		SelectorLoop(EventLoop eventLoop, Selector selector, ServerSocketChannel ssChan){
			this.eventLoop = eventLoop;
			this.config    = eventLoop.config;
			this.ssChan    = ssChan;
			this.selector  = selector;
			this.serverSessManager = new SessionManager(eventLoop, selector, 
					"serverSess", (ssChan==null)?0:config.getMaxServerConns(), config.getServerHandlers());
			this.clientSessManager = new SessionManager(eventLoop, selector, 
					"clientSess", config.getMaxClientConns(), config.getClientHandlers());
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
						if(isCompleted()) {
							break;
						}
					}
					
					handleConnRequests();
					
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
								onServerConnect(ssChan);
								continue;
							}
							
							try {
								if(key.isConnectable()) {
									onClientConnect(key);
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
				log.error("Selector loop severe error", e);
			} finally {
				eventLoop.stop = true;
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
		
		final void handleConnRequests() {
			Queue<SocketAddress> queue = eventLoop.externConnReqQueue;
			for(;;) {
				final SocketAddress remote = queue.poll();
				if(remote == null) {
					break;
				}
				openSocketChan(selector, remote);
			}
			
			queue = eventLoop.internConnReqQueue;
			for(;;) {
				final SocketAddress remote = queue.poll();
				if(remote == null) {
					break;
				}
				openSocketChan(selector, remote);
			}
		}
		
		boolean isCompleted() {
			return (serverSessManager.isCompleted() && clientSessManager.isCompleted());
		}
		
		void onUncaught(SelectionKey selKey, final Throwable cause) {
			final Object attach = selKey.attachment();
			if(attach == null){
				final SelectableChannel chan = selKey.channel();
				IoUtil.close(chan);
				log.warn("Uncaught exception occurs", cause);
				return;
			}
			final Session sess = (Session)attach;
			final StackTraceElement[] stack = cause.getStackTrace();
			for(int j = 0, size = stack.length; j < size; ++j) {
				final StackTraceElement e = stack[j];
				final boolean isSub = Session.class.isAssignableFrom(e.getClass());
				if(isSub && Session.ON_CAUSE.equals(e.getMethodName())){
					log.warn("Event handler uncaught error", cause);
					IoUtil.close(sess);
					break;
				}
			}
			try {
				if(sess.isOpen()) {
					sess.fireCause(cause);
				}
			} catch(final Throwable e) {
				log.warn("onCause() handler error", e);
				IoUtil.close(sess);
			}
		}
		
		void onServerConnect(final ServerSocketChannel ssChan){
			SocketChannel chan = null;
			boolean failed = true;
			try{
				chan = ssChan.accept();
				if(chan == null){
					return;
				}
				chan.configureBlocking(false);
				failed = false;
			}catch(final IOException e){
				log.warn("Accept channel error", e);
				return;
			}finally{
				if(failed){
					IoUtil.close(chan);
				}
			}
			
			final Session sess = serverSessManager.allocateSession(chan);
			if(sess != null){
				sess.fireConnected();
			}
		}
		
		void onClientConnect(final SelectionKey key) {
			final SocketChannel chan = (SocketChannel)key.channel();
			final Session sess = clientSessManager.allocateSession(chan);
			if(sess != null){
				sess.fireConnected();
			}
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
	
	// Session pool manager.
	final static class SessionManager {
		final static Logger log = LoggerFactory.getLogger(SessionManager.class);
		
		final EventLoop eventLoop;
		final Selector selector;
		final String name;
		
		private final Session sessions[];
		private long nextSessionId;
		private int maxIndex;
		final List<Class<? extends EventHandler>> handlers;
		
		public SessionManager(EventLoop eventLoop, Selector selector, 
				String name, int maxConns, List<Class<? extends EventHandler>> handlers){
			this.eventLoop = eventLoop;
			this.selector  = selector;
			this.name      = name;
			this.sessions  = new Session[maxConns];
			this.handlers  = handlers;
		}
		
		public boolean isCompleted() {
			for(int i = 0; i < maxIndex; ++i){
				final Session sess = sessions[i];
				if(sess != null && sess.isOpen()){
					return false;
				}
			}
			return true;
		}

		/**
		 * Allocate a session for socket channel.
		 * 
		 * @param chan
		 * 
		 * @return the session, or null if failed
		 */
		final Session allocateSession(final SocketChannel chan){
			final Configuration config = eventLoop.config;
			Session sess = null;
			try {
				sess = new Session(name, nextSessionId++, this, chan, eventLoop);
				for(final Class<? extends EventHandler> c : handlers) {
					final EventHandler handler = ReflectUtil.newObject(c);
					sess.addHandler(handler);
				}
			}catch(final Throwable cause) {
				log.error("Add event handler error", cause);
				IoUtil.close(sess);
				return null;
			}
			
			try {
				if(chan.isConnectionPending()){
					chan.finishConnect();
				}
				sess.setSelector(selector);
				if(config.isAutoRead()) {
					sess.enableRead();
				}
			} catch (final IOException cause) {
				sess.fireCause(cause);
				return null;
			}
			
			final int maxConns = sessions.length;
			if(maxIndex >= maxConns) {
				final String reason = String
					.format("%s allocation exceeds maxConns %d", name, maxConns);
				sess.fireCause(new SessionAllocateException(reason));
				return null;
			}
			
			for(int i = 0; i < maxIndex; ++i) {
				final Session s = sessions[i];
				if(s == null || !s.isOpen()) {
					sessions[i] = sess;
					sess.setSessionIndex(i);
					if(i >= maxIndex) {
						++maxIndex;
					}
					log.debug("{}: allocate a session success at sessions[{}] - maxIndex = {}",
							                                                name, i, maxIndex);
					break;
				}
			}
			return sess;
		}

		final void releaseSession(final Session session, final int sessIndex) {
			if(sessIndex != -1){
				final Session sess = sessions[sessIndex];
				if(sess == session){
					sessions[sessIndex] = null;
					if(sessIndex == maxIndex - 1){
						--maxIndex;
					}
					log.debug("{}: release session {} at sessions[{}] - maxIndex = {}",
							                       name, session, sessIndex, maxIndex);
				}
			}
		}
		
	}

}
