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

import java.nio.ByteBuffer;

/**
 * @author Austin Miller
 *
 */
public class FrameBuffer {
	
	private OpCode opCode = OpCode.TEXT;
	private ByteBuffer buffer;
	private int dataLength = 0;
	
	public static FrameBuffer createTextFrame(String text) {
		byte [] bytes = text.getBytes();
		
		FrameBuffer fb = new FrameBuffer();
		
		fb.createBuffer(bytes);
		return fb;
	}
	
	public static FrameBuffer createControlFrame(OpCode opCode) {
		byte [] bytes = "ctl".getBytes();
		
		FrameBuffer fb = new FrameBuffer();
		
		fb.opCode = opCode;
		
		fb.createBuffer(bytes);
		return fb;
	}
	
	/**
	 * @param bytes
	 */
	private void createBuffer(byte[] bytes) {
		dataLength = bytes.length;
		int size = 0;
		int bsize = 2;
		
		if(bytes.length > 125) {
			if(bytes.length < 1<<16) {
				bsize += 2;
				size=126;
			} else {
				bsize +=8;
				size = 127;
			}
		} else {
			size = bytes.length;
		}
		
		buffer = ByteBuffer.allocate(bsize + bytes.length);
		
		buffer.put((byte) (0x80 | opCode.getValue()));
		buffer.put((byte) (size & 0x7F));
		
		if(size==126) {
			buffer.put((byte) (bytes.length >> (Byte.SIZE)));
			buffer.put((byte) (bytes.length));
		}
		
		if(size == 127) {
			buffer.put((byte) 0);
			buffer.put((byte) 0);
			buffer.put((byte) 0);
			buffer.put((byte) 0);
			buffer.put((byte) (bytes.length >> (3*Byte.SIZE)));
			buffer.put((byte) (bytes.length >> (2*Byte.SIZE)));
			buffer.put((byte) (bytes.length >> (Byte.SIZE)));
			buffer.put((byte) (bytes.length));
		}
		buffer.put(bytes);
		buffer.flip();
	}

	private FrameBuffer() {
		
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("\n\n>>> Outgoing Header\n");
		
		binary(sb,buffer.array(),0,buffer.limit() - dataLength);
		sb.append(">>> Outgoing Payload\n");
		binary(sb,buffer.array(),0,dataLength);
		
		return sb.toString();
	}

	/**
	 * @param sb
	 * @param bytes 
	 */
	private void binary(StringBuilder sb, byte[] bytes,int offset,int length) {
		int count = 0;
		int stop = offset + length;
		for(int i = offset;i<stop;++i) {
			sb.append(" "+MaskedFrame.binary(bytes[i]));
			++count;
			if(count == 32/Byte.SIZE) {
				sb.append("\n");
				count = 0;
			}
		}
		sb.append("\n");
	}

	/**
	 * @return
	 */
	public ByteBuffer getBuffer() {
		return buffer;
	}


}
