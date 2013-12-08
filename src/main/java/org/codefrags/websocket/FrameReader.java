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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.codefrags.websocket.codec.Base64;

/**
 * @author Austin Miller
 *
 */
public class FrameReader {
	
	private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public void acceptWebsocket() throws Exception {
    	System.out.println("opening socket");
    	ServerSocket serverSocket = new ServerSocket(8090);
    	Socket client = serverSocket.accept();
        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        
        
        negotiateHeaders(out, in);
 
        read(client.getInputStream());
        
        serverSocket.close();
    }
	
	/**
	 * @param out
	 * @param in
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	private void negotiateHeaders(PrintWriter out, BufferedReader in) throws IOException, NoSuchAlgorithmException {
        Map<String, String> headers = readHeaders(in);
        
        if(headers.get("sec-websocket-version").equals("13") == false) {
        	throw new IOException("unsupported websocket version");
        }
        
        System.out.println("\n>>>> our turn\n");
        
        String key = headers.get("sec-websocket-key")+ WEBSOCKET_GUID;
        String accept = hashWebsocketKey(key);
        
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
        		+ "Upgrade: websocket\r\n"
        		+ "Connection: Upgrade\r\n"
        		+ "Sec-WebSocket-Accept: " +accept+"\r\n"
        		+ "Sec-WebSocket-Protocol: BeanScript\r\n\r\n";
		out.write(response);
        out.flush();
        System.out.println(response);
	}
	
    public static void main(String[] args) {
    	try {
    		
    		
    		FrameReader fr = new FrameReader();
    		
   		
    		fr.acceptWebsocket();
    		
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    	System.out.println("finis");
    }
    
	/**
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private Map<String, String> readHeaders(BufferedReader in) throws IOException {
		String line = in.readLine();
        Map<String,String> headers = new HashMap<String, String>();
        
        System.out.println(line);
        
        while(!line.isEmpty() && line != null) {
        	line = in.readLine();
        	
        	System.out.println(line);
        	String [] tokens = line.split(":");
        	if(tokens.length == 2) {
        		headers.put(tokens[0].trim().toLowerCase(), tokens[1].trim());
        	}
        	
        }
		return headers;
	}
	
	/**
	 * @param key
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	private String hashWebsocketKey(String key) throws NoSuchAlgorithmException {
		MessageDigest msg = MessageDigest.getInstance("SHA-1");
        msg.reset();
        msg.update(key.getBytes());
        String accept =Base64.encodeBytes(msg.digest());
		return accept;
	}
	
	private void read(InputStream in) throws IOException {
		
		byte [] bytes = new byte[1<<16];
		int read = 0;
		int written = 0;
		
		Frame frame = Frame.newFrame();
		
		while((read = in.read(bytes)) != -1) {
			written = frame.writeBytes(bytes, 0, read);

			while(written != read) {
				frame = Frame.newFrame();
				written += frame.writeBytes(bytes, written, read-written);
			}

			if(frame.isConstructed()) {
				frame = Frame.newFrame();
			}
		}
	}
}
