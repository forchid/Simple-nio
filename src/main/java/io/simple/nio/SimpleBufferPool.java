package io.simple.nio;

import java.nio.ByteBuffer;

public class SimpleBufferPool extends AbstractBufferPool {
	
	public SimpleBufferPool(long poolSize) {
		super(poolSize);
	}
	
	public SimpleBufferPool(long poolSize, int bufferSize) {
		super(poolSize, bufferSize);
	}
	
	@Override
	public String toString() {
		return "SimplePool";
	}

	@Override
	protected ByteBuffer doAllocate() {
		return ByteBuffer.allocate(bufferSize);
	}

}
