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

/**
 * This class is meant to encapsulate a websocket frame while also keeping
 * buffers around to limit the amount of allocation that occurs for better performance.
 * 
 * @author Austin Miller
 *
 */
public class Frame {
	
	public static final int MAX_DATA_SIZE = 2+8+4;
	
	private static final int KEEP_FRAMES_COUNT = 30;
	private static final int DATA_BUFFER_SIZE=1<<18; // 1/4 megabyte
	private static final int MAX_BUFFER_SIZE=1<<20; // a megabyte
	private static final int MAX_FRAME_LENGTH=1<<25; // 32 megabytes
	
	/**
	 * the max amount of memory this can become is ....
	 * KEEP_FRAMES_COUNT*(MAX_data_SIZE + MAX_BUFFER_SIZE + class overhead)
	 */
	private static List<Frame> storage = new ArrayList<Frame>();
	
	public static Frame newFrame() {
		
		Frame frame;
		int size = storage.size();
		if(size == 0) {
			frame = new Frame();
		} else {
			frame = storage.get(size-1);
			storage.remove(size-1);
		}
		frame.reset();
		return frame;
	}
	
	public static void returnFrame(Frame frame) {
		if(storage.size() < KEEP_FRAMES_COUNT) {
			storage.add(frame);
		}
	}

	private Frame() {
		
	}
	

	/**
	 * It is difficult to come up with an optimal size. 
	 */
	private byte [] data = new byte[DATA_BUFFER_SIZE];
	
	private int length;
	private int dataSize;
	private int expectedHeaderSize;
	private int maskKey;
	
	/**
	 * This resets values such that the frame is reusable to store
	 * a new frame. 
	 */
	public void reset() {
		length = -1;
		dataSize=0;
		expectedHeaderSize=20; // to foil the constructed() function
		
		if(data.length >= MAX_BUFFER_SIZE) {
			data = new byte[DATA_BUFFER_SIZE];
		}
	}
	
	public boolean isConstructed() {
		return (dataSize == expectedHeaderSize + length) ? true : false;
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
		
		int bytesToRead = length;
		if(length !=-1) {
			int bytesNeeded = expectedHeaderSize + length;
			int bytesLeft = bytesNeeded - dataSize;
			bytesToRead = Math.min(bytesLeft, length);
		}
		
		System.arraycopy(bytes, offset, data, dataSize, bytesToRead);
		
		dataSize+=bytesToRead;
		
		if(length != -1 && dataSize == expectedHeaderSize + length) {
			System.out.println(this);
			returnFrame(this);
		}
		
		return bytesToRead;
	}
	
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("\n\n>>>Frame\n");
		
		int count = 0;
		for(int i = 0;i<dataSize;++i) {
			sb.append(" "+binary(data[i]));
			++count;
			if(count == 32/Byte.SIZE) {
				sb.append("\n");
				count = 0;
			}
		}
		return sb.toString();
	}

	
    private String binary(byte word) {
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
		
		expectedHeaderSize = 2;
		
		if(isMasked()) {
			expectedHeaderSize+=4;
		}
		
		int size = data[1] & 0x7F;
		if(size == 126) {
			expectedHeaderSize+=2;
		} else if(size == 127) {
			expectedHeaderSize+=8;
		}
		
		if(dataSize + length < expectedHeaderSize) {
			return;
		}
		
		int offset2 = 0;
		for(int i = dataSize;i<expectedHeaderSize;++i) {
			data[i] = bytes[offset + offset2];
			++offset2;
		}
		
		calculateLength();
		
		if(isMasked()) {
			for(int i = expectedHeaderSize-4;i<expectedHeaderSize;++i) {
				maskKey = (maskKey << Byte.SIZE) | data[i];
			}
		}
	}

	/**
	 * @return whether the data is masked
	 */
	private boolean isMasked() {
		return (data[1]>>Byte.SIZE) == 1 ? true : false;
	}
	
	private void calculateLength() throws IOException {
		int size = data[1] & 0x7F;
		
		if(size < 126) {
			length = size;
		}
		
		if(size==126) {
			length = (data[2] << Byte.SIZE) | data[3];
		}
		
		// the final case, bytes 3-10 are the length
		// we're choosing not to support sizes greater 32 bits (about 4billion)
		
		if((data[6] | data[7] | data[8] | data[9]) != 0 ||
				data[5]>>Byte.SIZE == 1) {
			throw new IOException("Frame data length is larger than 2^31 which is not supported.");
		}
		
		length = 0;
		for(int i = 2;i<10;++i) {
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
}
