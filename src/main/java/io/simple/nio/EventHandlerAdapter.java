package io.simple.nio;

public class EventHandlerAdapter implements EventHandler {
	
	protected EventHandlerAdapter() {}

	@Override
	public void onConnected(HandlerContext ctx) throws Exception {
		ctx.fireConnected();
	}

	@Override
	public void onRead(HandlerContext ctx, Object in) throws Exception {
		ctx.fireRead(in);
	}
	
	@Override
	public void onReadComplete(HandlerContext ctx) throws Exception {
		ctx.fireReadComplete();
	}

	@Override
	public void onWrite(HandlerContext ctx, Object out) throws Exception {
		ctx.fireWrite(out);
	}

	@Override
	public void onFlushed(HandlerContext ctx) throws Exception {
		ctx.fireFlushed();
	}
	
	@Override
	public void onUserEvent(HandlerContext ctx, Object ev) throws Exception {
		ctx.fireUserEvent(ev);
	}

	@Override
	public void onCause(HandlerContext ctx, Throwable cause) {
		ctx.fireCause(cause);
	}

}
