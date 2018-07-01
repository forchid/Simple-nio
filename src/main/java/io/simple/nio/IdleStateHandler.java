package io.simple.nio;

/**
 * <p>
 * Idle state event handler.
 * </p>
 * 
 * @author little-pan
 * @since 2018-07-01
 *
 */
public class IdleStateHandler extends EventHandlerAdapter {
	
	private TimeTask readTimeTask;
	private TimeTask writeTimeTask;
	
	private long readIdleTime;
	private long writeIdleTime;
	
	HandlerContext context;
	
	public IdleStateHandler(long readIdleTime, long writeIdleTime) {
		this.readIdleTime  = readIdleTime;
		this.writeIdleTime = writeIdleTime;
	}
	
	@Override
	public void onConnected(HandlerContext ctx) {
		if(this.context == null) {
			this.context = ctx;
			schedule(IdleState.READ_IDLE);
			schedule(IdleState.WRITE_IDLE);
		}
		ctx.fireConnected();
	}
	
	@Override
	public void onReadComplete(HandlerContext ctx) {
		try {
			if(readIdleTime <= 0L) {
				return;
			}
			final long cur = System.currentTimeMillis();
			readTimeTask.executeTime(cur + readIdleTime);
		} finally {
			ctx.fireReadComplete();
		}
	}
	
	@Override
	public void onFlushed(HandlerContext ctx) {
		try {
			if(writeIdleTime <= 0L) {
				return;
			}
			final long cur = System.currentTimeMillis();
			writeTimeTask.executeTime(cur + writeIdleTime);
		} finally {
			ctx.fireFlushed();
		}
	}
	
	final long readIdleTime() {
		return readIdleTime;
	}
	
	final IdleStateHandler readIdleTime(long idleTime) {
		this.readIdleTime = idleTime;
		schedule(IdleState.READ_IDLE);
		return this;
	}
	
	final long writeIdleTime() {
		return writeIdleTime;
	}
	
	final IdleStateHandler writeIdleTime(long idleTime) {
		this.writeIdleTime = idleTime;
		schedule(IdleState.WRITE_IDLE);
		return this;
	}
	
	protected void schedule(final IdleState state) {
		switch(state) {
		case READ_IDLE:
			if(readIdleTime > 0L) {
				if(this.readTimeTask == null) {
					this.readTimeTask  = new IdleTimeTask(this, state, readIdleTime, readIdleTime);
					context.session().schedule(this.readTimeTask);
				}else {
					final long cur = System.currentTimeMillis();
					this.readTimeTask.executeTime(cur + readIdleTime);
				}
			}else {
				if(this.readTimeTask != null) {
					this.readTimeTask.cancel();
					this.readTimeTask = null;
				}
			}
			break;
		case WRITE_IDLE:
			if(writeIdleTime > 0L) {
				if(this.writeTimeTask == null) {
					this.writeTimeTask  = new IdleTimeTask(this, state, writeIdleTime, writeIdleTime);
					context.session().schedule(this.writeTimeTask);
				}else {
					final long cur = System.currentTimeMillis();
					this.writeTimeTask.executeTime(cur + writeIdleTime);
				}
			}else {
				if(this.writeTimeTask != null) {
					this.writeTimeTask.cancel();
					this.writeTimeTask = null;
				}
			}
			break;
		default:
			throw new IllegalArgumentException("Unsupported state: " + state);
		}
	}
	
	// Read or write idle time task.
	static class IdleTimeTask extends TimeTask {
		
		final IdleStateHandler handler;
		final IdleState state;
		
		public IdleTimeTask(IdleStateHandler handler, IdleState state, long delay, long period) {
			super(delay, period);
			this.handler = handler;
			this.state   = state;
		}
		
		@Override
		public void run() {
			handler.onUserEvent(handler.context, state);
		}
		
	}

}
