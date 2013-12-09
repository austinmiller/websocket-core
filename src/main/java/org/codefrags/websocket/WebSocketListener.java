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

/**
 * 
 * All of these are going to be called by the WebSocketServer thread and so whatever
 * they do may be in a race condition with the thread(s) that started the server.
 * 
 * @author Austin Miller
 *
 */
public interface WebSocketListener {
	
	public void onNewUser(WebSocketUser webSocketUser);
	
	public void onMessage(WebSocketUser webSocketUser,String message);
	
	public void onCloseConnection(WebSocketUser webSocketUser);

}
