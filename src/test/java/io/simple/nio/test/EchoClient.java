package io.simple.nio.test;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.BufferInputStream;
import io.simple.nio.BufferOutputStream;
import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.EventLoop;
import io.simple.nio.HandlerContext;
import io.simple.nio.Session;

public class EchoClient extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(EchoClient.class);
	
	final byte []buf = 
			( "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"/*
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
			+ "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"*/).getBytes();
	long ts, bytes, tps;
	
	public EchoClient() {}
	
	@Override
	public void onConnected(HandlerContext ctx) {
		ctx.enableWrite();
		ts = System.currentTimeMillis();
		log.debug("{}: connected", ctx.session());
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
					in.mark(1);
					if(in.read() == -1) {
						showTps(session);
						session.close();
						return;
					}
					in.reset();
				}
				return;
			}
			final byte[] buffer = new byte[buf.length];
			final int i = in.read(buffer);
			if(Arrays.equals(buffer, buf) == false) {
				throw new IOException("Protocol error: "+i);
			}
			bytes += buf.length;
			++tps;
		} catch (IOException e) {
			onCause(ctx, e);
		}
	}
	
	@Override
	public void onWrite(HandlerContext ctx, Object o) {
		try {
			final BufferOutputStream out = (BufferOutputStream)o;
			out.write(buf);
			ctx.flush();
			log.debug("{}: write buffer", ctx.session());
		} catch (final IOException e) {
			onCause(ctx, e);
		}
	}
	
	@Override
	public void onFlushed(HandlerContext ctx) {
		final Session session = ctx.session();
		log.debug("{}: flushed bytes {}", session, buf.length);
		bytes += buf.length;
		final long tm = System.currentTimeMillis() - ts;
		if(tm > 60000L) {
			showTps(session);
			session.close();
			return;
		}
		ctx.enableWrite();
	}
	
	void showTps(Session session) {
		final long tm = System.currentTimeMillis() - ts;
		log.info("{}: tranport bytes {}, tps {}", session, bytes, tps/(tm/1000L));
	}
	
	public static void main(String args[]) {
		for(int i = 0, n = 1; i < n; ++i){
			Configuration clientConfig = Configuration.newBuilder()
				.appendClientHandler(EchoClient.class)
				.setName("client-loop")
				.build();
			new EventLoop(clientConfig);
		}
	}
	
}
