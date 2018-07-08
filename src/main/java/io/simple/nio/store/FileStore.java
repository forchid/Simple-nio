package io.simple.nio.store;

import io.simple.util.IoUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * The file based storage that manages a pool of {@link FileRegion}.
 * 
 * @author little-pan
 * @since 2018-06-25
 *
 */
public class FileStore implements Closeable {
	
	public final String name;
	public final long storeSize;
	public final int  regionSize;
	
	final File file;
	final FileChannel chan;
	
	private FileRegion regionPool[];
	private long size;
	private int maxId;
	
	public FileStore(long storeSize, int regionSize) throws IOException {
		this("FileStore", null, null, storeSize, regionSize);
	}
	
	public FileStore(String name, long storeSize, int regionSize) throws IOException {
		this(name, null, null, storeSize, regionSize);
	}
	
	public FileStore(String name, File file, long storeSize, int regionSize) throws IOException {
		this(name, file, null, storeSize, regionSize);
	}

	public FileStore(String name, File file, String mode, long storeSize, int regionSize) 
			throws IOException {
		boolean failed = true;
		try {
			final long cap = storeSize / regionSize;
			if(cap > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("storeSize can't bigger than " + 
						((long)Integer.MAX_VALUE * regionSize));
			}
			this.file = (file==null?createTempFile():file);
			this.chan = openChannel(this.file, mode);
			this.storeSize  = storeSize;
			this.regionPool = new FileRegion[(int)cap];
			this.regionSize = regionSize;
			this.name       = name;
			failed = false;
		}finally {
			if(failed) {
				IoUtil.close(this.chan);
				if(file == null && this.file != null) {
					this.file.delete();
				}
			}
		}
	}
	
	public FileRegion allocate() throws IOException {
		// Sequence allocate and write for performance.
		// @since 2018-07-07 little-pan
		final int id;
		if(maxId == regionPool.length) {
			// circular
			id = 0;
			chan.position(0L);
		}else {
			id = maxId;
		}
		
		FileRegion region = regionPool[id];
		if(region != null && !region.isReleased()) {
			throw new IOException("Too many file regions");
		}
		if(region == null){
			region = new FileRegion(this, id);
			regionPool[id] = region;
		}
		region.onAllocate();
		
		maxId = id + 1;
		return region;
	}
	
	public void release(FileRegion region) {
		if(region.store == this){
			final int id  = region.id;
			region.onRelease();
			region.clear();
			regionPool[id]= null;
			return;
		}
		throw new IllegalArgumentException(region + ": not in " + this);
	}
	
	public boolean isOpen(){
		return chan.isOpen();
	}
	
	/**
	 * @return this store byte number
	 */
	public long size(){
		return size;
	}
	
	public int transferFrom(FileRegion region, ReadableByteChannel src, int count) 
			throws IOException {
		region.checkNotReleased();
		
		final int widx = region.writeIndex();
		final int size = Math.min(regionSize - widx, count);
		if(size == 0){
			return 0;
		}
		final long position = region.id * regionSize + widx;
		final int n = (int)chan.transferFrom(src, position, size);
		this.size  += n;
		region.writeIndex(widx + n);
		return n;
	}
	
	public int transferTo(FileRegion region, int count, WritableByteChannel dst) 
			throws IOException {
		region.checkNotReleased();
		
		final int size = Math.min(region.readRemaining(), count);
		if(size == 0){
			return 0;
		}
		
		// Random read
		final int ridx = region.readIndex();
		final long position = region.id * regionSize + ridx;
		final int n = (int)chan.transferTo(position, size, dst);
		this.size  -= n;
		region.readIndex(ridx + n);
		
		return n;
	}
	
	public int read(FileRegion region, ByteBuffer dst) throws IOException {
		region.checkNotReleased();
		
		final int rem = region.readRemaining();
		if(rem == 0){
			return -1;
		}
		
		final int size = Math.min(rem, dst.remaining());
		if(size == 0){
			return 0;
		}
		
		final int lim = dst.limit();
		try{
			dst.limit(dst.position() + size);
			final int ridx = region.readIndex();
			final long position = region.id * regionSize + ridx;
			final int n = chan.read(dst, position);
			if(n == -1){
				throw new IOException(name+" truncated");
			}
			this.size  -= n;
			region.readIndex(ridx + n);
			return n;
		}finally{
			dst.limit(lim);
		}
	}
	
	public int write(FileRegion region, ByteBuffer src) throws IOException {
		region.checkNotReleased();
		
		final int size = Math.min(region.writeRemaining(), src.remaining());
		if(size == 0){
			return 0;
		}
		
		final int lim = src.limit();
		try{
			src.limit(src.position() + size);
			final int widx = region.writeIndex();
			final long position = region.id * regionSize + widx;
			
			// Keep sequence write for performance
			if(chan.position() != position) {
				chan.position(position);
			}
			
			int n = 0;
			// write complete for sequence write
			for(int i = 0; n < size; n += i) {
				i = chan.write(src);
			}
			this.size  += n;
			region.writeIndex(widx + n);
			
			return n;
		}finally{
			src.limit(lim);
		}
	}
	
	public void force(boolean metaData) throws IOException {
		chan.force(metaData);
	}
	
	@Override
	public void close(){
		regionPool = null;
		maxId      = 0;
		truncate(size = 0L);
		IoUtil.close(chan);
		file.delete();
	}

	protected void truncate(final long size){
		try {
			chan.truncate(size);
		} catch (IOException e) {
			// ignore: NOOP
		}
	}
	
	@Override
	public String toString(){
		return name;
	}
	
	public static FileStore open(long storeSize, int regionSize) throws IOException {
		return open(null, null, null, storeSize, regionSize);
	}
	
	public static FileStore open(String name, long storeSize, int regionSize) throws IOException {
		return open(name, null, null, storeSize,  regionSize);
	}
	
	public static FileStore open(String name, File file, long storeSize, int regionSize) 
			throws IOException {
		return open(name, file, null, storeSize, regionSize);
	}
	
	public static FileStore open(String name, File file, String mode, long storeSize, int regionSize) 
			throws IOException {
		return new FileStore(name, file, mode, storeSize, regionSize);
	}
	
	public static FileChannel openChannel() throws IOException{
		return openChannel(null, null);
	}
	
	public static FileChannel openChannel(File file) throws IOException {
		return openChannel(file, null);
	}
	
	public static FileChannel openChannel(File file, String mode)throws IOException {
		boolean tmpf = false;
		if(file == null){
			file = createTempFile();
			tmpf = true;
		}
		if(mode == null){
			mode = "rw";
		}
		RandomAccessFile raf = null;
		boolean failed = true;
		try {
			raf = new RandomAccessFile(file, mode);
			failed = false;
			return raf.getChannel();
		}finally {
			if(failed) {
				IoUtil.close(raf);
				if(failed && tmpf) {
					file.delete();
				}
			}
		}
	}
	
	static File createTempFile() throws IOException {
		final File f;
		f = File.createTempFile("Simple-nio.", ".tmp");
		f.deleteOnExit();
		return f;
	}

}
