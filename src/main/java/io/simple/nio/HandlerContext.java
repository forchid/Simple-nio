package io.simple.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The event handler context.
 * 
 * @author little-pan
 * @since 2018-06-20
 *
 */
public class HandlerContext implements Closeable {
	
	protected HandlerContext next;
	protected HandlerContext prev;
	
	final EventHandler handler;
	final Session session;
	
	public HandlerContext(Session session, EventHandler handler){
		this.session = session;
		this.handler = handler;
	}
	
	public Session session() {
		return session;
	}
	
	public boolean isShutdown() {
		return session.isShutdown();
	}
	
	public EventLoop eventLoop(){
		return session.eventLoop();
	}
	
	/**
	 * <p>
	 * Execute the task in event loop. Thread safe.
	 * </p>
	 * 
	 * @param task
	 * @return this context
	 * 
	 */
	public HandlerContext execute(Runnable task) {
		session.execute(task);
		return this;
	}
	
	/**
	 * <p>
	 * Schedule the time task in event loop. Thread safe.
	 * </p>
	 * 
	 * @param task
	 * @return this context
	 */
	public HandlerContext schedule(TimeTask task) {
		session.schedule(task);
		return this;
	}
	
	public EventHandler handler(){
		return handler;
	}
	
	public Buffer alloc()throws BufferAllocateException{
		return session.alloc();
	}
	
	public HandlerContext enableRead() {
		session.enableRead();
		return this;
	}
	
	public HandlerContext enableWrite() {
		session.enableWrite();
		return this;
	}
	
	public HandlerContext disableRead() {
		session.disableRead();
		return this;
	}
	
	public HandlerContext disableWrite() {
		session.disableWrite();
		return this;
	}
	
	/**
	 * Write byte array into output stream.
	 * @param b
	 * @return this context
	 * @throws IOException 
	 */
	public HandlerContext write(byte b[]) throws IOException {
		session.write(b);
		return this;
	}
	
	/**
	 * Write byte array into output stream.
	 * 
	 * @param b
	 * @param off
	 * @param len
	 * 
	 * @return this context
	 * @throws IOException 
	 */
	public HandlerContext write(byte b[], int off, int len) throws IOException {
		session.write(b, off, len);
		return this;
	}
	
	/**
	 * Write byte buffer into output buffer stream.
	 * 
	 * @param buf
	 * @return this context
	 * @throws IOException 
	 */
	public HandlerContext write(ByteBuffer buf) throws IOException {
		session.write(buf);
		return this;
	}
	
	/**
	 * Write byte buffer into output buffer stream.
	 * 
	 * @param buf
	 * @param off
	 * @param len
	 * 
	 * @return this context
	 * @throws IOException 
	 */
	public HandlerContext write(ByteBuffer buf, int off, int len) throws IOException {
		session.write(buf, off, len);
		return this;
	}

	/**
	 * <p>
	 * Flush output buffer stream into the socket channel.
	 * Please see {@link Session#flush()} method.
	 * </p>
	 * @throws Exception 
	 */
	public HandlerContext flush() throws Exception {
		session.flush();
		return this;
	}
	
	public void close() {
		session.close();
	}

	public void fireConnected() throws Exception {
		if(next != null){
			next.handler.onConnected(next);
		}
	}
	
	public void fireRead(Object in) throws Exception {
		if(next != null){
			next.handler.onRead(next, in);
		}
	}
	
	public void fireReadComplete() throws Exception {
		if(next != null){
			next.handler.onReadComplete(next);
		}
	}
	
	public void fireWrite(Object out) throws Exception {
		if(prev != null){
			prev.handler.onWrite(prev, out);
		}
	}
	
	public void fireFlushed() throws Exception {
		if(next != null){
			next.handler.onFlushed(next);
		}
	}
	
	public void fireUserEvent(Object ev) throws Exception {
		if(next != null){
			next.handler.onUserEvent(next, ev);
		}
	}
	
	public void fireCause(Throwable cause) {
		if(next != null){
			next.handler.onCause(next, cause);
		}
	}
	
}
