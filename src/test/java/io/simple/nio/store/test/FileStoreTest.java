package io.simple.nio.store.test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.*;
import io.simple.nio.store.FileRegion;
import io.simple.nio.store.FileStore;

/**
 * Test file store.
 * 
 * @author little-pan
 * @since 2018-06-25
 *
 */
public class FileStoreTest {
	
	final static int storeSize = 1 << 4, regionSize = 1 << 3;
	
	File file;
	FileStore store;
	
	@Before
	public void init() throws IOException {
		file  = new File("data/store.data");
		store = FileStore.open("TestStore", file, storeSize, regionSize);
	}
	
	@Test
	public void testEmpty(){
		assertEquals(store.size(), 0);
	}
	
	@Test
	public void testReadEmptyRegion() throws IOException {
		final FileRegion region = store.allocate();
		try{
			assertEquals(store.size(), 0);
			
			ByteBuffer buf = ByteBuffer.allocate(regionSize);
			int n = region.read(buf);
			assertEquals(n, -1);
		}finally{
			region.release();
		}
	}
	
	@Test
	public void testWriteEmptyRegion() throws IOException {
		final FileRegion region = store.allocate();
		try{
			assertEquals(store.size(), 0);
			
			ByteBuffer buf = ByteBuffer.allocate(0);
			int n = region.write(buf);
			assertEquals(n, 0);
		}finally{
			region.release();
		}
	}
	
	@Test
	public void testWriteReadOneRegion() throws IOException {
		final FileRegion region = store.allocate();
		try{
			assertEquals(store.size(), 0);
			
			final byte a[] = "01234567".getBytes();
			ByteBuffer buf = ByteBuffer.allocate(a.length);
			buf.put(a);
			
			buf.flip();
			int n = region.write(buf);
			assertEquals(n, regionSize);
			
			buf.clear();
			n = region.read(buf);
			assertEquals(n, regionSize);
			
			assertTrue(Arrays.equals(buf.array(), a));
		}finally{
			region.release();
		}
	}
	
	@Test
	public void testWriteOverflow() throws IOException {
		final FileRegion region = store.allocate();
		try{
			assertEquals(store.size(), 0);
			
			final String msg = "012345679";
			final byte a[] = msg.getBytes();
			ByteBuffer buf = ByteBuffer.allocate(a.length);
			buf.put(a);
			
			buf.flip();
			int n = region.write(buf);
			assertEquals(n, regionSize);
			
			n = region.write(buf);
			assertEquals(n, 0);
			
			buf.clear();
			n = region.read(buf);
			assertEquals(n, regionSize);
			buf.flip();
			final byte b[] = new byte[buf.remaining()];
			buf.get(b);
			final byte c[] = msg.substring(0, regionSize).getBytes();
			assertTrue(Arrays.equals(b, c));
			
			buf.clear();
			n = region.read(buf);
			assertEquals(n, -1);
			
		}finally{
			region.release();
		}
	}
	
	@Test
	public void testWriteMore() throws IOException {
		final List<FileRegion> regions= new ArrayList<FileRegion>();
		final byte a[] = "012345679".getBytes();
		final int size = a.length;
		ByteBuffer buf = ByteBuffer.allocate(size);
		buf.put(a);
		
		// write more
		buf.flip();
		FileRegion region = store.allocate();
		assertEquals(region.readIndex(), 0);
		assertEquals(region.writeIndex(), 0);
		int writeCount = 0;
		for(int i = 0, n = 0; writeCount < size;){
			i = region.write(buf);
			if(i == 0 && n == regionSize){
				regions.add(region);
				region = store.allocate();
				assertEquals(region.readIndex(), 0);
				assertEquals(region.writeIndex(), 0);
				n = 0;
				continue;
			}
			n += i;
			writeCount += i;
		}
		regions.add(region);
		
		assertEquals(size, writeCount);
		assertEquals(regions.size(), (size/regionSize)+((size%regionSize)==0?0:1));
		
		// read more for test
		buf.clear();
		int readCount = 0;
		for(final FileRegion r : regions){
			for(int n = 0; n < regionSize;){
				final int i = r.read(buf);
				if(i == -1){
					break;
				}
				n += i;
				readCount += i;
			}
		}
		assertEquals(readCount, writeCount);
		assertTrue(Arrays.equals(buf.array(), a));
		
		// release
		for(final FileRegion r : regions){
			r.release();
		}
		assertEquals(store.size(), 0);
	}
	
	@Test(expected = IllegalStateException.class)
	public void testRelease() throws IOException {
		final FileRegion region = store.allocate();
		region.release();
		// throw
		region.read(ByteBuffer.allocate(regionSize));
	}
	
	@Test
	public void testAllocateRelease() throws IOException {
		for(int i = 0, n = 1000 * (storeSize/regionSize); i < n; ++i) {
			// allocate-release
			final FileRegion region = store.allocate();
			region.release();
		}
	}
	
	@Test(expected = java.io.IOException.class)
	public void testAllocateFull() throws IOException {
		for(int i = 0, n = 1000 * (storeSize/regionSize); i < n; ++i) {
			// allocate-not-release
			store.allocate();
		}
	}
	
	@After
	public void destroy(){
		store.close();
	}
	
}
