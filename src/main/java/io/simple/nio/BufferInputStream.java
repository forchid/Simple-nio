package io.simple.nio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

/**
 * <p>
 * A buffer backed input stream, which supports mark and reset operations 
 *in local buffer pool.
 * </p>
 * 
 * <p>
 * <b>Note: </b> the {@link #read()} method throws {@link PendingIOException} 
 * when no one byte readable, in order to keep compatible with {@link java.io.InputStream}.
 * So suggest using the {@link #available()} method to get readable byte number, then using
 * {@link #read(byte[], int, int)} method to read byte data.
 * </p>
 * 
 * @author little-pan
 * @since 2018-06-17
 *
 */
public class BufferInputStream extends InputStream {
	
	// MAX_SKIP_BUFFER_SIZE is used to determine the maximum buffer size to
    // use when skipping.
    private static final int MAX_SKIP_BUFFER_SIZE = 2048;
	
	protected final SocketChannel chan;
	protected final BufferPool bufferPool;
	protected final LinkedList<Buffer> localPool;
	private int available;
	
	// mark support
	private int markPos = -1, readLimit;
	
	public BufferInputStream(SocketChannel chan, BufferPool bufferPool) {
		this.chan   = chan;
		this.bufferPool = bufferPool;
		this.localPool  = new LinkedList<Buffer>();
	}
	
	/**
	 * @throws io.simple.nio.PendingIOException 
	 *  if no byte readable
	 */
	@Override
	public int read() throws IOException {
		final ByteBuffer buffer = tailBuffer();
		if(!buffer.hasRemaining()) {
			final ByteBuffer b;
			localPool.pollLast().release();
			// next buffer
			final int n = available();
			b = tailBuffer();
			markPos = -1;
			if(n == 0) {
				// no byte readable or EOF
				b.clear();
				final int i = chan.read(b);
				b.flip();
				if(i == -1) {
					// EOF
					return -1;
				}
				if(i == 0) {
					// no byte readable!
					throw new PendingIOException();
				}
				available += i;
			}
			--available;
			return (0xFF & b.get());
		}
		if(markPos >= 0 && --readLimit <= 0) {
			markPos = -1;
		}
		--available;
		return (0xFF & buffer.get());
	}
	
	@Override
	public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }
	
	@Override
	public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int c = read();
        if (c == -1) {
            return c;
        }
        b[off] = (byte)c;
        
        final int size = Math.min(len, available()+1/*prev byte*/);
        int i = 1;
        for (; i < size ; i++) {
            c = read();
            if (c == -1) {
                break;
            }
            b[off + i] = (byte)c;
        }
        return i;
    }

	@Override
	public long skip(long n) throws IOException {
        long rem = Math.min(n, available());
        if (rem <= 0L) {
            return 0L;
        }

        final int size = (int)Math.min(MAX_SKIP_BUFFER_SIZE, rem);
        final byte[] skipBuffer = new byte[size];
        for (; rem > 0; ) {
            final int nr = read(skipBuffer, 0, (int)Math.min(size, rem));
            if (nr < 0) {
                break;
            }
            rem -= nr;
        }

        return n - rem;
    }
	
	@Override
	public int available() throws IOException {
		final ByteBuffer buffer = headBuffer();
		final int pos = buffer.position();
		final int lim = buffer.limit();
		
		// prepare for channel read
		if(pos < lim) {
			buffer.position(lim);
			buffer.limit(buffer.capacity());
		}else {
			buffer.clear();
		}
		ByteBuffer b = buffer;
		for(;;) {
			final int i = chan.read(b);
			// no byte readable or buffer full
			if(i <= 0 || !b.hasRemaining()) {
				// buffer changed to read state
				if(b == buffer && pos < lim) {
					// old data remaining
					buffer
					.limit(buffer.position())
					.position(pos);
				}else {
					b.flip();
				}
				if(i <= 0) {
					return available;
				}
				b = allocate();
				b.clear();
			}
			available += i;
		}
    }
	
	/**
	 * Get a byte buffer for user read.
	 * 
	 * @return byte buffer
	 */
	protected ByteBuffer tailBuffer() {
		final Buffer buf = localPool.peekLast();
		if(buf == null) {
			return allocate();
		}
		return buf.byteBuffer();
	}
	
	/**
	 * Get a byte buffer for read channel.
	 * 
	 * @return byte buffer
	 */
	protected ByteBuffer headBuffer() {
		final Buffer buf = localPool.peek();
		if(buf == null) {
			return allocate();
		}
		final ByteBuffer b = buf.byteBuffer();
		if(b.hasRemaining()) {
			if(b.limit() < b.capacity()) {
				return b;
			}
			return allocate();
		}
		return b;
	}
	
	/**
	 * Allocate a byte buffer for channel read.
	 * 
	 * @return a byte buffer
	 */
	protected ByteBuffer allocate() {
		final Buffer newBuf = bufferPool.allocate();
		boolean failed = true;
		try {
			localPool.offerFirst(newBuf);
			final ByteBuffer b = newBuf.byteBuffer();
			// keep user read state
			b.flip();
			failed = false;
			return b;
		}finally {
			if(failed) {
				newBuf.release();
			}
		}
	}
	
	@Override
	public void close() throws IOException {
		releaseBuffers();
		chan.shutdownInput();
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
	
	@Override
	public boolean markSupported() {
        return true;
    }
	
	@Override
	public void mark(int readlimit) {
		this.readLimit = readlimit;
		this.markPos   = tailBuffer().position();
	}
	
	@Override
	public void reset() throws IOException {
		if(markPos < 0) {
			throw new IOException("Resetting to invalid mark");
		}
		tailBuffer().position(markPos);
    }

}
