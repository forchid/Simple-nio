package io.simple.nio.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.BufferInputStream;
import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.EventLoop;
import io.simple.nio.HandlerContext;
import io.simple.nio.Session;
import io.simple.nio.SessionInitializer;

public class HeartbeatServer extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(HeartbeatServer.class);
	
	final byte[] pong = "pong".getBytes();
	
	@Override
	public void onRead(HandlerContext ctx, Object o) throws Exception {
		final BufferInputStream in = (BufferInputStream)o;
		final int n = in.available();
		if(n < 4) {
			return;
		}
		
		final byte[] ping = new byte[4];
		in.read(ping);
		if(new String(ping).equalsIgnoreCase("ping")) {
			ctx.write(pong)
			.flush();
			return;
		}
		ctx.close();
	}
	
	@Override
	public void onUserEvent(HandlerContext ctx, Object ev) throws Exception {
		ctx.fireUserEvent(ev);
	}
	
	static class ServerInitializer implements SessionInitializer {

		@Override
		public void initSession(final Session session) {
			session.addHandler(new HeartbeatServer());
		}
		
	}

	public static void main(String[] args) {
		Configuration config = Configuration.newBuilder()
			.setServerInitializer(new ServerInitializer())
			.setName("heartbeat-server")
			.build();
		new EventLoop(config);
	}
	
}
