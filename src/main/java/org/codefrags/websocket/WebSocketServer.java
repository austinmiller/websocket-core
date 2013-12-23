/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codefrags.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codefrags.websocket.WebSocketUser.Status;

/**
 * @author Austin Miller
 *
 */
public class WebSocketServer implements Runnable {
	private static final String CONCURRENT_EXCEPTION_MESSAGE = "Cannot call after starting server.";

	protected static final Log logger = LogFactory.getLog(WebSocketServer.class);

	static private Selector selector;
	
	private enum Command {
		RUN,
		SHUTDOWN,
		SHUTDOWN_NOW;
	}
	
	private int port = 8090;
    private ServerSocketChannel server;
    private InetSocketAddress address;
    private volatile boolean running = false;
	private WebSocketListener webSocketListener;
	private long sleepTime = 50;
	private Command command = Command.RUN;
	private String protocol = "chat";
	private long pingInterval = 0;
	private Thread thread;
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {
			thread = Thread.currentThread();
			
			startServer();
			
			runServer();
			
			if(command == Command.SHUTDOWN) {
				closeAll();
			}
			
			if(command == Command.SHUTDOWN_NOW) {
				closeAllNow();
			}
			
			running = false;
			
		} catch (Exception e) {
			//TODO handle cleaning up connections
			logger.error(e.getMessage(), e);
			running = false;
		} 

	}

	/**
	 * Gracefully close all connected clients by following the protocol of sending
	 * a close frame and waiting for a response
	 * @throws IOException 
	 */
	private void closeAll() throws IOException {
		//TODO actually wait for them to respond and wrap this in the proper NIO
		// select calls;
		for(SelectionKey sk : selector.keys()) {
			WebSocketUser user = (WebSocketUser) sk.attachment();
			user.sendCloseFrame();
		}
		
	}

	/**
	 * This aggressively closes all sockets without following the protocol of
	 * sending a close frame and waiting for a resulting close frame.
	 * @throws IOException 
	 */
	private void closeAllNow() throws IOException {
		for(SelectionKey sk : selector.keys()) {
			sk.channel().close();
		}
	}

	/**
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void runServer() throws IOException, InterruptedException {
		long time;
		long lastPingTime = System.currentTimeMillis();
		
		logger.info("Running server");
		
		while(command == Command.RUN) {
			time = System.currentTimeMillis();
			
			select();
			
			if(pingInterval != 0 && pingInterval > System.currentTimeMillis() - lastPingTime) {
				pingAll();
				lastPingTime = System.currentTimeMillis();
			}
			

			time = sleepTime - (System.currentTimeMillis() - time);
			
			// We don't want to force the thread to sleep if the connection is really busy
			if(time > 0) {
				Thread.sleep(time);
			}
		}
	}

	/**
	 * 
	 */
	private void pingAll() {
		for(SelectionKey sk : selector.keys()) {
			WebSocketUser user = (WebSocketUser) sk.attachment();
			if(user != null && user.getStatus() == Status.OPEN) {
				user.ping();
			}
		}
		
	}

	/**
	 * Send a command to the server to shutdown.  If gracefully is selected the
	 * websocket protocol will be followed where a close frame is sent to each connection and we
	 * wait to receive a close frame.  The process will wait for each client to respond for
	 * a window of time before forcefully shutting down the socket, anyway.
	 * 
	 * @param whether to shutdown gracefully by following the protocol
	 */
	public void shutdown(boolean gracefully) {
		if(gracefully) {
			command = Command.SHUTDOWN;
		} else {
			command = Command.SHUTDOWN_NOW;
		}
		
		try {
			thread.join();
		} catch (InterruptedException e) {
			logger.error(e);
		}
	}

	/**
	 * @throws IOException
	 * @throws UnknownHostException
	 * @throws ClosedChannelException
	 */
	private void startServer() throws IOException, UnknownHostException, ClosedChannelException {
		server = ServerSocketChannel.open();
		selector = Selector.open();
		address = new InetSocketAddress(port);
		server.configureBlocking(false);
		server.socket().bind(address);
		server.register(selector, SelectionKey.OP_ACCEPT);

		running = true;
	}

	private void select() throws IOException {
		selector.selectNow();
		
		
        Set<SelectionKey> readyKeys = selector.selectedKeys();
        Iterator<SelectionKey> i = readyKeys.iterator();
        
		while(i.hasNext()) {
			SelectionKey sk = i.next();
			i.remove();
			
			if (sk.isAcceptable()) {
				accept();
				continue;
			}

			WebSocketUser user = (WebSocketUser) sk.attachment();

			try {
				if (sk.isWritable()) {
					user.write();
				}
	
				if (sk.isValid() && sk.isReadable()) {
					if(user.read() == false) {
						user.close();
					}
				}
			} catch(Exception e) {
				logger.error(e.getMessage(),e);
			}
		}
	}


	private void accept() {
		try {
			SocketChannel sc = server.accept();
			sc.configureBlocking(false);
			WebSocketUser user = new WebSocketUser(sc,webSocketListener,getProtocol());
			sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE , user);
			webSocketListener.onNewUser(user);
		} catch(Exception e) {
			logger.error(e.getMessage(),e);
		}
	}

	/**
	 * If the server is still processing events, returns true;
	 * @return
	 */
	public boolean isRunning() {
		return running;
	}


	public WebSocketListener getWebSocketListener() {
		return webSocketListener;
	}


	/**
	 * Supply a callback interface for the server to send events.
	 * 
	 * @param webSocketListener
	 */
	public void setWebSocketListener(WebSocketListener webSocketListener) {
		if(running == true) {
			throw new ConcurrentModificationException(CONCURRENT_EXCEPTION_MESSAGE);
		}
		this.webSocketListener = webSocketListener;
	}


	public long getSleepTime() {
		return sleepTime;
	}


	/**
	 * The "tick rate" of the server.  The server WILL eat all cycles it can if the
	 * processing time of the tick is longer than this time, as it will not sleep
	 * at all.  It sleeps the difference between the sleepTime and tick processing
	 * time, if the difference is positive.
	 * 
	 * This value is in milliseconds.
	 * 
	 * @param sleepTime
	 */
	public void setSleepTime(long sleepTime) {
		if(running == true) {
			throw new ConcurrentModificationException(CONCURRENT_EXCEPTION_MESSAGE);
		}
		this.sleepTime = sleepTime;
	}

	public String getProtocol() {
		return protocol;
	}

	/**
	 * Sub protocol to "accept".  This is arbitrary as long as both ends
	 * send and receive a sub protocol that they can accept.  Default is
	 * "chat" for lack of a better option.
	 *   
	 * @param protocol
	 */
	public void setProtocol(String protocol) {
		if(running == true) {
			throw new ConcurrentModificationException(CONCURRENT_EXCEPTION_MESSAGE);
		}
		this.protocol = protocol;
	}

	public int getPort() {
		return port;
	}

	/**
	 * The port on which to listen for websockets.
	 * @param port
	 */
	public void setPort(int port) {
		if(running == true) {
			throw new ConcurrentModificationException(CONCURRENT_EXCEPTION_MESSAGE);
		}
		this.port = port;
	}

	public long getPingInterval() {
		return pingInterval;
	}

	/**
	 * If this is set to 0, the default, the server won't ping.  Otherwise, set the value
	 * to some reasonable frequency in milliseconds, like 30.
	 * 
	 * @param The frequency, in milliseconds, with which to send pings to connected sockets
	 */
	public void setPingInterval(long pingInterval) {
		if(running == true) {
			throw new ConcurrentModificationException(CONCURRENT_EXCEPTION_MESSAGE);
		}
		this.pingInterval = pingInterval;
	}

}
