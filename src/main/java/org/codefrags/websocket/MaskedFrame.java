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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class is meant to encapsulate a websocket frame while also keeping
 * buffers around to limit the amount of allocation that occurs for better performance.
 * 
 * @author Austin Miller
 *
 */
public class MaskedFrame {
	protected static final Log logger = LogFactory.getLog(MaskedFrame.class);
	
	OpCode opCode;

	private static OpCode [] OPCODES = new OpCode[1<<4];

	{
		for(OpCode opCode : OpCode.values()) {
			OPCODES[opCode.getValue()] = opCode;
		}
	}
	
	public static final int MAX_DATA_SIZE = 2+8+4;
	
	private static final int KEEP_FRAMES_COUNT = 30;
	private static final int DATA_BUFFER_SIZE=1<<18; // 1/4 megabyte
	private static final int MAX_BUFFER_SIZE=1<<20; // a megabyte
	private static final int MAX_FRAME_LENGTH=1<<25; // 32 megabytes
	
	/**
	 * the max amount of memory this can become is ....
	 * KEEP_FRAMES_COUNT*(MAX_data_SIZE + MAX_BUFFER_SIZE + class overhead)
	 */
	private static List<MaskedFrame> storage = new ArrayList<MaskedFrame>();
	
	public static MaskedFrame newFrame() {
		
		MaskedFrame frame;
		int size = storage.size();
		if(size == 0) {
			frame = new MaskedFrame();
		} else {
			frame = storage.get(size-1);
			storage.remove(size-1);
		}
		frame.reset();
		return frame;
	}
	
	public static void returnFrame(MaskedFrame frame) {
		if(storage.size() < KEEP_FRAMES_COUNT) {
			storage.add(frame);
		}
	}
	

	/**
	 * Joins multiple fragmented continuation frames and a final frame, expecting them in order in the list
	 * and returning the String;
	 * @param frames
	 * @return
	 */
	public static String join(List<MaskedFrame> frames) {
		String text;

		// TODO Consider rewriting to avoid copying the bytes twice
		int bytesToAllocate = 0;
		for(MaskedFrame frame : frames) {
			bytesToAllocate += frame.data.length - frame.headerSize;
		}
		
		int marker = 0;
		
		// TODO Consider how to fix the error where bytesToAllocate > 2^31
		byte [] bytes = new byte[bytesToAllocate];
		
		for(MaskedFrame frame : frames) {
			int length = frame.data.length - frame.headerSize;
			System.arraycopy(frame.data, frame.headerSize, bytes, marker, length);
			marker += length;
		}
		
		text = new String(bytes);
		
		if(logger.isDebugEnabled()) {
			logger.debug("marker = " + marker + " bytesToAllocate=" +bytesToAllocate);
			logger.debug("joined frames text: " + text);
		}
		return text;
	}

	private MaskedFrame() {
		
	}

	/**
	 * It is difficult to come up with an optimal size. 
	 */
	private byte [] data = new byte[DATA_BUFFER_SIZE];
	
	private int length;
	private int dataSize;
	private int headerSize;

	private boolean constructed;
	
	/**
	 * This resets values such that the frame is reusable to store
	 * a new frame. 
	 */
	public void reset() {
		length = -1;
		dataSize=0;
		constructed=false;
		
		if(data.length >= MAX_BUFFER_SIZE) {
			data = new byte[DATA_BUFFER_SIZE];
		}
	}
	
	public boolean isConstructed() {
		return constructed;
	}
	
	public OpCode getOpCode() {
		return OPCODES[data[0] & 0xF]; 
	}
	
	/**
	 * 
	 * 
	 * @param The bytes to append to the data.
	 * @param The offset to begin copying from the bytes.
	 * @param The number of bytes to copy starting at the offset.
	 * @return The number of bytes written, less than the length means the frame finished
	 * @throws Exception
	 */
	public int writeBytes(byte[] bytes,int offset,int length) throws IOException {
		
		if(dataSize + length > data.length) {
			increaseAllocation(dataSize + length);
		}
		
		copyHeader(bytes,offset,length);
		
		int bytesToWrite = length;
		if(this.length !=-1) {
			int bytesNeeded = headerSize + this.length;
			int bytesLeft = bytesNeeded - dataSize;
			bytesToWrite = Math.min(bytesLeft, length);
		}
		
		System.arraycopy(bytes, offset, data, dataSize, bytesToWrite);
		
		dataSize+=bytesToWrite;
		
		if(dataSize == headerSize + this.length && dataSize > headerSize) {
			finishFrame();
		}
		
		return bytesToWrite;
	}

	/**
	 * 
	 */
	private void finishFrame() {
		if(constructed == true) {
			return;
		}
		
		constructed = true;

		if(isMasked()) {
			unmask();
		}
	}
	
	private void unmask() {
		int maskKeyOffset=headerSize-4;
		int mod = 0;
		for(int i = headerSize;i<dataSize;++i) {
			mod = (i - headerSize) % 4;
			data[i] ^= data[maskKeyOffset+mod];
		}
	}
	
	public String getText() {
		String text =new String(data,headerSize,dataSize-headerSize);
		
		return text;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("\n\n>>> Frame\n");
		
		int count = 0;
		for(int i = 0;i<dataSize;++i) {
			sb.append(" "+binary(data[i]));
			++count;
			if(count == 32/Byte.SIZE) {
				sb.append("\n");
				count = 0;
			}
		}
		sb.append("\n");
		sb.append(">>> Payload\n");
		sb.append(getText());
		
		return sb.toString();
	}

	
    public static String binary(byte word) {
    	int size = Byte.SIZE;
    	
    	String s = "";
    	for(int i = size-1;i>=0;--i) {
    		s += 1 & (word >> i);
    	}
    	return s;
    }

	/**
	 * @param bytes
	 * @param offset2
	 * @param length2
	 * @throws IOException 
	 */
	private void copyHeader(byte[] bytes, int offset, int length) throws IOException {
		if(dataSize + length < 2) {
			return;
		}
		
		if(dataSize == 0) {
			data[0] = bytes[offset];
			data[1] = bytes[offset+1];
		} else if(dataSize == 1){
			data[1] = bytes[offset];
		}
		
		headerSize = 2;
		
		if(isMasked()) {
			headerSize+=4;
		}
		
		int size = data[1] & 0x7F;
		if(size == 126) {
			headerSize+=2;
		} else if(size == 127) {
			headerSize+=8;
		}
		
		if(dataSize + length < headerSize) {
			return;
		}
		
		int offset2 = 0;
		for(int i = dataSize;i<headerSize;++i) {
			data[i] = bytes[offset + offset2];
			++offset2;
		}
		
		calculateLength();
	}

	/**
	 * @return whether the data is masked
	 */
	private boolean isMasked() {
		return ((data[1]>>Byte.SIZE-1) & 1) == 1 ? true : false;
	}
	
	private void calculateLength() throws IOException {
		int size = data[1] & 0x7F;
		
		if(size < 126) {
			length = size;
			return;
		}
		
		if(size==126) {
			length = (data[2] << Byte.SIZE) | data[3];
			return;
		}
		
		// the final case, bytes 3-10 are the length
		// we're choosing not to support sizes greater 32 bits (about 4billion)
		
		if((data[2] | data[3] | data[4] | data[5]) != 0 ||
				((data[6]>>Byte.SIZE-1) & 1) == 1) {
			throw new IOException("Frame data length is larger than 2^31 which is not supported.");
		}
		
		length = 0;
		for(int i = 6;i<10;++i) {
			length = (length << Byte.SIZE) | data[i];
		}
	}

	/**
	 * 
	 * @return the length of the data in bytes
	 */
	public int getLength() throws IOException {
		return length;
	}
	
	private void increaseAllocation(int desiredSize) throws IOException {

		int newSize = data.length;
		while(true) {
			newSize <<= 1;
			if(newSize < 0 || newSize > MAX_FRAME_LENGTH) {
				throw new IOException("Too large a frame requested by peer.  This might be malicious.");
			} 
			if(newSize > desiredSize) {
				break;
			}
		}
		
		byte[] newData = new byte[newSize];
		System.arraycopy(data, 0, newData, 0, data.length);
		data = newData;
	}

	/**
	 * @return whether the constructed frame is a final frame or a continuation frame
	 */
	public boolean isFinal() {
		return (data[0] & 0x80) == 0x80 ? true : false;
	}

}
