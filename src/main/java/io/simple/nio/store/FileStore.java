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
import java.util.LinkedList;

/**
 * The file based storage that manages a pool of {@link FileRegion}.
 * 
 * @author little-pan
 * @since 2018-06-25
 *
 */
public class FileStore implements Closeable {
	
	public final String name;
	public final int regionSize;
	
	final FileChannel chan;
	
	private final LinkedList<FileRegion> regionPool;
	private long maxId, size;
	
	public FileStore(int regionSize){
		this("FileStore", null, null, regionSize);
	}
	
	public FileStore(String name, int regionSize){
		this(name, null, null, regionSize);
	}
	
	public FileStore(String name, File file, int regionSize){
		this(name, file, null, regionSize);
	}

	public FileStore(String name, File file, String mode, int regionSize){
		this.chan = openChannel(file, mode);
		this.regionPool = new LinkedList<FileRegion>();
		this.regionSize = regionSize;
		this.name       = name;
	}
	
	public FileRegion allocate(){
		FileRegion region = regionPool.poll();
		if(region == null){
			final long id = maxId;
			region = new FileRegion(this, id);
			++maxId;
		}
		region.onAllocate();
		return region;
	}
	
	public void release(FileRegion region) {
		if(region.store == this){
			region.onRelease();
			if(region.id == maxId - 1){
				try {
					chan.truncate((maxId-1) * regionSize);
					--maxId;
					return;
				} catch (final IOException e) {}
			}
			regionPool.offer(region.clear());
		}
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
		
		final int ridx = region.readIndex();
		final long position = region.id * regionSize + ridx;
		final int n = (int)chan.transferTo(position, size, dst);
		this.size  -= n;
		region.readIndex(ridx + n);
		return n;
	}
	
	public int read(FileRegion region, ByteBuffer dst) throws IOException {
		region.checkNotReleased();
		
		final int rem = region.writeRemaining();
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
			final int n = chan.write(src, position);
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
		regionPool.clear();
		this.size = 0L;
		IoUtil.close(chan);
	}
	
	@Override
	public String toString(){
		return name;
	}
	
	public static FileStore open(int regionSize){
		return open(null, null, null, regionSize);
	}
	
	public static FileStore open(String name, int regionSize){
		return open(name, null, null, regionSize);
	}
	
	public static FileStore open(String name, File file, int regionSize){
		return open(name, file, null, regionSize);
	}
	
	public static FileStore open(String name, File file, String mode, int regionSize){
		return new FileStore(name, file, mode, regionSize);
	}
	
	public static FileChannel openChannel(){
		return openChannel(null, null);
	}
	
	public static FileChannel openChannel(File file){
		return openChannel(file, null);
	}
	
	public static FileChannel openChannel(File file, String mode){
		try {
			if(file == null){
				file = File.createTempFile("Simple-nio.", ".tmp");
				file.deleteOnExit();
			}
			if(mode == null){
				mode = "rw";
			}
			return new RandomAccessFile(file, mode).getChannel();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

}
