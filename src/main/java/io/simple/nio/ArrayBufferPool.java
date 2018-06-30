package io.simple.nio;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.util.ArrayQueue;

public class ArrayBufferPool extends AbstractBufferPool {
	final static Logger log = LoggerFactory.getLogger(ArrayBufferPool.class);
	
	final ArrayQueue<Buffer> pool;
	
	public ArrayBufferPool(long poolSize) {
		this(poolSize, DEFAULT_BUFFER_SIZE);
	}
	
	public ArrayBufferPool(long poolSize, int bufferSize) {
		super(poolSize, bufferSize);
		long maxBuffers = poolSize / bufferSize;
		final long rem  = poolSize & (bufferSize-1);
		if(rem != 0L) {
			++maxBuffers;
		}
		if(maxBuffers > Integer.MAX_VALUE) {
			final long max = ((long)bufferSize) * (Integer.MAX_VALUE - (rem != 0L? 1: 0));
			final String error = String.format("poolSize can't larger than %d: %d", 
					max, poolSize);
			throw new IllegalArgumentException(error);
		}
		log.info("maxBuffers = {}", maxBuffers);
		this.pool = new ArrayQueue<Buffer>((int)(maxBuffers));
	}

	@Override
	public Buffer allocate() throws BufferAllocateException {
		checkNotClosed();
		
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
		return "ArrayPool";
	}

	@Override
	protected ByteBuffer doAllocate() {
		return ByteBuffer.allocateDirect(bufferSize);
	}
	
	@Override
	public void close(){
		pool.clear();
		pooledSize = 0L;
		super.close();
	}

}
