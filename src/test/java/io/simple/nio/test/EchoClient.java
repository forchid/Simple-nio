package io.simple.nio.test;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.BufferInputStream;
import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.EventLoop;
import io.simple.nio.EventLoopListener;
import io.simple.nio.HandlerContext;
import io.simple.nio.Session;
import io.simple.nio.SessionInitializer;

public class EchoClient extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(EchoClient.class);
	
	static final String HOST = System.getProperty("host", "127.0.0.1");
	static final int PORT = Integer.parseInt(System.getProperty("port", "9696"));
	static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));
	
	final byte []buf = new byte[SIZE];
	long ts, bytes, tps;
	
	public EchoClient() {}
	
	@Override
	public void onConnected(HandlerContext ctx) {
		ts = System.currentTimeMillis();
		log.debug("{}: connected", ctx.session());
		
		// init
		for(int i = 0; i < buf.length; ++i){
			buf[i] = (byte)i;
		}
		try {
			ctx.write(buf)
			.flush();
		} catch (IOException e) {
			ctx.close();
		}
	}
	
	@Override
	public void onRead(HandlerContext ctx, Object o) {
		final Session session = ctx.session();
		try {
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
			
		} catch (IOException e) {
			onCause(ctx, e);
		}
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
		public void initSession(Session session) {
			session.addHandler(new EchoClient());
		}
		
	}
	
	public static void main(String args[]) throws InterruptedException {
		Configuration config = Configuration.newBuilder()
				.setPort(PORT)
				.setHost(HOST)
				.setEventLoopListener(new Connector())
				.setClientInitializer(new ClientInitializer())
				.setName("echo-client")
				.setBufferDirect(true)
				.build();
		EventLoop eventLoop = new EventLoop(config);
		
		// Shutdown process
		// @since 2018-06-27 little-pan
		Thread.sleep(60000L);
		log.info("Shutdown echo client");
		eventLoop.shutdown();
		eventLoop.awaitTermination();
	}
	
}
