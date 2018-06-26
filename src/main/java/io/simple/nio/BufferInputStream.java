package io.simple.nio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	final static Logger log = LoggerFactory.getLogger(BufferInputStream.class);
	
	// MAX_SKIP_BUFFER_SIZE is used to determine the maximum buffer size to
    // use when skipping.
    private static final int MAX_SKIP_BUFFER_SIZE = 2048;
    
    // channel and buffers
	protected final Session session;
	protected LinkedList<Buffer> localPool;
	private int available, maxBuffers;
	
	// mark support
	private int markPos = -1, readLimit;
	
	public BufferInputStream(final Session session) {
		this.session   = session;
		this.localPool = new LinkedList<Buffer>();
		
		final Configuration config = session.getConfig();
		setMaxBuffers(config.getReadMaxBuffers());
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
	
	/**
	 * @throws io.simple.nio.PendingIOException 
	 *  if no byte readable
	 */
	@Override
	public int read() throws IOException {
		final SocketChannel chan= session.getChannel();
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
        
        final int size = Math.min(len, available + 1/*prev byte*/);
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
		// limit read rate since 2018-06-24 little-pan
		int buffers = calcBuffers();
		if(buffers >= maxBuffers && available > 0) {
			final ByteBuffer b = localPool.peek().byteBuffer();
			if(b.limit() == b.capacity()) {
				log.debug("Don't read from channel - reach maxBuffers = {}, buffers = {}, available = {}",
						maxBuffers, buffers, available);
				return available;
			}
		}
		
		final SocketChannel chan= session.getChannel();
		// prepare for channel read
		final ByteBuffer buffer = headBuffer();
		final int cap = buffer.capacity();
		final int pos = buffer.position();
		final int lim = buffer.limit();
		if(pos < lim) {
			buffer.position(lim).limit(cap);
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
					final int newlim = buffer.position();
					buffer.position(pos).limit(newlim);
				}else {
					b.flip();
				}
				
				if(i <= 0 || buffers >= maxBuffers) {
					return available;
				}
				
				b = allocate();
				b.clear();
				++buffers;
			}
			available += i;
		}
    }
	
	protected int calcBuffers() {
		final BufferPool bufferPool = session.getBufferPool();
		final int shift = bufferPool.bufferSizeShift();
		int buffers = available >> shift;
		if((available & (bufferPool.bufferSize()-1)) != 0){
			++buffers;
		}
		return buffers;
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
		final Buffer newBuf = session.alloc();
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
		session.getChannel().shutdownInput();
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
