package io.simple.nio;

/**
 * The idle states.
 * 
 * @author little-pan
 * @since 2018-07-01
 *
 */
public enum IdleState {
	
	/**
	 * Read idle state.
	 */
	READ_IDLE("Read idle"),
	/**
	 * Write idle state.
	 */
	WRITE_IDLE("Write idle");
	
	public final String name;
	
	private IdleState(String name) {
		this.name = name;
	}
	
}
