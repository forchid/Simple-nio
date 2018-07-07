package io.simple.nio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simple.util.ArrayQueue;

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
	
    // channel and buffers
	protected final Session session;
	protected ArrayQueue<Buffer> localPool;
	private int available, maxBuffers;
	private boolean eof;
	
	// mark support
	private int markPos = -1, readLimit;
	
	public BufferInputStream(final Session session) {
		this.session   = session;
		final Configuration config = session.config();
		setMaxBuffers(config.getMaxReadBuffers());
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
	
	/**
	 * @throws io.simple.nio.PendingIOException 
	 *  if no byte readable
	 */
	@Override
	public int read() throws IOException {
		final SocketChannel chan= session.channel();
		final ByteBuffer buffer = headBuffer();
		if(!buffer.hasRemaining()) {
			final ByteBuffer b;
			localPool.poll().release();
			// next buffer
			final int n = available();
			b = headBuffer();
			markPos = -1;
			if(n == 0) {
				// no byte readable or EOF
				b.clear();
				final int i = chan.read(b);
				b.flip();
				if(i == -1) {
					this.eof = true;
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

        final int c = read();
        if (c == -1) {
            return c;
        }
        b[off] = (byte)c;
        
        final int size = Math.min(len, available + 1/*prev byte*/);
        int i = 1;
        ByteBuffer buf = headBuffer();
        for (int n = 0; i < size ; i += n) {
        	final int rem = buf.remaining();
        	if(rem == 0) {
        		buf = headBuffer();
        		markPos = -1;
        	}
        	n = Math.min(rem, size-i);
        	buf.get(b, off + i, n);
        	available -= n;
        }
        return i;
    }

	@Override
	public long skip(final long n) throws IOException {
        final int size = (int)Math.min(n, available());
        if (size <= 0) {
            return 0L;
        }

        ByteBuffer buf = headBuffer();
        for (int i = 0, j = 0; i < size; i += j) {
        	final int rem = buf.remaining();
        	if(rem == 0) {
        		buf = headBuffer();
        		markPos = -1;
        	}
        	j = Math.min(rem, size-i);
        	buf.position(buf.position() + j);
        	available -= j;
        }

        return n - size;
    }
	
	@Override
	public int available() throws IOException {
		// limit read rate since 2018-06-24 little-pan
		int buffers = calcBuffers();
		if(buffers >= maxBuffers && available > 0) {
			final ByteBuffer b = localPool.peekLast().byteBuffer();
			if(b.limit() == b.capacity()) {
				log.debug("Don't read from channel - reach maxBuffers = {}, buffers = {}, available = {}",
						maxBuffers, buffers, available);
				return available;
			}
		}
		
		final SocketChannel chan = session.channel();
		final int oldAvailable   = available;
		// prepare for channel read
		final ByteBuffer buffer  = tailBuffer();
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
					this.eof = (i == -1);
					if(oldAvailable != available) {
						try {
							session.fireReadComplete();
						}catch(final Exception e) {
							if(e instanceof RuntimeException) {
								throw (RuntimeException)e;
							}
							throw new RuntimeException(e);
						}
					}
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
		final BufferPool bufferPool = session.bufferPool();
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
	protected ByteBuffer headBuffer() {
		final Buffer buf = localPool.peek();
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
	protected ByteBuffer tailBuffer() {
		final Buffer buf = localPool.peekLast();
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
			localPool.offer(newBuf);
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
		session.channel().shutdownInput();
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
		this.markPos   = headBuffer().position();
	}
	
	@Override
	public void reset() throws IOException {
		if(markPos < 0) {
			throw new IOException("Resetting to invalid mark");
		}
		final ByteBuffer buf = headBuffer();
		available += (buf.position() - markPos);
		buf.position(markPos);
    }
	
	/**
	 * @return true if has reached the end-of-stream
	 */
	public final boolean eof() {
		return eof;
	}

}
