package io.simple.nio;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.util.IoUtil;

public class EventLoop {
	final static Logger log = LoggerFactory.getLogger(EventLoop.class);
	
	protected final Configuration config;
	
	// state management
	private volatile boolean shutdown;
	private volatile boolean terminated;
	private final SelectorLoop selLoop;
	private final Thread selThread;
	
	// connection queue
	private final Queue<ConnectionRequest> connReqQueue;
	{
		connReqQueue = new LinkedList<ConnectionRequest>();
	}
	
	// time task queue
	private final LinkedList<TimeTask> timeTaskQueue = new LinkedList<TimeTask>();
	// used to avoid CME
	private final LinkedList<TimeTask> backTimeQueue = new LinkedList<TimeTask>();
	private boolean executingTimeTask;
	
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
	
	public boolean isTerminated(){
		return terminated;
	}
	
	public EventLoop shutdown() {
		this.shutdown = true;
		selLoop.selector.wakeup();
		return this;
	}
	
	/**
	 * Await the event loop terminated.
	 * 
	 * @throws InterruptedException if this current thread interrupted
	 */
	public void awaitTermination() throws InterruptedException {
		selThread.join();
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
	
	public void connect(long timeout) {
		connect(config.getHost(), config.getPort(), timeout);
	}
	
	public void connect(final String remoteHost, int remotePort) {
		connect(new InetSocketAddress(remoteHost, remotePort), config.getConnectTimeout());
	}
	
	public void connect(final String remoteHost, int remotePort, long timeout) {
		connect(new InetSocketAddress(remoteHost, remotePort), timeout);
	}
	
	public void connect(final SocketAddress remote) {
		connect(remote, config.getConnectTimeout());
	}
	
	public void connect(final SocketAddress remote, long timeout) {
		if(inEventLoop()){
			connReqQueue.add(new ConnectionRequest(remote, timeout));
			return;
		}
		throw new IllegalStateException("Not in event loop");
	}
	
	public void schedule(TimeTask task) {
		if(inEventLoop()){
			if(executingTimeTask) {
				backTimeQueue.add(task);
			}else {
				timeTaskQueue.add(task);
			}
			return;
		}
		throw new IllegalStateException("Not in event loop");
	}
	
	protected static ServerSocketChannel openServerChan(final Configuration config) {
		if(config.getServerInitializer() == null) {
			return null;
		}
		ServerSocketChannel chan = null;
		boolean failed = true;
		try {
			chan = ServerSocketChannel.open();
			chan.configureBlocking(false);
			chan.socket().setReuseAddress(true);
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
	
	final static SocketChannel openSocketChan(Selector selector, ConnectionRequest req) 
			throws IOException {
		SocketChannel chan = null;
		boolean failed = true;
		try {
			final int op = SelectionKey.OP_CONNECT;
			chan = SocketChannel.open();
			chan.configureBlocking(false);
			req.chan = chan;
			chan.register(selector, op, req);
			chan.connect(req.remote);
			failed = false;
			return chan;
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
	
	// Selector execute loop.
	final static class SelectorLoop implements Runnable {
		
		final EventLoop eventLoop;
		final Configuration config;
		
		private final ServerSocketChannel ssChan;
		private Selector selector;
		
		private SessionManager serverSessManager;
		private SessionManager clientSessManager;
		
		SelectorLoop(EventLoop eventLoop, Selector selector, ServerSocketChannel ssChan){
			this.eventLoop = eventLoop;
			this.config    = eventLoop.config;
			this.ssChan    = ssChan;
			this.selector  = selector;
			
			int maxServerConns = config.getMaxServerConns();
			if(ssChan == null) {
				maxServerConns = 0;
			}
			this.serverSessManager = new SessionManager(eventLoop, selector, "serverSess", 
					maxServerConns, config.getServerInitializer());
			
			int maxClientConns = config.getMaxClientConns();
			this.clientSessManager = new SessionManager(eventLoop, selector,  "clientSess", 
					maxClientConns, config.getClientInitializer());
		}

		public void run() {
			final long ts = System.currentTimeMillis();
			initChans();
			log.info("Started");
			
			final EventLoopListener listener = config.getEventLoopListener();
			try {
				listener.init(eventLoop);
				for(;;) {
					// 0. handle shutdown event
					final boolean shutdown = eventLoop.shutdown;
					if(shutdown) {
						// shutdown normally
						destroyChans();
						if(isCompleted()) {
							break;
						}
					}
					
					// 1. handle connect events
					if(!shutdown) {
						handleConnRequests();
					}
					
					// 2. handle file events
					final long nearest = nearestScheduleTime();
					final int events;
					final Selector sel = selector;
					if(nearest == -1L) {
						events = sel.select();
					}else if(nearest == 0L) {
						events = sel.selectNow();
					}else {
						events = sel.select(nearest);
					}
					if(events > 0) {
						final Iterator<SelectionKey> i = sel.selectedKeys().iterator();
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
					
					// 3. handle time events
					executeTimeTasks();
					
				}// loop
			} catch(final IOException e) {
				log.error("Selector loop severe error", e);
			} finally {
				cleanup();
				eventLoop.terminated = true;
				listener.destroy(eventLoop);
			}
			
			log.info("Terminated: uptime {}s", (System.currentTimeMillis() -ts)/1000);
		}
		
		final void executeTimeTasks() {
			final long cur = System.currentTimeMillis();
			final LinkedList<TimeTask> q = eventLoop.timeTaskQueue;
			final Iterator<TimeTask> i   = q.iterator();
			eventLoop.executingTimeTask  = true;
			try {
				for(; i.hasNext(); ) {
					final TimeTask task = i.next();
					if(task.isCancel()) {
						i.remove();
						continue;
					}
					final long tm = task.executeTime();
					if(tm <= cur) {
						try {
							task.run();
						} catch(final Throwable cause){
							log.debug("Time task execution error", cause);
						} finally {
							final long period = task.period();
							if(period <= 0L) {
								i.remove();
							}else {
								task.executeTime(tm + period);
							}
						}
					}
				}
			}finally {
				eventLoop.executingTimeTask = false;
			}
			
			// merge time tasks
			final LinkedList<TimeTask> b = eventLoop.backTimeQueue;
			for(;;) {
				final TimeTask t = b.poll();
				if(t == null) {
					break;
				}
				q.offer(t);
			}
			
		}
		
		final long nearestScheduleTime() {
			final long cur = System.currentTimeMillis();
			long nearest   = -1L;
			final Iterator<TimeTask> i = eventLoop.timeTaskQueue.iterator();
			for(; i.hasNext(); ) {
				final TimeTask task = i.next();
				if(task.isCancel()) {
					i.remove();
					continue;
				}
				final long tm = task.executeTime();
				if(tm <= cur) {
					nearest = 0L;
					break;
				}
				if(nearest == -1L || tm - cur < nearest) {
					nearest = tm - cur;
				}
			}
			return nearest;
		}
		
		final void cleanup(){
			destroyChans();
			eventLoop.connReqQueue.clear();
			eventLoop.timeTaskQueue.clear();
			config.getBufferStore().close();
			config.getBufferPool().close();
		}
		
		final void initChans() {
			try {
				if(ssChan != null) {
					final int accOp = SelectionKey.OP_ACCEPT;
					ssChan.register(selector, accOp);
				}
			} catch (ClosedChannelException e) {}
		}
		
		final void destroyChans(){
			if(ssChan == null){
				// client only
				return;
			}
			if(ssChan.isOpen()) {
				IoUtil.close(ssChan);
				log.info("Shutdown");
			}
		}
		
		final void handleConnRequests() {
			final Queue<ConnectionRequest> queue = eventLoop.connReqQueue;
			for(;;) {
				final ConnectionRequest req = queue.poll();
				if(req == null) {
					break;
				}
				SocketChannel chan = null;
				try {
					req.manager = clientSessManager;
					chan = openSocketChan(selector, req);
					if(req.timeout > 0L) {
						eventLoop.schedule(req);
					}
				} catch (final Throwable cause) {
					clientSessManager.allocateSession(chan, cause);
				}
			}
		}
		
		final boolean isCompleted() {
			return (serverSessManager.isCompleted() && clientSessManager.isCompleted());
		}
		
		final void onUncaught(SelectionKey selKey, final Throwable cause) {
			final Object attach = selKey.attachment();
			if(!(attach instanceof Session)){
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
		
		final void onServerConnect(final ServerSocketChannel ssChan){
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
		
		final void onClientConnect(final SelectionKey key) {
			final SocketChannel chan = (SocketChannel)key.channel();
			final Object attach = key.attachment();
			// Cancel connection timeout handler
			if(attach instanceof ConnectionRequest) {
				final ConnectionRequest req = (ConnectionRequest)attach;
				req.cancel();
				key.attach(null);
			}
			final Session sess = clientSessManager.allocateSession(chan);
			key.attach(sess);
			if(sess != null){
				sess.fireConnected();
			}
		}
		
		final void onRead(SelectionKey key) {
			final Session sess = (Session)key.attachment();
			sess.fireRead();
		}
		
		final void onWrite(SelectionKey key) {
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
		
		final SessionInitializer sessionInitializer;
		
		public SessionManager(EventLoop eventLoop, Selector selector, 
				String name, int maxConns, SessionInitializer sessionInitializer){
			this.eventLoop = eventLoop;
			this.selector  = selector;
			this.name      = name;
			this.sessions  = new Session[maxConns];
			
			if(sessionInitializer == null) {
				this.sessionInitializer = SessionInitializer.NOOP;
			}else {
				this.sessionInitializer = sessionInitializer;
			}
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
		final Session allocateSession(final SocketChannel chan) {
			return allocateSession(chan, null);
		}
		
		final Session allocateSession(final SocketChannel chan, final Throwable cause){
			final Configuration config = eventLoop.config;
			Session sess = null;
			try {
				sess = new Session(name, nextSessionId++, this, chan, eventLoop);
				
				// Read or write timeout handler.
				// @since 2018-07-01 little-pan
				final long readTimeout  = config.getReadTimeout();
				final long writeTimeout = config.getWriteTimeout();
				sess.setTimeoutHandler(new IdleStateHandler(readTimeout, writeTimeout));
				
				sessionInitializer.initSession(sess);
				if(cause != null) {
					sess.fireCause(cause);
					return null;
				}
			}catch(final Throwable e) {
				log.error("Initialize session error", e);
				IoUtil.close(sess);
				return null;
			}
			
			try {
				if(chan.isConnectionPending()){
					chan.finishConnect();
				}
				
				// Setting socket options
				// @since 2018-06-26 little-pan
				final Socket so = chan.socket();
				so.setTcpNoDelay(true);
				so.setKeepAlive(true);
				so.setReuseAddress(true);
				
				sess.selector(selector);
				if(config.isAutoRead()) {
					sess.enableRead();
				}
			} catch (final IOException e) {
				sess.fireCause(e);
				return null;
			}
			
			final int maxConns = sessions.length;
			if(maxIndex >= maxConns) {
				final String reason = String.format("%s allocation exceeds maxConns %d", 
						name, maxConns);
				sess.fireCause(new SessionAllocateException(reason));
				return null;
			}
			
			for(int i = 0; i < maxConns; ++i) {
				final Session s = sessions[i];
				if(s == null || !s.isOpen()) {
					sessions[i] = sess;
					sess.sessionIndex(i);
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
	
	// connection request and timeout handler.
	// @since 2018-07-01 little-pan
	static class ConnectionRequest extends TimeTask {
		final SocketAddress remote;
		final long timeout;
		
		SocketChannel chan;
		SessionManager manager;
		
		ConnectionRequest(SocketAddress remote, long timeout) {
			super(timeout, 0L);
			this.remote  = remote;
			this.timeout = timeout;
		}
		
		@Override
		public void run() {
			IoUtil.close(chan);
			final String error = "Connection timed out: remote " + remote;
			final SocketException cause = new ConnectException(error);
			manager.allocateSession(chan, cause);
			this.cancel();
		}
		
	}

}
