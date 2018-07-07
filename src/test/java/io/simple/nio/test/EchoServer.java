package io.simple.nio.test;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.BufferInputStream;
import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.HandlerContext;
import io.simple.nio.Session;
import io.simple.nio.SessionInitializer;

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
	public void onRead(HandlerContext ctx, Object o) {
		final Session session = ctx.session();
		try {
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
			
		} catch (IOException e) {
			session.close();
		}
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
