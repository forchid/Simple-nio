package io.simple.nio;

import java.io.IOException;

/**
 * No byte readable exception in non-blocking channel.
 * 
 * @author little-pan
 * @since 2018-06-17
 *
 */
public class IOPendingException extends IOException {

	private static final long serialVersionUID = 1286505817151812529L;

	public IOPendingException() {
		this("IO pending");
	}
	
	public IOPendingException(String message) {
		super(message);
	}

}
