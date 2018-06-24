package io.simple.nio;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkedBufferPool extends AbstractBufferPool {
	final static Logger log = LoggerFactory.getLogger(LinkedBufferPool.class);
	
	final LinkedList<Buffer> pool;
	
	public LinkedBufferPool(long poolSize) {
		this(poolSize, DEFAULT_BUFFER_SIZE);
	}
	
	public LinkedBufferPool(long poolSize, int bufferSize) {
		super(poolSize, bufferSize);
		this.pool = new LinkedList<Buffer>();
	}

	@Override
	public Buffer allocate() throws BufferAllocateException {
		final Buffer buffer = pool.poll();
		if(buffer != null) {
			if(log.isDebugEnabled()) {
				log.debug("{}: Allocate a buffer from pool - buffer#{} {}", this, buffer.hashCode(), buffer);
			}
			buffer.onAlloc();
			pooledSize -= bufferSize;
			return buffer;
		}
		return super.allocate();
	}
	
	@Override
	protected void doRelease(Buffer buffer) {
		pool.offer(buffer.clear());
		pooledSize += bufferSize;
		if(log.isDebugEnabled()) {
			log.debug("{}: Release a buffer into pool - buffer#{} {}", this, buffer.hashCode(), buffer);
		}
	}
	
	@Override
	public String toString() {
		return "LinkedPool";
	}

	@Override
	protected ByteBuffer doAllocate() {
		return ByteBuffer.allocateDirect(bufferSize);
	}

}
