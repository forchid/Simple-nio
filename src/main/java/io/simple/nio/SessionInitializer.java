package io.simple.nio;

/**
 * <p>
 * The session initializer that initialize session handlers, configure 
 * session scope event handlers.
 * </p>
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
