package io.simple.nio;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleBufferPool implements BufferPool {
	final static Logger log = LoggerFactory.getLogger(SimpleBufferPool.class);
	
	final long poolSize;
	private long curSize;
	final int bufferSize;
	
	public SimpleBufferPool(long poolSize) {
		this(poolSize, DEFAULT_BUFFER_SIZE);
	}
	
	public SimpleBufferPool(long poolSize, int bufferSize) {
		this.poolSize   = poolSize;
		this.bufferSize = bufferSize;
		if(poolSize <= 0L) {
			throw new IllegalArgumentException("poolSize must bigger than 0: " + poolSize);
		}
		log.debug("{}: poolSize = {}, bufferSize = {}", this, poolSize, bufferSize);
	}

	public Buffer allocate() throws BufferAllocateException {
		if(curSize + bufferSize > poolSize) {
			throw new BufferAllocateException("Exceeds pool size limit");
		}
		final ByteBuffer buf = ByteBuffer.allocate(bufferSize);
		curSize += bufferSize;
		final Buffer buffer = new Buffer(this, buf);
		buffer.onAlloc();
		log.debug("{}: Allocate a buffer from VM - {}", this, buffer);
		return buffer;
	}

	public void release(Buffer buffer) {
		if(buffer.bufferPool() == this) {
			buffer.onRelease();
			curSize -= bufferSize;
			log.debug("{}: Release a buffer into VM - {}", this, buffer);
			return;
		}
		log.warn("{}: buffer not allocated from this pool - {}", this, buffer);
	}
	
	public long available() {
		return (poolSize - curSize);
	}

	public long pooledSize() {
		return 0L;
	}
	
	@Override
	public String toString() {
		return "SimplePool";
	}

}
