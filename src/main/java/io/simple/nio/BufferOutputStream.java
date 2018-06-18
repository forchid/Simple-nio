package io.simple.nio;

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
	
	protected final SocketChannel chan;
	// buffer pool
	protected final BufferPool bufferPool;
	protected final LinkedList<Buffer> localPool;
	private int remaining;
	
	public BufferOutputStream(SocketChannel chan, BufferPool bufferPool) {
		this.chan = chan;
		this.bufferPool = bufferPool;
		this.localPool  = new LinkedList<Buffer>();
	}
	
	@Override
	public void write(int b) throws IOException {
		headBuffer().put((byte)b);
		++remaining;
	}
	
	protected ByteBuffer headBuffer() {
		final Buffer buf = localPool.peek();
		if(buf == null || !buf.byteBuffer().hasRemaining()) {
			final Buffer newBuf = bufferPool.allocate();
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
		for(;;) {
			final Buffer buf = localPool.peekLast();
			if(buf == null) {
				break;
			}
			final ByteBuffer buffer = buf.byteBuffer();
			buffer.flip();
			for(;buffer.hasRemaining();) {
				final int i = chan.write(buffer);
				if(i == 0) {
					break;
				}
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
		chan.shutdownOutput();
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