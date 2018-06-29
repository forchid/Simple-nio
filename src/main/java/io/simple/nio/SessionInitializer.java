package io.simple.nio;

/**
 * The session initializer that initialize session handlers before adding
 * server or client handler classes, which are added in configuration.
 * 
 * The initializer can configure session scope event handlers.
 * 
 * @author little-pan
 * @since 2018-06-29
 *
 */
public interface SessionInitializer {
	
	SessionInitializer NOOP = new SessionInitializer() {
		
		@Override
		public void initSession(Session session) {
			
		}
		
	};
	
	void initSession(Session session);
	
}
