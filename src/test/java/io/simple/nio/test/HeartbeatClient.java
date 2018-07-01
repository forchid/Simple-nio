package io.simple.nio.test;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.BufferInputStream;
import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.EventLoop;
import io.simple.nio.EventLoopListener;
import io.simple.nio.HandlerContext;
import io.simple.nio.IdleState;
import io.simple.nio.Session;
import io.simple.nio.SessionInitializer;

public class HeartbeatClient extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(HeartbeatClient.class);
	
	final byte[] ping = "ping".getBytes();
	
	@Override
	public void onRead(HandlerContext ctx, Object o) {
		final BufferInputStream in = (BufferInputStream)o;
		try {
			final int n = in.available();
			if(n < 4) {
				return;
			}
			
			final byte[] ping = new byte[4];
			in.read(ping);
			log.info("{}: recv heartbeat - {}", ctx.session(), new String(ping));
		}catch(IOException e) {
			onCause(ctx, e);
		}
	}
	
	@Override
	public void onUserEvent(HandlerContext ctx, Object ev) {
		log.info("{}: user event - {}", ctx.session(), ev);
		try {
			if(ev instanceof IdleState) {
				final IdleState state = (IdleState)ev;
				if(IdleState.WRITE_IDLE == state) {
					log.info("{}: write timeout  - close", ctx.session());
					ctx.close();
					return;
				}
				ctx.write(ping)
				.flush();
				return;
			}
		}catch(IOException e) {
			onCause(ctx, e);
		}
	}
	
	@Override
	public void onCause(HandlerContext ctx, Throwable cause) {
		log.warn(ctx.session()+": uncaught exception", cause);
		ctx.close();
	}
	
	static class ClientInitializer implements SessionInitializer {

		@Override
		public void initSession(final Session session) {
			session.addHandler(new HeartbeatClient());
		}
		
	}
	
	static class Connector extends EventLoopListener {
		
		@Override
		public void init(EventLoop eventLoop){
			for(int i = 0, n = 10; i < n; ++i){
				eventLoop.connect();
			}
		}
		
	}

	public static void main(String[] args) {
		Configuration config = Configuration.newBuilder()
			.setEventLoopListener(new Connector())
			.setClientInitializer(new ClientInitializer())
			.setName("heartbeat-client")
			.build();
		new EventLoop(config);
	}
	
}
