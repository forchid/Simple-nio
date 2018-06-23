package io.simple.nio;

public class BufferAllocateException extends RuntimeException {
	
	private static final long serialVersionUID = 6669782601918243631L;
	
	public BufferAllocateException() {
		this("Buffer allocation - exceeds buffer pool limit");
	}
	
	public BufferAllocateException(String message) {
		super(message);
	}
	
	public BufferAllocateException(String message, Throwable cause) {
		super(message, cause);
	}

}
