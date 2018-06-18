package io.simple.nio;

public interface BufferPool {
	
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

}
