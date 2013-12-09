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
 * @author Austin Miller
 *
 */
public enum OpCode {

	TEXT(0x1),
	BINARY(0x2),
	PING(0x9),
	PONG(0xA),
	CLOSE(0x8);
	
	private int value;

	private OpCode(int hex) {
		this.value = hex;
	}
	
	public int getValue() {
		return value;
	}
}
