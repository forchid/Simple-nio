package io.simple.nio;

public interface EventHandler {
	
	/**
	 * A new connection established.
	 * 
	 * @param session
	 * @return true if call next handler
	 */
	boolean onConnected(Session session);
	
	boolean onRead(Session session, BufferInputStream in);

	boolean onWrite(Session session, BufferOutputStream out);
	
	boolean onFlushed(Session session);

	boolean onCause(Session session, Throwable cause);

}
