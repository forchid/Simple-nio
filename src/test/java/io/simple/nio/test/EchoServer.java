package io.simple.nio.test;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.BufferInputStream;
import io.simple.nio.BufferOutputStream;
import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.EventLoop;
import io.simple.nio.HandlerContext;
import io.simple.nio.Session;

public class EchoServer extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(EchoServer.class);
	
	public EchoServer() {
		
	}
	
	byte[] buf;
	int pos, count;
	
	public void onConnected(HandlerContext ctx){
		Session session = ctx.session();
		log.debug("{}: connected", session);
	}

	public void onRead(HandlerContext ctx, Object o) {
		final Session session = ctx.session();
		try {
			final BufferInputStream in = (BufferInputStream)o;
			int n = in.available();
			log.debug("{}: recv bytes {} ->", session, n);
			if(n == 0) {
				in.mark(1);
				int b = in.read();
				if(b == -1) {
					log.info("{}: Client closed", session);
					session.close();
					return;
				}
				in.reset();
				return;
			}
			buf = new byte[n];
			count = in.read(buf);
			if(count != n) {
				throw new IOException("Read bytes too short");
			}
			log.debug("{}: recv bytes {} <-", session, count);
			session.disableRead();
			session.enableWrite();
		} catch (IOException e) {
			onCause(ctx, e);
		}
	}

	public void onWrite(HandlerContext ctx, Object o) {
		final Session session = ctx.session();
		try {
			final BufferOutputStream out = (BufferOutputStream)o;
			out.write(buf, pos, count);
			session.flush();
		} catch (IOException e) {
			onCause(ctx, e);
		}
	}

	public void onFlushed(HandlerContext ctx) {
		final Session session = ctx.session();
		log.debug("{}: flush bytes {}", session, count);
		session.disableWrite();
		session.enableRead();
	}
	
	public static void main(String args[]) {
		final Configuration serverConfig = Configuration.newBuilder()
			.appendServerHandler(EchoServer.class)
			.setName("server-loop")
			.build();
		new EventLoop(serverConfig);
		
		Configuration clientConfig = Configuration.newBuilder()
			.appendClientHandler(EchoClient.class)
			.setName("client-loop")
			.build();
		new EventLoop(clientConfig);
	}
	
}
