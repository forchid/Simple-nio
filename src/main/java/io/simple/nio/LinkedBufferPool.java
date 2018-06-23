package io.simple.nio;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkedBufferPool implements BufferPool {
	final static Logger log = LoggerFactory.getLogger(LinkedBufferPool.class);
	
	final LinkedList<Buffer> pool;
	
	final long poolSize;
	private long curSize;
	private long pooledSize;
	final int bufferSize;
	
	public LinkedBufferPool(int poolSize) {
		this(poolSize, DEFAULT_BUFFER_SIZE);
	}
	
	public LinkedBufferPool(long poolSize, int bufferSize) {
		this.poolSize   = poolSize;
		this.bufferSize = bufferSize;
		this.pool       = new LinkedList<Buffer>();
		if(poolSize <= 0L) {
			throw new IllegalArgumentException("poolSize must bigger than 0: " + poolSize);
		}
		log.debug("{}: poolSize = {}, bufferSize = {}", this, poolSize, bufferSize);
	}

	public Buffer allocate() throws BufferAllocateException {
		Buffer buffer = pool.poll();
		if(buffer != null) {
			if(log.isDebugEnabled()) {
				log.debug("{}: Allocate a buffer from pool - buffer#{} {}", this, buffer.hashCode(), buffer);
			}
			buffer.onAlloc();
			pooledSize -= bufferSize;
			return buffer;
		}
		if(curSize + bufferSize > poolSize) {
			throw new BufferAllocateException("Exceeds pool size limit");
		}
		final ByteBuffer buf = ByteBuffer.allocateDirect(bufferSize);
		curSize += bufferSize;
		buffer   = new Buffer(this, buf);
		buffer.onAlloc();
		if(log.isDebugEnabled()) {
			log.debug("{}: Allocate a buffer from VM - buffer#{} {}", this, buffer.hashCode(), buffer);
		}
		return buffer;
	}

	public void release(Buffer buffer) {
		if(buffer.bufferPool() == this) {
			pool.offer(buffer.clear());
			buffer.onRelease();
			curSize    -= bufferSize;
			pooledSize += bufferSize;
			if(log.isDebugEnabled()) {
				log.debug("{}: Release a buffer into pool - buffer#{} {}", this, buffer.hashCode(), buffer);
			}
			return;
		}
		log.warn("{}: buffer not allocated from this pool - {}", this, buffer);
	}
	
	public long available() {
		return (poolSize - curSize);
	}

	public long pooledSize() {
		return pooledSize;
	}
	
	@Override
	public String toString() {
		return "LinkedPool";
	}

}
