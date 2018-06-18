package io.simple.nio;

import java.nio.ByteBuffer;

public class Buffer {
	
	final BufferPool pool;
	final ByteBuffer backed;
	
	private boolean released;
	
	public Buffer(final BufferPool pool, ByteBuffer backed) {
		this.pool   = pool;
		this.backed = backed;
		released    = true;
	}
	
	public Buffer onAlloc() {
		if(!released) {
			throw new IllegalStateException("Buffer not released");
		}
		released = false;
		return this;
	}
	
	public ByteBuffer byteBuffer() {
		if(released) {
			throw new IllegalStateException("Buffer released");
		}
		return backed;
	}
	
	public BufferPool bufferPool() {
		return pool;
	}
	
	public Buffer clear() {
		backed.clear();
		return this;
	}
	
	public void release() {
		pool.release(this);
	}
	
	public void onRelease() {
		released = true;
	}
	
	@Override
	public String toString() {
		return backed+"";
	}

}
