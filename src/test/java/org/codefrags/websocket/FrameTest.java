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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Random;

import org.junit.Test;

/**
 * @author Austin Miller
 *
 */
public class FrameTest {

	/**
	 * Test method for {@link org.codefrags.websocket.MaskedFrame#writeBytes(byte[], int, int)}.
	 * @throws IOException 
	 */
	@Test
	public void testWriteBytes() throws IOException {
		
		MaskedFrame frame = MaskedFrame.newFrame();
		
		byte[] bytes = new byte[9];
		
		Random r = new Random();
		bytes[0] = (byte) 0x81;
		bytes[1] = (byte) 0x83;
		bytes[2] = (byte) r.nextInt();
		bytes[3] = (byte) r.nextInt();
		bytes[4] = (byte) r.nextInt();
		bytes[5] = (byte) r.nextInt();
		bytes[6] = 'w';
		bytes[7] = 'o';
		bytes[8] = 'o';
		
		bytes[6] ^= bytes[2];
		bytes[7] ^= bytes[3];
		bytes[8] ^= bytes[4];
		
		for(int i = 0;i<bytes.length;++i) {
			frame.writeBytes(bytes, i, 1);
		}
		
		assertEquals(OpCode.TEXT,frame.getOpCode());
		assertEquals("woo", frame.getText());
	}

}
