package io.simple.nio;

import io.simple.nio.store.FileRegion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

/**
 * A buffer backed output stream.
 * 
 * @author little-pan
 * @since 2018-06-17
 *
 */
public class BufferOutputStream extends OutputStream {
	
	// stream owner
	protected final Session session;
	
	// buffer pool
	protected LinkedList<Buffer> localPool;
	private int remaining, maxBuffers;
	
	// file backed buffer
	protected LinkedList<FileRegion> regionPool;
	private Buffer storeBuffer;
	
	public BufferOutputStream(final Session session) {
		this.session   = session;
		this.localPool = new LinkedList<Buffer>();
		this.regionPool= new LinkedList<FileRegion>();
		
		final Configuration config = session.getConfig();
		setMaxBuffers(config.getWriteMaxBuffers());
	}
	
	public int getMaxBuffers() {
		return maxBuffers;
	}
	
	public void setMaxBuffers(int maxBuffers) {
		if(maxBuffers < 1) {
			throw new IllegalArgumentException("maxBuffers must bigger than 0: " + maxBuffers);
		}
		this.maxBuffers = maxBuffers;
	}
	
	@Override
	public void write(int b) throws IOException {
		headBuffer().put((byte)b);
		++remaining;
	}
	
	protected ByteBuffer headBuffer() throws IOException {
		if(storeBuffer != null){
			final ByteBuffer b = storeBuffer.byteBuffer();
			if(!b.hasRemaining()){
				FileRegion headRegion = regionPool.peek();
				if(headRegion == null || headRegion.capacity() - headRegion.writeIndex()==0){
					headRegion = session.getBufferStore().allocate();
					regionPool.offer(headRegion);
				}
				b.flip();
				int rrem = headRegion.capacity() - headRegion.writeIndex();
				for(int n = 0; b.hasRemaining();){
					final int i = headRegion.write(b);
					n += i;
					if(n >= rrem && b.hasRemaining()){
						n = 0;
						headRegion = session.getBufferStore().allocate();
						regionPool.offer(headRegion);
						rrem = headRegion.capacity();
					}
				}
				b.clear();
			}
			return b;
		}
		
		final Buffer buf = localPool.peek();
		if(buf == null || !buf.byteBuffer().hasRemaining()) {
			final Buffer newBuf = session.alloc();
			boolean failed = true;
			try {
				localPool.offerFirst(newBuf);
				failed = false;
				return newBuf.byteBuffer();
			}finally {
				if(failed) {
					newBuf.release();
				}
			}
		}
		return buf.byteBuffer();
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
		final Configuration config = session.getConfig();
		final int spinCount = config.getWriteSpinCount();
		final SocketChannel chan = session.getChannel();
		for(int spins = 0; spins < spinCount;) {
			final Buffer buf = localPool.peekLast();
			if(buf == null) {
				break;
			}
			final ByteBuffer buffer = buf.byteBuffer();
			buffer.flip();
			for(;buffer.hasRemaining() && spins < spinCount;) {
				final int i = chan.write(buffer);
				if(i == 0) {
					break;
				}
				++spins;
				remaining -= i;
			}
			if(buffer.hasRemaining()) {
				buffer.compact();
				break;
			}
			localPool.pollLast();
			buf.release();
		}
	}
	
	@Override
	public void close() throws IOException {
		releaseBuffers();
		session.getChannel().shutdownOutput();
    }
	
	protected void releaseBuffers() {
		for(;;) {
			final Buffer buf = localPool.poll();
			if(buf == null) {
				break;
			}
			buf.release();
		}
	}
	
	public int remaining() {
		return remaining;
	}
	
	public boolean hasRemaining() {
		return (remaining() > 0);
	}
	
}
