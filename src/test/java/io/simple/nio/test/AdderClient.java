package io.simple.nio.test;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.EventLoop;
import io.simple.nio.EventLoopListener;
import io.simple.nio.HandlerContext;
import io.simple.nio.Session;
import io.simple.nio.SessionInitializer;

public class AdderClient extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(AdderClient.class);
	
	static final String HOST = System.getProperty("host", "127.0.0.1");
	static final int PORT = Integer.parseInt(System.getProperty("port", "9696"));
	
	private final Random random = new Random();
	private Long a, b;
	
	private long ts, tps;
	
	public AdderClient() {}
	
	@Override
	public void onConnected(HandlerContext ctx) throws Exception {
		ts = System.currentTimeMillis();
		log.debug("{}: connected", ctx.session());
		
		callAdd(ctx);
	}
	
	@Override
	public void onRead(HandlerContext ctx, Object o) throws Exception {
		final Session session = ctx.session();
		
		final Long res = (Long)o;
		if(res != a + b) {
			log.warn("{}: add error - quit", session);
			ctx.close();
			return;
		}
		
		++tps;
		if(session.isShutdown()) {
			showTps(session);
			session.close();
			return;
		}
		callAdd(ctx);
	}
	
	protected void callAdd(HandlerContext ctx) throws Exception {
		a = random.nextLong();
		b = random.nextLong();
		
		ctx.fireWrite(a);
		ctx.fireWrite(b);
		
		ctx.flush();
	}
	
	void showTps(Session session) {
		final long tm = System.currentTimeMillis() - ts;
		log.info("{}: tps {}", session, tps/(tm/1000L));
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
			session.addHandler(new AdderCodec())
			.addHandler(new AdderClient());
		}
		
	}
	
	public static void main(String args[]) throws InterruptedException {
		final EventLoop eventLoop = Configuration.newBuilder()
			.setPort(PORT)
			.setHost(HOST)
			.setEventLoopListener(new Connector())
			.setClientInitializer(new ClientInitializer())
			.setName("adder-client")
			.setBufferDirect(true)
			.boot();
		
		Thread.sleep(1 * 60000L);
		log.info("Shutdown adder client");
		eventLoop.shutdown();
		eventLoop.awaitTermination();
	}
	
}
