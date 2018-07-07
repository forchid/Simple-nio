package io.simple.nio;

public interface EventHandler {
	
	/**
	 * A new connection established.
	 * 
	 * @param ctx handler context
	 * @return true if call next handler
	 */
	void onConnected(HandlerContext ctx)throws Exception;
	
	void onRead(HandlerContext ctx, Object msg)throws Exception;
	
	/**
	 * Invoked after read more data from channel.
	 * @param ctx
	 */
	void onReadComplete(HandlerContext ctx)throws Exception;

	void onWrite(HandlerContext ctx, Object msg)throws Exception;
	
	void onFlushed(HandlerContext ctx)throws Exception;
	
	void onUserEvent(HandlerContext ctx, Object ev)throws Exception;

	void onCause(HandlerContext ctx, Throwable cause);

}
