package io.simple.nio;

import java.io.Closeable;

public interface BufferPool extends Closeable {
	
	int DEFAULT_BUFFER_SIZE = 1 << 13;
	
	Buffer allocate()throws BufferAllocateException;
	
	void release(Buffer buffer);
	
	/**
	 * @return available bytes
	 */
	long available();
	
	/**
	 * @return pooled bytes
	 */
	long pooledSize();
	
	int bufferSize();
	
	/**
	 * @return the buffer size shift number
	 */
	int bufferSizeShift();
	
	boolean isOpen();
	
	@Override
	void close();

}
