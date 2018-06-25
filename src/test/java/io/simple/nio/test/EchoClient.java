package io.simple.nio.test;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.BufferInputStream;
import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.EventLoop;
import io.simple.nio.HandlerContext;
import io.simple.nio.Session;

public class EchoClient extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(EchoClient.class);
	
	final byte []buf = 
			( "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
			+ "abcdefghij").getBytes();
	long ts, bytes, tps;
	
	public EchoClient() {}
	
	@Override
	public void onConnected(HandlerContext ctx) {
		ts = System.currentTimeMillis();
		log.debug("{}: connected", ctx.session());
		
		// init
		ctx.write(buf)
		.flush();
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
					log.info("Peer closed");
					showTps(session);
					session.close();
				}
				return;
			}
			
			final byte[] buffer = new byte[buf.length];
			in.read(buffer);
			if(Arrays.equals(buffer, buf) == false) {
				throw new IOException("Protocol error: "+new String(buffer));
			}
			bytes += buf.length;
			
			// send
			ctx.write(buffer)
			.flush();
			
		} catch (IOException e) {
			onCause(ctx, e);
		}
	}
	
	@Override
	public void onFlushed(HandlerContext ctx) {
		final Session session = ctx.session();
		log.debug("{}: flushed bytes {}", session, buf.length);
		bytes += buf.length;
		++tps;
		final long tm = System.currentTimeMillis() - ts;
		if(tm > 60000L) {
			showTps(session);
			session.close();
			return;
		}
	}
	
	void showTps(Session session) {
		final long tm = System.currentTimeMillis() - ts;
		log.info("{}: tranport bytes {}, tps {}", session, bytes, tps/(tm/1000L));
	}
	
	public static void main(String args[]) {
		final Configuration config = Configuration.newBuilder()
				.appendClientHandler(EchoClient.class)
				.setName("echo-client")
				.build();
		final EventLoop eventLoop = new EventLoop(config);
		for(int i = 0, n = 1000; i < n; ++i){
			eventLoop.connect();
		}
	}
	
}
