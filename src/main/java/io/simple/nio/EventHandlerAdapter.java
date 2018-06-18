package io.simple.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.util.IoUtil;

public class EventHandlerAdapter implements EventHandler {
	
	final static Logger log = LoggerFactory.getLogger(EventHandlerAdapter.class);
	
	protected EventHandlerAdapter() {}

	public boolean onConnected(Session session) {
		
		return true;
	}

	public boolean onRead(Session session, BufferInputStream in) {
		
		return true;
	}

	public boolean onWrite(Session session, BufferOutputStream out) {
		
		return true;
	}

	public boolean onFlushed(Session session) {
		
		return true;
	}

	public boolean onCause(Session session, Throwable cause) {
		log.warn("Error occurs: close session", cause);
		IoUtil.close(session);
		return true;
	}

}
