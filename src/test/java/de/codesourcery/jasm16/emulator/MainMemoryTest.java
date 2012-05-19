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
import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.emulator.memory.IMemory;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion;
import de.codesourcery.jasm16.emulator.memory.MainMemory;
import de.codesourcery.jasm16.emulator.memory.MemoryRegion;
import de.codesourcery.jasm16.utils.Misc;

public class MainMemoryTest extends TestCase {

	private MainMemory memory = new MainMemory(65536);
	
	public void testMemoryRemapping() {
		
		/*
DEBUG: Memory layout
DEBUG: main memory - 0x0000 - 0x8000 ( 32768 words / 65536 bytes )
DEBUG: main memory - 0x8000 - 0x8180 ( 384 words / 768 bytes )
DEBUG: main memory - 0x8180 - 0x9000 ( 3712 words / 7424 bytes )
DEBUG: keyboard buffer (legacy) - 0x9000 - 0x9001 ( 1 words / 2 bytes )
DEBUG: main memory - 0x9001 - 0x010000 ( 28671 words / 57342 bytes )		 
		 */
		
		AddressRange range1 = new AddressRange( Address.wordAddress( 0 ) , Address.wordAddress( 0x8000 ) );
		MemoryRegion region1 = new MemoryRegion("region #1" , range1 );
		memory.mapRegion( region1 );
		
		AddressRange range2 = new AddressRange( Address.wordAddress( 0x8000 ) , Address.wordAddress( 0x8180 ) );
		MemoryRegion region2 = new MemoryRegion("region #2" , range2 );
		memory.mapRegion( region2 );
		
		AddressRange range3 = new AddressRange( Address.wordAddress( 0x8180) , Address.wordAddress( 0x9000 ) );
		MemoryRegion region3 = new MemoryRegion("region #3" , range3 );
		memory.mapRegion( region3 );
		
		AddressRange range4 = new AddressRange( Address.wordAddress( 0x9000 ) , Address.wordAddress( 0x9001 ) );
		MemoryRegion region4 = new MemoryRegion("region #4" , range4 );
		memory.mapRegion( region4 );
		
		AddressRange range5 = new AddressRange( Address.wordAddress( 0x9001 ) , Address.wordAddress( 0x10000 ) );
		MemoryRegion region5 = new MemoryRegion("region #6" , range5 );
		memory.mapRegion( region5 );		
		
		System.out.println("Memory layout");
		memory.dumpMemoryLayout( new PrintStreamLogger( System.out ) );
		
		fillMemoryRegion( memory, region1 , 0x1234 );
		fillMemoryRegion( memory, region2 , 0x5678 );
		fillMemoryRegion( memory, region3 , 0x90ab );
		fillMemoryRegion( memory, region4 , 0xcdef );
		fillMemoryRegion( memory, region5 , 0xbeef );

		assertMemoryContains( region1 , 0x1234 );
		assertMemoryContains( region2 , 0x5678 );
		assertMemoryContains( region3 , 0x90ab );
		assertMemoryContains( region4 , 0xcdef );
		assertMemoryContains( region5 , 0xbeef );
	}
	
	private void fillMemoryRegion(IMemory memory , IMemoryRegion region , int value) {
		
		Address start = region.getAddressRange().getStartAddress();
		int words = region.getSize().getSizeInWords();
		for ( int i = 0 ; i < words ; i++ ) {
			memory.write( start , value);
			start = start.incrementByOne( false );
		}
	}	
	
	private void assertMemoryContains(IMemoryRegion region , int value) {
		
		Address start = Address.wordAddress( 0 );
		int words = region.getSize().getSizeInWords();
		for ( int i = 0 ; i < words ; i++ ) {
			if ( region.read( start ) != value ) {
				fail("Memory region "+region+" did not contain "+Misc.toHexString( value )+" at address "+Misc.toHexString( start ) );
			}
			start = start.incrementByOne( false );
		}
	}
}
