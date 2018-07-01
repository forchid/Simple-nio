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
	
	/**
	 * Invoked after read more data from channel.
	 * @param ctx
	 */
	void onReadComplete(HandlerContext ctx);

	void onWrite(HandlerContext ctx, Object out);
	
	void onFlushed(HandlerContext ctx);
	
	void onUserEvent(HandlerContext ctx, Object ev);

	void onCause(HandlerContext ctx, Throwable cause);

}
