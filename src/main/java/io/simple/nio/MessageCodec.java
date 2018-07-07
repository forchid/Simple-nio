package io.simple.nio;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * The message codec base framework.
 * 
 * @author little-pan
 * @since 2018-07-07
 *
 */
public abstract class MessageCodec extends EventHandlerAdapter {
	
	private LinkedList<Object> out = new LinkedList<Object>();
	
	protected MessageCodec() {
		
	}
	
	/**
	 * <p>
	 * Encode the message into output stream.
	 * </p>
	 * 
	 * @param ctx
	 * @param msg the message
	 * @param out the output stream
	 * 
	 * @throws Exception
	 */
	protected abstract void encode(HandlerContext ctx, 
		Object msg, BufferOutputStream out) throws Exception;
	
	/**
	 * <p>
	 * Decode input stream into the message list.
	 * </p>
	 * 
	 * @param ctx
	 * @param in the input stream
	 * @param out the message list
	 * 
	 * @throws Exception
	 */
	protected abstract void decode(HandlerContext ctx, 
		BufferInputStream in, List<Object> out) throws Exception;
	
	@Override
	public void onWrite(HandlerContext ctx, Object msg) throws Exception {
		if(msg instanceof BufferOutputStream) {
			super.onWrite(ctx, msg);
			return;
		}
		final Session session = ctx.session;
		session.ensureOpen();
		encode(ctx, msg, session.out);
	}
	
	@Override
	public void onRead(HandlerContext ctx, Object msg) throws Exception {
		if(msg instanceof BufferInputStream) {
			final BufferInputStream in = (BufferInputStream)msg;
			decode(ctx, in, out);
			if(out.size() > 0) {
				final Iterator<Object> i = out.iterator();
				for(; i.hasNext(); i.remove()) {
					final Object o = i.next();
					super.onRead(ctx, o);
				}
			}
			return;
		}
		super.onRead(ctx, msg);
	}

}
