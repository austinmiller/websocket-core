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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codefrags.websocket.codec.Base64;

/**
 * This class is responsible for sending and receiving websocket frames in an NIO 
 * compatible way. It is also intended to negotiate the header acceptance and
 * all control frames, pings, pongs, and close.  Actual frame manipulation is
 * done by other classes.  This is effectively a "WebSocket" NIO wrapper, but
 * is semantically named to be thought of as a connected user. 
 * 
 * It is meant to be consumed by a thread, so all public interface methods need to
 * be thread safe or throw ConcurrentModificationException.
 *  
 * @author Austin Miller
 * @see org.codefrags.websocket.MaskedFrame
 * @see org.codefrags.websocket.FrameBuffer
 */
public class WebSocketUser {
	/**
	 * 
	 */
	private static final int CAPACITY = 1<<16;
	protected static final Log logger = LogFactory.getLog(WebSocketUser.class);
	private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
	
	/**
	 * The amount of time to wait, in milliseconds, before deciding to close a
	 * websocket for not responding to a ping with a pong frame. 
	 */
	public static long PING_WAIT_TIME = 30*1000; // 30 seconds
	private static int nextUserId=0;
	
	public enum Status {
		CONNECTING,
		RESPONDING,
		OPEN,
		CLOSING;
	}
	
	private int id;
	private Map<String,String> headers = new HashMap<String,String>();
	private SocketChannel socketChannel;
	private Queue<FrameBuffer> outgoingFrames = new ConcurrentLinkedQueue<FrameBuffer>();
	private List<MaskedFrame> frames = new ArrayList<MaskedFrame>();
	private ByteBuffer in = ByteBuffer.allocate(CAPACITY);
	private ByteBuffer out = ByteBuffer.allocate(CAPACITY);
	private MaskedFrame frame;
	private WebSocketListener webSocketListener;
	private long pingSentTime = 0;
	private int read;
	private Status status = Status.CONNECTING;
	private int written;
	private String protocol;
	
	WebSocketUser(SocketChannel socketChannel,WebSocketListener webSocketListener,String protocol) throws IOException {
		id = nextUserId;
		++nextUserId;
		this.socketChannel = socketChannel;
		this.webSocketListener = webSocketListener;
		this.protocol = protocol;
	}
	
	@Override
	public int hashCode() {
		return id;
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == null || other instanceof WebSocketUser) {
			return false;
		}

		WebSocketUser that = (WebSocketUser) other;

		return this.id == that.id;
	}

	public int getId() {
		return id;
	}

	Map<String,String> getHeaders() {
		return headers;
	}
	
	public void send(String message) {
		outgoingFrames.add(FrameBuffer.createTextFrame(message));
	}

	/**
	 * Attempt to write outgoing data across the socket. Writes header and
	 * frame data.
	 * 
	 * @throws IOException 
	 */
	void write() throws IOException {
		
		if(status == Status.RESPONDING) {
			
			writeOut();
			
			if(out == null) {
				status = Status.OPEN;
			}
			
		} else if (status == Status.OPEN) {
			
			if(out == null && outgoingFrames.size() == 0) {
				return;
			}
			
			if(out == null) {
				out = outgoingFrames.poll().getBuffer();
			}
			
			writeOut();
		}
	}

	/**
	 * @throws IOException 
	 * 
	 */
	private void writeOut() throws IOException {
		if(socketChannel.write(out) == -1) {
			throw new IOException("socket is no longer valid");
		}
		
		if(out.position() == out.limit()) {
			out = null;
		}
	}

	/**
	 * Intentionally package private.
	 * 
	 * Read as many bytes as we can and handle the result.
	 * 
	 * @param webSocketListener
	 * @throws IOException 
	 */
	boolean read() throws IOException {
		if(status == Status.CLOSING) {
			return false;
		}
		
		if (socketChannel.isConnected() == false) {
			return false;
		}
		
		if(pingSentTime != 0) {
			if(System.currentTimeMillis() - pingSentTime > PING_WAIT_TIME) {
				socketChannel.close();
				status = Status.CLOSING;
				throw new IOException("Failed to receive pong in time, connection is now invalid.");
			}
		}
		read = socketChannel.read(in);
		
		if(read == 0) {
			return true; // we should never really read 0
		}
		
		if(read == -1) {
			return false; // connection reset by peer
		}
		
		switch(status) {
		case CONNECTING:
			receiveClientHeader();
			break;
		case OPEN:
			readIntoFrame();
			break;
		case RESPONDING:
			break;
		default:
			break;
		
		}
		
		
		return true;

	}

	/**
	 * @throws IOException
	 * 
	 */
	private void composeServerHeader() throws IOException {
		if (logger.isDebugEnabled()) {
			logger.debug("Sending server headers for user: " + id);
		}

		if (headers.get("sec-websocket-version").equals("13") == false) {
			throw new IOException("unsupported websocket version");
		}
		
		if(headers.get("sec-websocket-protocol").contains(protocol) == false) {
			throw new IOException("unsupported websocket subprotocol");
		}

		String accept;
		try {
			accept = hashWebsocketKey();
		} catch (NoSuchAlgorithmException e) {
			logger.error(e.getMessage(), e);
			throw new IOException("couldn't negotiate headers due to exception: " + e.getMessage());
		}

		String response = String.format(
				"HTTP/1.1 101 Switching Protocols\r\n" 
				+ "Upgrade: websocket\r\n"
				+ "Connection: Upgrade\r\n"
				+ "Sec-WebSocket-Accept: %s\r\n"
				+ "Sec-WebSocket-Protocol: %s\r\n\r\n",
				accept, protocol);

		out.clear();
		out.put(response.getBytes());
		out.flip();
		logger.debug(response);
		status = Status.RESPONDING;
	}

	/**
	 * @throws IOException 
	 * @returns whether the socket is still valid
	 */
	private void receiveClientHeader() throws IOException {
		if(in.position() == in.limit()) {
			throw new IOException("Probably malicious attempt to flood buffers.");
		}
		
		byte[] bytes = in.array();
        
        int pos = in.position() - 4;
        if(
        		pos < 0 ||
        		bytes[pos] != '\r' ||
				bytes[pos+1] != '\n' ||
				bytes[pos+2] != '\r' ||
				bytes[pos+3] != '\n') {
        	return ;
        }
        
        
        readHeaders();
        composeServerHeader();
	}
	
	/**
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private void readHeaders() throws IOException {
		
		String header = new String(in.array(),0,in.position()) ;
		in.clear();
		
		logger.debug(header);
		
		headers = new HashMap<String, String>();
        
        for(String line : header.split("\r\n")) {
        	String [] tokens = line.split(":");
        	if(tokens.length == 2) {
        		headers.put(tokens[0].trim().toLowerCase(), tokens[1].trim());
        	}
        	
        }
	}
	
	/**
	 * Converts the web socket key to the proper hash accept key as dictacted by
	 * <a href="http://tools.ietf.org/html/rfc6455#section-4.2.2">RFC 6455 #4.2.2</a>
	 * 
	 * @param key
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	private String hashWebsocketKey() throws NoSuchAlgorithmException {
        
        String key = headers.get("sec-websocket-key")+ WEBSOCKET_GUID;
        
		if(logger.isDebugEnabled()) {
			logger.debug(String.format("sec-websocket-key: %s from user: %d",headers.get("sec-websocket-key"),id));
		}
		
		MessageDigest msg = MessageDigest.getInstance("SHA-1");
        msg.reset();
        msg.update(key.getBytes());
        key = Base64.encodeBytes(msg.digest());

        if(logger.isDebugEnabled()) {
			logger.debug(String.format("Sec-WebSocket-Accept: %s for user %d",key,id));
		}
        
        
		return key;
	}

	/**
	 * Use the incoming bytes to fill and construct frames.
	 * 
	 * @param webSocketListener
	 * @param read
	 * @throws IOException
	 */
	private void readIntoFrame() throws IOException {
		
		if(frame == null) {
			frame = MaskedFrame.newFrame();
		}
		
		in.flip();
		
		written = frame.writeBytes(in.array(), in.position(), in.limit());

		while(written != in.limit()) {
			handleFrame();
			frame = MaskedFrame.newFrame();
			written += frame.writeBytes(in.array(), written, in.limit()-written);
		}

		if(frame.isConstructed()) {
			handleFrame();
			frame = null;
		}
		in.clear();
	}

	/**
	 * Assumes that the currently worked on frame is constructed and must be handled.
	 * 
	 * Control frames must be responded to even if they occur between fragmented frames, and
	 * it is desirable that we respond to them right away, despite what we're trying to write on
	 * the wire.
	 * 
	 * @param frame
	 * @throws IOException 
	 */
	private void handleFrame() throws IOException {
		
		if(logger.isDebugEnabled()) { logger.debug(">>> Frame read by "+id); }
		
		if(frame.getOpCode() == OpCode.CLOSE && status != Status.CLOSING) {
			sendCloseFrame();
			return;
		}
		
		if(frame.getOpCode() == OpCode.PING) {
			logger.debug(">>> Ping"); 
			outgoingFrames.add(FrameBuffer.createControlFrame(OpCode.PONG));
			return;
		}
		
		if(frame.getOpCode() == OpCode.PONG) {
			logger.debug(">>> Pong");
			pingSentTime = 0;
			return;
		}
		
		if(frame.isFinal() == false) {
			logger.debug(">>> Continuation Frame");
			frames.add(frame);
			return;
		}
		
		if(frames.size() > 0) {
			
			frames.add(frame);
			webSocketListener.onMessage(this, MaskedFrame.join(frames));
			for(MaskedFrame mf : frames) {
				MaskedFrame.returnFrame(mf);
			}
			
			frames.clear();
		} else {
			webSocketListener.onMessage(this, frame.getText());
			MaskedFrame.returnFrame(frame);
		}
	}

	/**
	 * Intentionally package private.
	 * 
	 * Send a close control frame and change internal status.
	 *  
	 * @throws IOException
	 */
	void sendCloseFrame() {
		status = Status.CLOSING;
		outgoingFrames.add(FrameBuffer.createControlFrame(OpCode.CLOSE));
	}
	
	void close() throws IOException {
		socketChannel.close();
		webSocketListener.onCloseConnection(this);
	}
		

	public void ping() {
		outgoingFrames.add(FrameBuffer.createControlFrame(OpCode.PING));
		pingSentTime = System.currentTimeMillis();
	}
	
	public Status getStatus() {
		return status;
	}

	/**
	 * Fails the connection, mostly due to a protocol error.
	 */
	public void fail() {
		status = Status.CLOSING;
		try {
			socketChannel.close();
			logger.debug("Failed the connection for user " + getId());
		} catch (IOException e) {
			logger.error(e);
		}
	}


}
