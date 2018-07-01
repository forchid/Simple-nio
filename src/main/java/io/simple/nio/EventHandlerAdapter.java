package io.simple.nio;

public class EventHandlerAdapter implements EventHandler {
	
	protected EventHandlerAdapter() {}

	@Override
	public void onConnected(HandlerContext ctx) {
		ctx.fireConnected();
	}

	@Override
	public void onRead(HandlerContext ctx, Object in) {
		ctx.fireRead(in);
	}
	
	@Override
	public void onReadComplete(HandlerContext ctx) {
		ctx.fireReadComplete();
	}

	@Override
	public void onWrite(HandlerContext ctx, Object out) {
		ctx.fireWrite(out);
	}

	@Override
	public void onFlushed(HandlerContext ctx) {
		ctx.fireFlushed();
	}
	
	@Override
	public void onUserEvent(HandlerContext ctx, Object ev) {
		ctx.fireUserEvent(ev);
	}

	@Override
	public void onCause(HandlerContext ctx, Throwable cause) {
		ctx.fireCause(cause);
	}

}
