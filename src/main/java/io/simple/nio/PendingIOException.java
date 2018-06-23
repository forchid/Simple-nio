package io.simple.nio;

import java.io.IOException;

/**
 * No byte readable exception in non-blocking channel.
 * 
 * @author little-pan
 * @since 2018-06-17
 *
 */
public class PendingIOException extends IOException {

	private static final long serialVersionUID = 1286505817151812529L;

	public PendingIOException() {
		this("IO pending");
	}
	
	public PendingIOException(String message) {
		super(message);
	}

}
