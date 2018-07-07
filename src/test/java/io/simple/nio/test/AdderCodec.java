package io.simple.nio.test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import io.simple.nio.BufferInputStream;
import io.simple.nio.BufferOutputStream;
import io.simple.nio.HandlerContext;
import io.simple.nio.MessageCodec;

public class AdderCodec extends MessageCodec {
	
	static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
	
	final ByteBuffer buffer = ByteBuffer.allocate(8).order(BYTE_ORDER);
	
	@Override
	protected void encode(HandlerContext ctx, Object msg, BufferOutputStream out) throws Exception {
		final Long res = (Long)msg;
		
		final ByteBuffer buf = buffer;
		buf.clear();
		buf.putLong(res);
		out.write(buf.array());
	}

	@Override
	protected void decode(HandlerContext ctx, BufferInputStream in, List<Object> out) throws Exception {
		final int n = in.available();
		if(n < buffer.capacity()) {
			if(in.eof()) {
				ctx.close();
			}
			return;
		}
		
		final ByteBuffer buf = buffer;
		buf.clear();
		
		for(int i = 0, size = n/buffer.capacity(); i < size; ++i) {
			buf.clear();
			in.read(buf.array());
			final Long opand = buf.getLong();
			out.add(opand);
		}
	}
}
