package io.simple.nio;

public class SessionAllocateException extends RuntimeException {
	
	private static final long serialVersionUID = 6669782601918243631L;
	
	public SessionAllocateException() {
		this("Session allocation - exceeds max sessions limit");
	}
	
	public SessionAllocateException(String message) {
		super(message);
	}
	
	public SessionAllocateException(String message, Throwable cause) {
		super(message, cause);
	}

}
