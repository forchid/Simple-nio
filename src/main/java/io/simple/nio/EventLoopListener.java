package io.simple.nio;

/**
 * The event loop listener that handles event loop life cycle events.
 * 
 * @author little-pan
 * @since 2018-06-27
 *
 */
public class EventLoopListener {
	
	public final static EventLoopListener NOOP = new EventLoopListener();
	
	protected EventLoopListener(){
		
	}
	
	public void init(EventLoop eventLoop){
		
	}

	
	public void destroy(EventLoop eventLoop){
		
	}
	
}
