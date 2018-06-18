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
	public boolean onConnected(Session session) {
		session.enableWrite();
		ts = System.currentTimeMillis();
		log.debug("{}: connected", session);
		return false;
	}
	
	@Override
	public boolean onRead(Session session, BufferInputStream in) {
		try {
			int n = in.available();
			log.debug("{}: avalable bytes {}", session, n);
			if(n < buf.length) {
				if(n == 0) {
					in.mark(1);
					if(in.read() == -1) {
						showTps(session);
						session.close();
						return false;
					}
					in.reset();
				}
				return false;
			}
			final byte[] buffer = new byte[buf.length];
			final int i = in.read(buffer);
			if(Arrays.equals(buffer, buf) == false) {
				throw new IOException("Protocol error: "+i);
			}
			bytes += buf.length;
			++tps;
		} catch (IOException e) {
			onCause(session, e);
		}
		return false;
	}
	
	@Override
	public boolean onWrite(Session session, BufferOutputStream out) {
		try {
			out.write(buf);
			session.flush();
			log.debug("{}: write buffer", session);
		} catch (final IOException e) {
			onCause(session, e);
		}
		return false;
	}
	
	@Override
	public boolean onFlushed(Session session) {
		log.debug("{}: flushed bytes {}", session, buf.length);
		bytes += buf.length;
		final long tm = System.currentTimeMillis() - ts;
		if(tm > 60000L) {
			showTps(session);
			session.close();
			return false;
		}
		return false;
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
