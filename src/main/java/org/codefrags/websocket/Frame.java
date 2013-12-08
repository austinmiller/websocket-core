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
	
	/**
	 * There are 2 bytes required , 8 possible
	 * bytes for size, and 4 possible bytes
	 * for a masking key.
	 */
	public static final int MAX_HEADER_SIZE = 2+8+4;
	
	private static final int KEEP_FRAMES_COUNT = 30;
	private static final int DATA_BUFFER_SIZE=1<<18; // 1/4 megabyte
	private static final int MAX_BUFFER_SIZE=1<<20; // a megabyte
	private static final int MAX_FRAME_LENGTH=1<<25; // 32 megabytes
	
	/**
	 * the max amount of memory this can become is ....
	 * KEEP_FRAMES_COUNT*(MAX_HEADER_SIZE + MAX_BUFFER_SIZE + class overhead)
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
	private int dataMarker;
	private int offset;
	private int maskKey;
	
	
	/**
	 * This resets values such that the frame is reusable to store
	 * a new frame. 
	 */
	public void reset() {
		offset=0;
		length = -1;
		dataMarker=0;
		
		if(data.length >= MAX_BUFFER_SIZE) {
			data = new byte[DATA_BUFFER_SIZE];
		}
	}
	
	
	/**
	 * 
	 * 
	 * @param The bytes to append to the header.
	 * @param The offset to begin copying from the bytes.
	 * @param The number of bytes to copy starting at the offset.
	 * @return Whether the header was completely formed.
	 * @throws Exception
	 */
	public boolean writeBytes(byte[] bytes,int offset,int length) throws Exception {
		
		int size = Math.min(length, MAX_HEADER_SIZE - headerSize);
		System.arraycopy(bytes, offset, header, headerSize, size);
		
		headerSize+=size;
		
		
		if(getLength() == -1) {
			return false;
		}

		increaseAllocation();
		
		if(isMasked()) {
			
		}
		
		return true;
	}
	
	/**
	 * @return
	 */
	private boolean isMasked() {
		return (data[1]>>Byte.SIZE) == 1 ? true : false;
	}

	/**
	 * 
	 * @return the length of the data, -1 if there's not enough header data
	 * @throws IOException 
	 */
	public int getLength() throws IOException {
		if(length >= 0) {
			return length;
		}
		
		if(headerSize < 2) {
			return -1;
		}
		
		int size = header[1] & 0x7F;
		
		if(size < 126) {
			length = size;
			return length;
		}
		
		if( (size == 126 && headerSize < 4) ||
				(size == 127 && headerSize < 10)) {
			return -1;
		}
		
		if(size==126) {
			length = (header[2] << Byte.SIZE) | header[3];
			return length;
		}
		
		// the final case, bytes 3-10 are the length
		// we're choosing not to support sizes greater 32 bits (about 4billion)
		
		if((header[6] | header[7] | header[8] | header[9]) != 0) {
			throw new IOException("Frame data length is larger than 2^32 which is not supported.");
		}
		
		length = 0;
		for(int i = 2;i<6;++i) {
			length = (length << Byte.SIZE) | header[i];
		}
		return length;
	}

	
	public void addDataByte(byte b) {
		data[dataMarker] = b;
		++dataMarker;
	}
	
	private void increaseAllocation() throws Exception {
		
		int newSize = (int) length;
		if(newSize == -1 || newSize > data.length) {
			return;
		}
		
		while(true) {
			newSize <<=1; // this doubles it;

			if(newSize > MAX_FRAME_LENGTH) {
				throw new IOException("Too large a frame requested by peer.  This might be malicious.");
			}
			
			if(newSize > length) {
				break;
			}
		}
		
		byte[] newData = new byte[newSize];
		System.arraycopy(data, 0, newData, 0, data.length);
		data = newData;
	}
	
	public void addDataBytes(byte[] bytes,int offset,int length) {
		
		
		
		int size = Math.min(length, MAX_HEADER_SIZE - dataMarker);
		System.arraycopy(bytes, offset, header, dataMarker, size);
		
		dataMarker+=size;
	}
	 
}
