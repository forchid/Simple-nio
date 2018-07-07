# Simple-nio
A simple java nio framework:
>
> 1. An event driven and nonblocking networking framework.
> 2. Single thread execution mode for performation and simplicity.
> 3. Byte stream style IO for simple buffer access and management.
> 4. Single event loop that supports server and client channels.
> 5. Support channel read and write HWM for rate limit.
> 6. Support timing events such as read, write and connect idle.
> 7. Provide a codec framework for message read and write.

# Sample code
EchoClient.java
```
public class EchoClient extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(EchoClient.class);
	
	static final String HOST = System.getProperty("host", "127.0.0.1");
	static final int PORT = Integer.parseInt(System.getProperty("port", "9696"));
	static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));
	
	final byte []buf = new byte[SIZE];
	long ts, bytes, tps;
	
	public EchoClient() {}
	
	@Override
	public void onConnected(HandlerContext ctx) throws Exception {
		ts = System.currentTimeMillis();
		log.debug("{}: connected", ctx.session());
		
		// init
		for(int i = 0; i < buf.length; ++i){
			buf[i] = (byte)i;
		}
		ctx.write(buf)
		.flush();
	}
	
	@Override
	public void onRead(HandlerContext ctx, Object o) throws Exception {
		final Session session = ctx.session();
		final BufferInputStream in = (BufferInputStream)o;
		int n = in.available();
		log.debug("{}: avalable bytes {}", session, n);
		if(n < buf.length) {
			if(n == 0) {
				log.info("{}: Peer closed", session);
				showTps(session);
				session.close();
			}
			return;
		}
		
		final byte[] buffer = new byte[buf.length];
		for(int i = 0, size = n/buf.length; i < size; ++i){
			in.read(buffer);
			if(Arrays.equals(buffer, buf) == false) {
				throw new IOException("Protocol error: "+new String(buffer));
			}
			ctx.write(buffer);
			bytes += buf.length;
			++tps;
		}
		
		if(ctx.isShutdown()){
			log.info("{}: Client shutdown", session);
			showTps(session);
			session.close();
			return;
		}
		
		// send
		ctx.flush();
	}
	
	@Override
	public void onFlushed(HandlerContext ctx) {
		final Session session = ctx.session();
		bytes += buf.length;
		log.debug("{}: flushed - tranport bytes {}", session, bytes);
	}
	
	void showTps(Session session) {
		final long tm = System.currentTimeMillis() - ts;
		log.info("{}: tranport bytes {}, tps {}", session, bytes, tps/(tm/1000L));
	}
	
	static class Connector extends EventLoopListener {
		
		@Override
		public void init(EventLoop eventLoop){
			for(int i = 0, n = 10; i < n; ++i){
				eventLoop.connect();
			}
		}
		
	}
	
	static class ClientInitializer implements SessionInitializer {

		@Override
		public void initSession(final Session session) {
			session.addHandler(new EchoClient());
		}
		
	}
	
	public static void main(String args[]) throws InterruptedException {
		final EventLoop eventLoop = Configuration.newBuilder()
			.setPort(PORT)
			.setHost(HOST)
			.setEventLoopListener(new Connector())
			.setClientInitializer(new ClientInitializer())
			.setName("echo-client")
			.setBufferDirect(true)
			.boot();
		
		// Shutdown process
		// @since 2018-06-27 little-pan
		Thread.sleep(1 * 60000L);
		log.info("Shutdown echo client");
		eventLoop.shutdown();
		eventLoop.awaitTermination();
	}
	
}
```

EchoServer.java
```
public class EchoServer extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(EchoServer.class);
	
	static final int PORT = Integer.parseInt(System.getProperty("port", "9696"));
	
	public EchoServer() {
		
	}
	
	@Override
	public void onConnected(HandlerContext ctx){
		Session session = ctx.session();
		log.debug("{}: connected", session);
	}

	@Override
	public void onRead(HandlerContext ctx, Object o) throws Exception {
		final Session session = ctx.session();
		final BufferInputStream in = (BufferInputStream)o;
		final int n = in.available();
		log.debug("{}: recv bytes {} ->", session, n);
		if(n == 0) {
			session.close();
			return;
		}
		final byte[] buf = new byte[n];
		final int count  = in.read(buf);
		if(count != n) {
			throw new IOException("Read bytes too short");
		}
		log.debug("{}: recv bytes {} <-", session, count);
		
		// echo
		ctx.write(buf)
		.flush();
	}
	
	static class ServerInitializer implements SessionInitializer {

		@Override
		public void initSession(Session session) {
			session.addHandler(new EchoServer());
		}
		
	}
	
	public static void main(String args[]) {
		Configuration.newBuilder()
			.setPort(PORT)
			.setServerInitializer(new ServerInitializer())
			.setName("echo-server")
			.boot();
	}
	
}
