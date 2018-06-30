package io.simple.util;

/**
 * <p>
 * Array based queue for low memory footprint and high performance intent.
 * </p>
 * 
 * @author little-pan
 * @since 2018-06-30
 *
 */
@SuppressWarnings("unchecked")
public class ArrayQueue<E> {
	
	final Object elems[];
	private int front, rear;
	private int count;
	
	/**
	 * Create a new circular queue using specified capacity.
	 */
	public ArrayQueue(final int capacity){
		this.elems = new Object[capacity];
	}
	
	public boolean offer(E e) {
		if(e == null) {
			throw new NullPointerException();
		}
		
		if(count == elems.length) {
			// Full
			return false;
		}
		elems[rear] = e;
		if(++rear >= elems.length) {
			rear -= elems.length;
		}
		++count;
		return true;
	}
	
	public E peekLast() {
		int r = rear - 1;
		if(r < 0) {
			r += elems.length;
		}
		return (E)elems[r];
	}
	
	public E poll() {
		if(count == 0) {
			// Empty
			return null;
		}
		final E e = (E)elems[front];
		elems[front] = null;
		if(++front >= elems.length) {
			front -= elems.length;
		}
		--count;
		return e;
	}
	
	public E peek() {
		return (E)elems[front];
	}
	
	public boolean offerFirst(E e) {
		if(e == null) {
			throw new NullPointerException();
		}
		
		if(count == elems.length) {
			// Full
			return false;
		}
		if(--front < 0) {
			front += elems.length;
		}
		elems[front] = e;
		++count;
		return true;
	}
	
	public E pollLast() {
		if(count == 0) {
			return null;
		}
		if(--rear < 0) {
			rear += elems.length;
		}
		final E e = (E)elems[rear];
		elems[rear] = null;
		--count;
		return e;
	}
	
	public int size() {
		return count;
	}
	
	public boolean isEmpty() {
		return (count == 0);
	}
	
	public int capacity() {
		return elems.length;
	}
	
	public void clear() {
		for(int i = 0, cap = elems.length; i < cap; ++i) {
			elems[i] = null;
		}
		front = rear = count = 0;
	}
	
	/**
	 * <p>
	 * Drain the source queue into the new destination queue.
	 * </p>
	 * 
	 * @param srcQueue
	 * @param dstCapacity
	 * 
	 * @return the specified dstCapacity new queue
	 */
	public static <E> ArrayQueue<E> drainQueue(ArrayQueue<E> srcQueue, int dstCapacity) {
		final ArrayQueue<E> dstQueue = new ArrayQueue<E>(dstCapacity);
		for(int i = 0; i < dstCapacity; ++i) {
			final E e = srcQueue.poll();
			if(e == null) {
				break;
			}
			dstQueue.offer(e);
		}
		return dstQueue;
	}

}
