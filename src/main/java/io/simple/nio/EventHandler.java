package io.simple.nio;

public interface EventHandler {
	
	/**
	 * A new connection established.
	 * 
	 * @param ctx handler context
	 * @return true if call next handler
	 */
	void onConnected(HandlerContext ctx);
	
	void onRead(HandlerContext ctx, Object in);

	void onWrite(HandlerContext ctx, Object out);
	
	void onFlushed(HandlerContext ctx);

	void onCause(HandlerContext ctx, Throwable cause);

}
