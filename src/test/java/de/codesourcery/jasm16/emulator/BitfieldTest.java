/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.jasm16.emulator;

import junit.framework.TestCase;
import de.codesourcery.jasm16.utils.Bitfield;

public class BitfieldTest extends TestCase {

	
	private Bitfield bitfield;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		bitfield = new Bitfield(65536);
	}
	
	public void test() {
		
		bitfield.setBit( 0 );
		assertTrue( bitfield.isSet( 0 ) );
		assertFalse( bitfield.isSet( 1 ) );
		assertFalse( bitfield.isSet( 2 ) );
		
		bitfield.setBit( 65535 );
		assertTrue( bitfield.isSet( 65535 ) );
		
		bitfield.setBit( 64 );
		assertTrue( bitfield.isSet( 64 ) );	
		
		bitfield.setBit( 65 );
		assertTrue( bitfield.isSet( 65 ) );
		
		bitfield.clearBit( 65 );
		assertFalse( bitfield.isSet( 65 ) );
		
		bitfield.clearBit( 64 );
		assertFalse( bitfield.isSet( 64 ) );
		
		bitfield.clearBit( 65535 );
		assertFalse( bitfield.isSet( 65535 ) );
		
		bitfield.clear();
		assertFalse( bitfield.isSet( 0 ) );
	}
	
	public void testProtecting() {
		
		final int[] toProtect = { 0x0000, 0x0001, 0x0002, 0x0003, 0x0004,
				0x0005, 0x0006, 0x0007, 0x0061, 0x0063, 0x0064, 0x0065, 0x0066,
				0x0067, 0x0068, 0x0062, 0x0008, 0x0009, 0x000a, 0x000b, 0x000c,
				0x000d, 0x000e, 0x000f, 0x0010, 0x0011, 0x0072, 0x0073, 0x0074,
				0x0075, 0x0076, 0x0077, 0x0012, 0x0013, 0x0014, 0x0015, 0x0016,
				0x0017, 0x0018, 0x0019, 0x001a, 0x001b, 0x001c, 0x001d, 0x001e,
				0x001f, 0x0040, 0x0049, 0x004a, 0x004b, 0x004c, 0x004d, 0x004e,
				0x004f, 0x0050, 0x0051, 0x0058, 0x0059, 0x005a, 0x0089, 0x008a,
				0x008b, 0x008c, 0x008d, 0x008e, 0x008f, 0x0090, 0x0091, 0x0092,
				0x0093, 0x0094, 0x0097, 0x005b, 0x005c, 0x0098, 0x0099, 0x009a,
				0x009b, 0x009c, 0x009d, 0x009e, 0x009f, 0x00a0, 0x00a1, 0x00a2,
				0x00a3, 0x00a6, 0x005d, 0x005e };
		
		for ( int i = 0 ; i <toProtect.length ; i++ ) 
		{
			final int val = toProtect[i];
			if ( bitfield.isSet( val ) ) {
				fail("Already protected ?");
			}
			bitfield.setBit( val );
			if ( ! bitfield.isSet( val ) ) {
				fail("Not set?");
			}
			if ( bitfield.isSet( 0x0071 ) ) {
				fail("ERROR");
			}
		}
	}
	
	public void testProtecting2() {
		
		for ( int i = 0 ; i < 33 ; i++ ) 
		{
			if ( bitfield.isSet( i ) ) {
				fail("Already protected ?");
			}
			bitfield.setBit( i );
			if ( ! bitfield.isSet( i ) ) {
				fail("Not set?");
			}
		}
	}	
	
}
