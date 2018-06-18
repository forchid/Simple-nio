package io.simple.util;

import java.io.Closeable;
import java.io.IOException;

public final class IoUtil {
	
	private IoUtil() {}
	
	public final static void close(Closeable closeable) {
		if(closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {}
		}
	}

}
