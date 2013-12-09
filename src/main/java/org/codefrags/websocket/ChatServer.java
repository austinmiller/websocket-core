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

import java.util.HashMap;
import java.util.Map;

import org.codefrags.websocket.WebSocketListener;
import org.codefrags.websocket.WebSocketServer;
import org.codefrags.websocket.WebSocketUser;

/**
 * @author Austin Miller
 *
 */
public class ChatServer implements WebSocketListener {
	
	private static final String SERVER_NAME = "<server>";


	public static void main(String args[]) {
		ChatServer server = new ChatServer();
		server.run();
		System.out.println(">>> finis");
	}


	private void run() {
		Thread wsServerThread = new Thread(webSocketServer);
		wsServerThread.start();
		
		try {
			wsServerThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}


	private WebSocketServer webSocketServer;
	private Map<WebSocketUser,String> users = new HashMap<WebSocketUser,String>();
	
	
	public ChatServer() {
		webSocketServer = new WebSocketServer();
		webSocketServer.setProtocol("chat");
		webSocketServer.setWebSocketListener(this);
		webSocketServer.setPort(8090);
		webSocketServer.setPingInterval(60*1000); // ping every minute
	}


	/* (non-Javadoc)
	 * @see org.codefrags.websocket.WebSocketListener#onNewUser(org.codefrags.websocket.WebSocketUser)
	 */
	@Override
	public void onNewUser(WebSocketUser webSocketUser) {
		users.put(webSocketUser,"guest-"+webSocketUser.getId());
		System.out.println("New user "+webSocketUser.getId());
		webSocketUser.send("echo:"+SERVER_NAME+": You are connected.");
	}


	/* (non-Javadoc)
	 * @see org.codefrags.websocket.WebSocketListener#onMessage(org.codefrags.websocket.WebSocketUser, java.lang.String)
	 */
	@Override
	public void onMessage(WebSocketUser webSocketUser, String message) {
		if(message.startsWith("send:")) {
			broadcast(webSocketUser,users.get(webSocketUser),message.substring(5));
		} else if(message.startsWith("name:")) {
			rename(webSocketUser,message.substring(5)); 
		}
	}


	/**
	 * @param webSocketUser
	 */
	private void broadcast(WebSocketUser webSocketUser,String name,String msg) {
		String message = "echo:"+name + ": "+msg;
		
		for(WebSocketUser user : users.keySet()) {
			if(user != webSocketUser) {
				user.send(message);
			}
		}
	}


	/**
	 * @param webSocketUser
	 * @param substring
	 */
	private void rename(WebSocketUser user, String name) {
		String error = null;
		if(name.length() < 4 || name.length() > 32) {
			error = "Name must be between 4 and 32 characters.";
		}
		
		if(name.contains("server")) {
			error = "Illegal name";
		}
		
		if(name.matches("[a-zA-Z0-9]") == false) {
			error = "Please only select digits and characters.";
		}
		
		if(error != null) {
			user.send("name:"+users.get(user));
			user.send("echo:"+SERVER_NAME+":"+ error);
		} else {
			String oldName = users.get(user);
			users.put(user, name);
			broadcast(null,SERVER_NAME,":"+oldName + " renamed to "+name);
			System.out.println("User "+oldName+" renamed to "+name);
		}
	}


	/* (non-Javadoc)
	 * @see org.codefrags.websocket.WebSocketListener#onCloseConnection(org.codefrags.websocket.WebSocketUser)
	 */
	@Override
	public void onCloseConnection(WebSocketUser webSocketUser) {
		System.out.println("Losing "+users.get(webSocketUser));
		users.remove(webSocketUser);
	}
	
}
