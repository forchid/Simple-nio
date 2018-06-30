package io.simple.util.test;

import io.simple.util.ArrayQueue;

public class ArrayQueueTest {
	
	public static void main(String args[]) {
		ArrayQueue<Integer> queue = new ArrayQueue<Integer>(10);
		println("queue.new(): capacity = %d, size = %d", queue.capacity(), queue.size());
		
		queue.offer(1);
		println("queue.offer(): capacity = %d, size = %d", queue.capacity(), queue.size());
		
		Integer header = queue.poll();
		println("queue.poll(): capacity = %d, size = %d, header = %s", 
			queue.capacity(), queue.size(), header);
		
		for(int i = 0, len = queue.capacity(); i < len; ++i) {
			final boolean ok = queue.offer(i);
			println("queue.offer(): ok = %s, capacity = %d, size = %d, elem = %d", 
				ok, queue.capacity(), queue.size(), i);
		}
		
		header = queue.peek();
		println("queue.peek(): capacity = %d, size = %d, header = %s", 
				queue.capacity(), queue.size(), header);
		
		Integer tailer = queue.peekLast();
		println("queue.peekLast(): capacity = %d, size = %d, tailer = %s", 
				queue.capacity(), queue.size(), tailer);
		
		header = queue.poll();
		println("queue.poll(): capacity = %d, size = %d, header = %s", 
				queue.capacity(), queue.size(), header);
		
		queue.offer(queue.capacity());
		tailer = queue.peekLast();
		println("queue.offer(): capacity = %d, size = %d, tailer = %d", 
				queue.capacity(), queue.size(), tailer);
	}
	
	static void println(String f, Object ... args) {
		if(args.length == 0) {
			System.out.println(f);
			return;
		}
		System.out.println(String.format(f, args));
	}

}
