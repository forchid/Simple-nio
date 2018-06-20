package io.simple.nio;

public class EventHandlerAdapter implements EventHandler {
	
	protected EventHandlerAdapter() {}

	public void onConnected(HandlerContext ctx) {
		ctx.fireConnected();
	}

	public void onRead(HandlerContext ctx, Object in) {
		ctx.fireRead(in);
	}

	public void onWrite(HandlerContext ctx, Object out) {
		ctx.fireWrite(out);
	}

	public void onFlushed(HandlerContext ctx) {
		ctx.fireFlushed();
	}

	public void onCause(HandlerContext ctx, Throwable cause) {
		ctx.fireCause(cause);
	}

}
