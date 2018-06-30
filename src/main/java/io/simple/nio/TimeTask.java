package io.simple.nio;

/**
 * <p>
 * The event loop time task.
 * </p>
 * 
 * @author little-pan
 * @since 2018-06-30
 *
 */
public abstract class TimeTask implements Runnable {
	
	private long executeTime;
	private long period;
	private boolean cancel;
	
	public TimeTask(long period) {
		this(0L, period);
	}
	
	public TimeTask(long delay, long period) {
		this.executeTime = System.currentTimeMillis();
		if(delay > 0L) {
			this.executeTime += delay;
		}
		this.period = period;
	}
	
	public long executeTime() {
		return executeTime;
	}
	
	public TimeTask executeTime(long executeTime) {
		this.executeTime = executeTime;
		return this;
	}
	
	public long period() {
		return period;
	}
	
	public boolean isCancel() {
		return cancel;
	}
	
	public void cancel() {
		this.cancel = true;
	}

}
