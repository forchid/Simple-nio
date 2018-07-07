package io.simple.nio.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.HandlerContext;
import io.simple.nio.Session;
import io.simple.nio.SessionInitializer;

public class AdderServer extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(AdderServer.class);
	
	static final int PORT = Integer.parseInt(System.getProperty("port", "9696"));
	
	private Long a, b;
	
	public AdderServer() {
		
	}
	
	@Override
	public void onConnected(HandlerContext ctx){
		Session session = ctx.session();
		log.debug("{}: connected", session);
	}

	@Override
	public void onRead(HandlerContext ctx, Object o) throws Exception {
		if(a == null) {
			a = (Long)o;
			return;
		}
		b = (Long)o;
		
		ctx.fireWrite(a + b);
		ctx.flush();
		
		a = b = null;
	}
	
	@Override
	public void onCause(HandlerContext ctx, Throwable cause) {
		ctx.close();
	}
	
	static class ServerInitializer implements SessionInitializer {

		@Override
		public void initSession(Session session) {
			session.addHandler(new AdderCodec())
			.addHandler(new AdderServer());
		}
		
	}
	
	public static void main(String args[]) {
		Configuration.newBuilder()
			.setPort(PORT)
			.setServerInitializer(new ServerInitializer())
			.setName("adder-server")
			.boot();
	}
	
}
