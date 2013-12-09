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
import java.io.OutputStream;

/**
 * @author Austin Miller
 *
 */
public class WritableFrame {
	
	byte [] payload = null;
	byte [] header = null;
	OpCode opCode = OpCode.TEXT;
	boolean mask = false;
	
	public WritableFrame() {
		
	}
	
	public WritableFrame(String text) {
		setPayload(text);
	}
	
	public WritableFrame(OpCode opCode) {
		
		this.opCode = opCode;
		
		setPayload("ctl");
	}
	
	public void write(OutputStream out) throws IOException {
		if(header == null) {
			constructHeader();
		}
		
		System.out.println(this);
		
		out.write(header);
		out.write(payload);
	}
	
	
	/**
	 * 
	 */
	private void constructHeader() {
		int headerSize = 2;
		int payloadSize = payload.length;
		int size;
		if(mask) {
			
			headerSize += 4;
		}
		if(payloadSize > 125) {
			if(payloadSize > 1<<16) {
				headerSize += 2;
				size=126;
			} else {
				headerSize +=8;
				size = 127;
			}
		} else {
			size = payloadSize;
		}
		
		header = new byte[headerSize];
		
		header[0] = (byte) (0x80 | opCode.getValue());
		header[1] = (byte) (mask ? 0x80 : 0 | size);
		
		if(size==126) {
			header[2] = (byte) (payloadSize >> (Byte.SIZE));
			header[3] = (byte) (payloadSize);
		}
		
		if(size == 127) {
			header[2] = header[3] = header[4] = header[5] = 0;
			header[6] = (byte) (payloadSize >> (3*Byte.SIZE));
			header[7] = (byte) (payloadSize >> (2*Byte.SIZE));
			header[8] = (byte) (payloadSize >> (Byte.SIZE));
			header[9] = (byte) (payloadSize);
		}
		
		// TODO actually mask, LOL
		if(mask) {
			header[headerSize-4] = 0;
			header[headerSize-3] = 0;
			header[headerSize-2] = 0;
			header[headerSize-1] = 0;
		}
		
	}


	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("\n\n>>> Outgoing Header\n");
		
		binary(sb,header);
		sb.append(">>> Outgoing Payload\n");
		binary(sb,payload);
		
		return sb.toString();
	}

	/**
	 * @param sb
	 * @param bytes 
	 */
	private void binary(StringBuilder sb, byte[] bytes) {
		int count = 0;
		for(int i = 0;i<bytes.length;++i) {
			sb.append(" "+MaskedFrame.binary(bytes[i]));
			++count;
			if(count == 32/Byte.SIZE) {
				sb.append("\n");
				count = 0;
			}
		}
		sb.append("\n");
	}
	

	public byte[] getPayload() {
		return payload;
	}
	public void setPayload(byte[] payload) {
		this.payload = payload;
	}
	public void setPayload(String payload) {
		this.payload = payload.getBytes();
	}
	public byte[] getHeader() {
		return header;
	}
	public void setHeader(byte[] header) {
		this.header = header;
	}
	public OpCode getOpCode() {
		return opCode;
	}
	public void setOpCode(OpCode opCode) {
		this.opCode = opCode;
	}
	public boolean isMask() {
		return mask;
	}
	public void setMask(boolean mask) {
		this.mask = mask;
	}
	
	

}
