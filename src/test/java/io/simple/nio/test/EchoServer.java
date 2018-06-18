package io.simple.nio.test;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.nio.BufferInputStream;
import io.simple.nio.BufferOutputStream;
import io.simple.nio.Configuration;
import io.simple.nio.EventHandlerAdapter;
import io.simple.nio.EventLoop;
import io.simple.nio.Session;

public class EchoServer extends EventHandlerAdapter {
	final static Logger log = LoggerFactory.getLogger(EchoServer.class);
	
	public EchoServer() {
		
	}
	
	byte[] buf;
	int pos, count;

	public boolean onRead(Session session, BufferInputStream in) {
		try {
			int n = in.available();
			log.debug("{}: recv bytes {} ->", session, n);
			if(n == 0) {
				in.mark(1);
				int b = in.read();
				if(b == -1) {
					log.info("{}: Client closed", session);
					session.close();
					return false;
				}
				in.reset();
				return false;
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
			onCause(session, e);
		}
		return false;
	}

	public boolean onWrite(Session session, BufferOutputStream out) {
		try {
			out.write(buf, pos, count);
			session.flush();
		} catch (IOException e) {
			onCause(session, e);
		}
		return false;
	}

	public boolean onFlushed(Session session) {
		log.debug("{}: flush bytes {}", session, count);
		session.disableWrite();
		session.enableRead();
		return false;
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
