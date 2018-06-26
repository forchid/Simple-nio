package io.simple.nio.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * A file region that allocated from a {@link FileStore}, and released into the store.
 * 
 * @author little-pan
 * @since 2018-06-25
 *
 */
public class FileRegion {
	
	public final FileStore store;
	public final long id;
	
	private int readIndex, writeIndex;
	private boolean released;
	
	public FileRegion(final FileStore store, final long id){
		this.store = store;
		this.id    = id;
		this.released = true;
	}
	
	protected void onAllocate(){
		if(!released){
			throw new IllegalStateException(this + " not released");
		}
		released = false;
	}

	public int readIndex(){
		return readIndex;
	}
	
	public int readRemaining(){
		return (writeIndex - readIndex);
	}
	
	public int writeIndex(){
		return writeIndex;
	}
	
	public int writeRemaining(){
		return (store.regionSize - writeIndex);
	}
	
	public int capacity(){
		return store.regionSize;
	}
	
	public int transferTo(int count, WritableByteChannel dst)throws IOException {
		return store.transferTo(this, count, dst);
	}
	
	public int transferFrom(ReadableByteChannel src, int count) throws IOException {
		return store.transferFrom(this, src, count);
	}
	
	public int read(ByteBuffer dst) throws IOException {
		return store.read(this, dst);
	}
	
	public int write(ByteBuffer src) throws IOException {
		return store.write(this, src);
	}
	
	public void release(){
		store.release(this);
	}
	
	protected void onRelease(){
		checkNotReleased();
		released = true;
	}
	
	public FileRegion clear(){
		readIndex = writeIndex = 0;
		return this;
	}
	
	final void checkNotReleased(){
		if(released){
			throw new IllegalStateException(this + " has released");
		}
	}

	final FileRegion readIndex(int i) {
		checkNotReleased();
		if(i < 0 || i > writeIndex){
			throw new IndexOutOfBoundsException("readIndex: " +i);
		}
		readIndex = i;
		return this;
	}
	
	final FileRegion writeIndex(int i) {
		checkNotReleased();
		if(i < readIndex || i > store.regionSize){
			throw new IndexOutOfBoundsException("writeIndex: " +i);
		}
		writeIndex = i;
		return this;
	}
	
	@Override
	public String toString(){
		return (String.format("%s:FileRegion-%d(ridx = %d, widx = %d)", 
				store, id, readIndex, writeIndex));
	}
	
}
