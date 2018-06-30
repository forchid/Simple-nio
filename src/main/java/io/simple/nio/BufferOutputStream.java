package io.simple.nio;

import io.simple.nio.store.FileRegion;
import io.simple.nio.store.FileStore;
import io.simple.util.ArrayQueue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A buffer backed output stream.
 * 
 * @author little-pan
 * @since 2018-06-17
 *
 */
public class BufferOutputStream extends OutputStream {
	final static Logger log = LoggerFactory.getLogger(BufferOutputStream.class);
	
	// stream owner
	protected final Session session;
	
	// buffer pool
	protected ArrayQueue<Buffer> localPool;
	private int remaining, maxBuffers, buffers;
	
	// file backed buffer
	protected LinkedList<FileRegion> regionPool;
	private Buffer regionBuffer;
	
	public BufferOutputStream(final Session session) {
		this.session   = session;
		this.regionPool= new LinkedList<FileRegion>();
		
		final Configuration config = session.config();
		setMaxBuffers(config.getMaxWriteBuffers());
	}
	
	public int getMaxBuffers() {
		return maxBuffers;
	}
	
	public void setMaxBuffers(int maxBuffers) {
		if(maxBuffers < 1) {
			throw new IllegalArgumentException("maxBuffers must bigger than 0: " + maxBuffers);
		}
		if(this.localPool == null) {
			this.localPool = new ArrayQueue<Buffer>(maxBuffers);
		}else {
			final ArrayQueue<Buffer> pool = this.localPool;
			final int cap = pool.capacity();
			if(maxBuffers > cap || (pool.size() < maxBuffers && maxBuffers < cap)) {
				this.localPool = ArrayQueue.drainQueue(pool, maxBuffers);
			}
		}
		this.maxBuffers = maxBuffers;
	}
	
	@Override
	public void write(int b) throws IOException {
		tailBuffer().put((byte)b);
		++remaining;
	}
	
	protected ByteBuffer tailBuffer() throws IOException {
		if(regionBuffer != null){
			return flushRegion();
		}
		
		final Buffer buf = localPool.peekLast();
		if(buf == null || !buf.byteBuffer().hasRemaining()) {
			if(buffers == maxBuffers-1){
				// Write HWM - switch to buffer store
				regionBuffer = session.alloc();
				++buffers;
				return regionBuffer.byteBuffer();
			}
			return allocBuffer().byteBuffer();
		}
		return buf.byteBuffer();
	}
	
	protected ByteBuffer flushRegion() throws IOException {
		final ByteBuffer b = regionBuffer.byteBuffer();
		if(b.hasRemaining()){
			return b;
		}
		
		FileRegion tailRegion = regionPool.peekLast();
		if(tailRegion == null || tailRegion.writeRemaining()==0){
			tailRegion = allocRegion();
		}
		b.flip();
		int regRem = tailRegion.writeRemaining();
		for(int n = 0; b.hasRemaining();){
			final int i = tailRegion.write(b);
			n += i;
			if(n >= regRem && b.hasRemaining()){
				n = 0;
				tailRegion = allocRegion();
				regRem = tailRegion.writeRemaining();
			}
		}
		b.clear();
		return b;
	}
	
	protected Buffer allocBuffer(){
		final Buffer newBuf = session.alloc();
		boolean failed = true;
		try {
			localPool.offer(newBuf);
			++buffers;
			failed = false;
			return newBuf;
		}finally {
			if(failed) {
				newBuf.release();
			}
		}
	}
	
	protected FileRegion allocRegion(){
		final FileStore store = session.bufferStore();
		final FileRegion region = store.allocate();
		boolean failed = true;
		try{
			regionPool.offer(region);
			failed = false;
			return region;
		}finally{
			if(failed){
				region.release();
			}
		}
	}
	
	@Override
	public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }
	
	@Override
	public void write(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                   ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        for (int i = 0 ; i < len ; i++) {
            write(b[off + i]);
        }
    }
	
	@Override
	public void flush() throws IOException {
		final Configuration config = session.config();
		final int spinCount = config.getWriteSpinCount();
		final SocketChannel chan = session.channel();
		for(int spins = 0; spins < spinCount;) {
			// Step-1. flush local buffers
			final Buffer buf = localPool.peek();
			if(buf == null) {
				if(regionBuffer != null){
					// Step-2. flush store buffers
					for(; spins < spinCount; ){
						final FileRegion region = regionPool.peek();
						if(region == null){
							// Step-3. flush region buffer
							final ByteBuffer buffer = regionBuffer.byteBuffer();
							buffer.flip();
							spins = flushBuffer(chan, buffer, spins, spinCount);
							if(buffer.remaining() != 0){
								buffer.compact();
								break;
							}
							// Write LWM - switch to local buffers
							regionBuffer.release();
							regionBuffer = null;
							--buffers;
							break;
						}
						int rem = region.readRemaining();
						for(;rem != 0 && spins < spinCount;){
							final int i = region.transferTo(rem, chan);
							if(i == 0){
								break;
							}
							++spins;
							remaining -= i;
							rem = region.readRemaining();
						}
						if(rem != 0){
							break;
						}
						regionPool.poll();
						region.release();
					}
				}
				break;
			}
			final ByteBuffer buffer = buf.byteBuffer();
			buffer.flip();
			spins = flushBuffer(chan, buffer, spins, spinCount);
			if(buffer.remaining() != 0) {
				buffer.compact();
				break;
			}
			localPool.poll();
			buf.release();
			--buffers;
		}
	}
	
	/**
	 * Flush the buffer in write spin count limit.
	 * 
	 * @param chan
	 * @param buffer
	 * @param spins
	 * @param spinCount
	 * 
	 * @return the new spin number
	 * 
	 * @throws IOException
	 */
	protected int flushBuffer(final SocketChannel chan, final ByteBuffer buffer, 
			int spins, final int spinCount) throws IOException {
		int rem = buffer.remaining();
		for(; rem != 0 && spins < spinCount;) {
			final int i = chan.write(buffer);
			if(i == 0) {
				break;
			}
			++spins;
			remaining -= i;
			rem = buffer.remaining();
		}
		return spins;
	}
	
	@Override
	public void close() throws IOException {
		releaseBuffers();
		session.channel().shutdownOutput();
    }
	
	protected void releaseBuffers() {
		for(;;) {
			final Buffer buf = localPool.poll();
			if(buf == null) {
				break;
			}
			buf.release();
		}
		for(;;){
			final FileRegion reg = regionPool.poll();
			if(reg == null){
				break;
			}
			reg.release();
		}
		if(regionBuffer != null){
			regionBuffer.release();
			regionBuffer = null;
		}
	}
	
	public int remaining() {
		return remaining;
	}
	
	public boolean hasRemaining() {
		return (remaining() > 0);
	}
	
}
