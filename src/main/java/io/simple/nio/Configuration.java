package io.simple.nio;

import io.simple.nio.store.FileStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Configuration {
	
	private boolean daemon = false;
	
	private String name  = "Simple-nio";
	private String host  = "0.0.0.0";
	private int port     = 9696;
	private int backlog  = 1024;
	private int maxConns = 10240, maxServerConns, maxClientConns;
	private int maxReadBuffers = 8, maxWriteBuffers = 64, writeSpinCount = 16;
	
	private boolean autoRead     = true;
	private boolean bufferDirect = true;
	private int bufferSize       = BufferPool.DEFAULT_BUFFER_SIZE;
	private long poolSize        = Runtime.getRuntime().maxMemory()>>1;
	private BufferPool bufferPool;
	private FileStore  bufferStore;
	
	private final List<Class<? extends EventHandler>> serverHandlers;
	private final List<Class<? extends EventHandler>> clientHandlers;
	
	private EventLoopListener eventLoopListener;
	
	public Configuration() {
		serverHandlers = new ArrayList<Class<? extends EventHandler>>();
		clientHandlers = new ArrayList<Class<? extends EventHandler>>();
	}

	public String getName() {
		return name;
	}
	
	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}
	
	public int getBacklog() {
		return backlog;
	}
	
	public boolean isDaemon() {
		return daemon;
	}
	
	public int getMaxConns() {
		return maxConns;
	}
	
	public int getMaxClientConns() {
		return maxClientConns;
	}

	public int getMaxServerConns() {
		return maxServerConns;
	}
	
	public boolean isAutoRead() {
		return autoRead;
	}
	
	public boolean isBufferDirect() {
		return bufferDirect;
	}
	
	public int getBufferSize() {
		return bufferSize;
	}
	
	public long getPoolSize() {
		return poolSize;
	}
	
	public BufferPool getBufferPool() {
		return bufferPool;
	}
	
	public FileStore getBufferStore() {
		return bufferStore;
	}
	
	public List<Class<? extends EventHandler>> getServerHandlers() {
		return Collections.unmodifiableList(serverHandlers);
	}
	
	public List<Class<? extends EventHandler>> getClientHandlers() {
		return Collections.unmodifiableList(clientHandlers);
	}
	
	public EventLoopListener getEventLoopListener(){
		return eventLoopListener;
	}
	
	/**
	 * <p>
	 * An input rate limit way for saving buffer memory consumption. 
	 * When beyond this, don't read bytes from channel until a buffer released.
	 * </p>
	 * 
	 * <p>
	 * <b>Note</b>: {@link #getMaxReadBuffers()} x {@link #getBufferSize()} should
	 * be bigger than max packet size in user protocol.
	 * </p>
	 * 
	 * @return max read buffer number
	 */
	public int getMaxReadBuffers() {
		return maxReadBuffers;
	}

	/**
	 * An output rate limit way for saving buffer memory consumption. 
	 * When beyond this, cache bytes into disc.
	 * 
	 * @return max write buffer number
	 */
	public int getMaxWriteBuffers() {
		return maxWriteBuffers;
	}
	
	/**
	 * The maximum loop count for a write operation until channel 
	 *returns a non-zero value.
	 * 
	 * @return write spin count
	 */
	public int getWriteSpinCount(){
		return writeSpinCount;
	}
	
	public final static Builder newBuilder() {
		return new Builder();
	}
	
	public static class Builder {
		
		final Configuration config = new Configuration();
		
		public Builder() {
			
		}
		
		public Builder setName(String name) {
			config.name = name;
			return this;
		}
		
		public Builder setDaemon(boolean daemon) {
			config.daemon = daemon;
			return this;
		}
		
		public Builder setHost(String host) {
			config.host = host;
			return this;
		}
		
		public Builder setPort(int port) {
			config.port = port;
			return this;
		}
		
		public Builder setBacklog(int backlog) {
			config.backlog = backlog;
			return this;
		}
		
		/**
		 * @param maxConns
		 * @return the default max connections for server or client
		 */
		public Builder setMaxConns(int maxConns) {
			config.maxConns = maxConns;
			return this;
		}
		
		public Builder setMaxServerConns(int maxServerConns) {
			config.maxServerConns = maxServerConns;
			return this;
		}
		
		public Builder setMaxClientConns(int maxClientConns) {
			config.maxClientConns = maxClientConns;
			return this;
		}
		
		public Builder setAutoRead(boolean autoRead) {
			config.autoRead = autoRead;
			return this;
		}
		
		public Builder setBufferDirect(boolean bufferDirect) {
			config.bufferDirect = bufferDirect;
			return this;
		}
		
		public Builder setBufferSize(int bufferSize) {
			config.bufferSize = bufferSize;
			return this;
		}
		
		public Builder setPoolSize(long poolSize) {
			config.poolSize = poolSize;
			return this;
		}
		
		public Builder setMaxReadBuffers(int maxReadBuffers) {
			config.maxReadBuffers  = maxReadBuffers;
			return this;
		}
		
		public Builder setMaxWriteBuffers(int maxWriteBuffers) {
			config.maxWriteBuffers = maxWriteBuffers;
			return this;
		}
		
		public Builder setWriteSpinCount(int writeSpinCount){
			config.writeSpinCount = writeSpinCount;
			return this;
		}
		
		public Builder appendServerHandler(EventHandler handler) {
			config.serverHandlers.add(handler.getClass());
			return this;
		}
		
		public Builder appendServerHandler(Class<? extends EventHandler> clazz) {
			config.serverHandlers.add(clazz);
			return this;
		}
		
		public Builder appendClientHandler(EventHandler handler) {
			config.clientHandlers.add(handler.getClass());
			return this;
		}
		
		public Builder appendClientHandler(Class<? extends EventHandler> clazz) {
			config.clientHandlers.add(clazz);
			return this;
		}
		
		public Builder setEventLoopListener(EventLoopListener eventLoopListener){
			config.eventLoopListener = eventLoopListener;
			return this;
		}
		
		public Configuration build() {
			final int maxConns = config.maxConns;
			if(maxConns < 1) {
				throw new IllegalArgumentException("maxConns must bigger than 0: "+config.maxConns);
			}
			if(config.maxServerConns <= 0){
				config.maxServerConns = maxConns;
			}
			if(config.maxClientConns <= 0){
				config.maxClientConns = maxConns;
			}
			
			if(config.maxReadBuffers  < 1) {
				throw new IllegalArgumentException("maxReadBuffers must bigger  than 0: "+config.maxReadBuffers);
			}
			if(config.maxWriteBuffers < 1) {
				throw new IllegalArgumentException("maxWriteBuffers must bigger than 0: "+config.maxWriteBuffers);
			}
			if(config.writeSpinCount  < 1) {
				throw new IllegalArgumentException("writeSpinCount must bigger than 0: "+config.writeSpinCount);
			}
			
			final long poolSize = config.poolSize;
			final int bufferSize= config.bufferSize;
			if(config.isBufferDirect()) {
				config.bufferPool = new LinkedBufferPool(poolSize, bufferSize);
			}else {
				config.bufferPool = new SimpleBufferPool(poolSize, bufferSize);
			}
			config.bufferStore    = FileStore.open("BufferStore", bufferSize);
			
			if(config.eventLoopListener == null){
				config.eventLoopListener = EventLoopListener.NOOP;
			}
			
			return config;
		}
		
	}

}
