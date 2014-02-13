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

import java.util.*;

import junit.framework.TestCase;
import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.emulator.memory.*;
import de.codesourcery.jasm16.utils.Misc;

public class MainMemoryTest extends TestCase implements IMemoryTypes {

	private MainMemory memory;
	
	@Override
	protected void setUp() throws Exception
	{
	    super.setUp();
	    memory = new MainMemory(65536);
	}
	
	public static void main(String[] args) throws Exception {
		
		MainMemoryTest test = new MainMemoryTest();
		test.setUp();
		test.testRemappingSpeed();
	}
	
	protected static final class Timing 
	{
		private long min=Integer.MAX_VALUE;
		private long max=Integer.MIN_VALUE;
		private long sum = 0;
		private long updateCount = 0;
		
		public void update(long delta) {
			min = Math.min(delta, min );
			max = Math.max(delta, max );
			sum += delta;
			updateCount++;
		}
		
		public void update(Timing other) {
			min = Math.min( other.min  , min );
			max = Math.max( other.max , max );
			sum += other.sum;
			updateCount += other.updateCount;
		}		
		
		public long min() { return min; }
		public long max() { return max; }
		public long avg() 
		{
			if ( updateCount == 0 ) {
				return 0;
			}
			return (long) ( sum / (float) updateCount ); 
		}
		
		@Override
		public String toString() {
			 return "Time: "+min()+" / "+avg()+" / "+max()+" ms";
		}
	}
	
	public void testRandomReadSpeed() 
	{
		final Random rnd = new Random(0xdeadbeef);
		Timing total = new Timing();
		for ( int i = 0 ; i < 5 ; i++ ) {
			total.update( internalTestRandomReadSpeed(rnd) );
		}
		// Time: 342 / 346 / 357 ms
		// Time: 333 / 337 / 347 ms
		// Time: 246 / 248 / 256 ms
		System.out.println("===> RESULT: "+total);
	}
	
	private Timing internalTestRandomReadSpeed(Random rnd) {
		
		System.out.println("--- random read ---");
		final int regionCount = 6;
		final int wordsPerRegion = 65536 / regionCount;
		
		int wordsLeft = 65536;
		int currentWord = 0;
		
		final List<MemoryRegion> toMap = new ArrayList<>();
		for ( int i = 0 ; i < regionCount ; i++ ) 
		{
			int sizeInWords;
			if ( i == ( regionCount-1) ) {
				sizeInWords = wordsLeft;
			} else {
				sizeInWords = wordsPerRegion;
			}
			final AddressRange range = new AddressRange( Address.wordAddress( currentWord ) , Size.words( sizeInWords ) );
			final MemoryRegion region = new MemoryRegion( "region #"+i, TYPE_RAM, range );
			toMap.add(region);
			
			wordsLeft -= sizeInWords;
			currentWord += sizeInWords;
		}
		
		Collections.shuffle( toMap , rnd );
		for ( MemoryRegion region : toMap ) {
			memory.mapRegion( region );
		}
		
		final int loopCount = 25;
		final Random rnd2 = new Random(0xdeadbeef);
		final Timing timing = new Timing();
		for ( int i = 0 ; i < loopCount ; i++) 
		{
			int dummy=0;
			rnd.setSeed( 0xdeadbeef );
			long delta = -System.currentTimeMillis();
			for ( int j = 0 ; j < 10000000 ; j++ ) 
			{
				final int adr = rnd2.nextInt( 65536 );
				dummy += memory.read( adr );
			}
			delta += System.currentTimeMillis();
			timing.update( delta );
			System.out.println( timing+" (dummy: "+dummy+")" );
		}
		return timing;
	}
	
	public void testRemappingSpeed() {
		
		AddressRange range1 = new AddressRange( Address.wordAddress( 0x8000 ) , 
				Size.words( 384 ) );
		MemoryRegion region1 = new MemoryRegion("region #1" , TYPE_RAM , range1 );		
		
		AddressRange range2 = new AddressRange( Address.wordAddress( 0x8180 ) , 
				Size.words( 384 ) );
		MemoryRegion region2 = new MemoryRegion("region #2" , TYPE_RAM , range2 );			
		
		MemoryRegion current = null;
		long delta = -System.currentTimeMillis();
		long timeUnmap = 0;
		long timeMap= 0;
		for ( int i = 0 ; i < 500 ; i++ ) {
			if ( current == null ) {
				current = region1;
			} else {
				long time1 = -System.currentTimeMillis();
				memory.unmapRegion( current );
				time1 += System.currentTimeMillis();
				timeUnmap += time1;
				if ( current == region1 ) {
					current = region2;
				} else {
					current = region1;
				}
			}
			long time2 = -System.currentTimeMillis();
			memory.mapRegion( current );
			time2 += System.currentTimeMillis();
			timeMap += time2;
		}
		delta += System.currentTimeMillis();
		System.out.println("Time "+delta+" millis.");
		System.out.println("MAP: Time "+timeMap+" millis.");
		System.out.println("UNMAP: Time "+timeUnmap+" millis.");
	}
	
	public void testMemoryRemapping() {
		
		/*
DEBUG: Memory layout
DEBUG: main memory - 0x0000 - 0x8000 ( 32768 words / 65536 bytes )
DEBUG: main memory - 0x8000 - 0x8180 ( 384 words / 768 bytes )
DEBUG: main memory - 0x8180 - 0x9000 ( 3712 words / 7424 bytes )
DEBUG: keyboard buffer (legacy) - 0x9000 - 0x9001 ( 1 words / 2 bytes )
DEBUG: main memory - 0x9001 - 0x010000 ( 28671 words / 57342 bytes )		 
		 */
		
		AddressRange range1 = new AddressRange( Address.wordAddress( 0 ) ,  Address.wordAddress( 0x8000 ) );
		MemoryRegion region1 = new MemoryRegion("region #1" , TYPE_RAM , range1 );
		memory.mapRegion( region1 );
		
		AddressRange range2 = new AddressRange( Address.wordAddress( 0x8000 ) , Address.wordAddress( 0x8180 ) );
		MemoryRegion region2 = new MemoryRegion("region #2" , TYPE_RAM , range2 );
		memory.mapRegion( region2 );
		
		AddressRange range3 = new AddressRange( Address.wordAddress( 0x8180) , Address.wordAddress( 0x9000 ) );
		MemoryRegion region3 = new MemoryRegion("region #3" , TYPE_RAM , range3 );
		memory.mapRegion( region3 );
		
		AddressRange range4 = new AddressRange( Address.wordAddress( 0x9000 ) , Address.wordAddress( 0x9001 ) );
		MemoryRegion region4 = new MemoryRegion("region #4" , TYPE_RAM , range4 );
		memory.mapRegion( region4 );
		
		AddressRange range5 = new AddressRange( Address.wordAddress( 0x9001 ) , Address.wordAddress( 0x10000 ) );
		MemoryRegion region5 = new MemoryRegion("region #5" , TYPE_RAM , range5 );
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
		
		AddressRange range6 = new AddressRange( Address.wordAddress( 0x4000 ) , Address.wordAddress( 0x4010 ) );
		MemoryRegion region6 = new MemoryRegion("region #6" , TYPE_RAM , range6 );
		memory.mapRegion( region6 );
		
		assertMemoryContains( region6 , 0x1234 );
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
        assertMemoryContains( region, Address.wordAddress( 0 ) , region.getSize() , value );
    }
	
	private void assertMemoryContains(IMemory region , Address start,Size size, int value) {
		
	    Address current = start;
		int words = size.getSizeInWords();
		for ( int i = 0 ; i < words ; i++ ) {
			int actualValue = region.read( current );
			if ( actualValue != value ) {
				fail("Memory region "+region+" did not contain expected value "+Misc.toHexString( value )+" (got: "+Misc.toHexString(actualValue)+") at address "+Misc.toHexString( current ) );
			}
			current = current.incrementByOne( false );
		}
	}
	
	public void testRemappingOverNonMergableRegion() {
		
        AddressRange range1 = new AddressRange( Address.wordAddress( 0 ) , Address.wordAddress( 0x8000 ) );
        AddressRange range2 = new AddressRange( Address.wordAddress( 0x8000 ) , Address.wordAddress( 0x8180 ) );
        AddressRange range3 = new AddressRange( Address.wordAddress( 0x8180) , Address.wordAddress( 0x9000 ) );
        
        MemoryRegion region1 = new MemoryRegion("region #1" , TYPE_RAM , range1 );
        MemoryRegion region2 = new MemoryRegion("region #2" , TYPE_RAM , range2 );
        MemoryRegion region3 = new MemoryRegion("region #3" , TYPE_RAM , range3 );        
        
        memory.mapRegion( region1 );
        memory.mapRegion( region2 );
        memory.mapRegion( region3 );
	}
	
    public void testMemoryMerging() {
        
        AddressRange range1 = new AddressRange( Address.wordAddress( 0 ) , 
                Address.wordAddress( 0x8000 ) );
        MemoryRegion region1 = new MemoryRegion("region #1" , TYPE_RAM , range1 );
        memory.mapRegion( region1 );
        
        AddressRange range2 = new AddressRange( Address.wordAddress( 0x8000 ) , Address.wordAddress( 0x8180 ) );
        MemoryRegion region2 = new MemoryRegion("region #2" , TYPE_RAM , range2 );
        memory.mapRegion( region2 );
        
        AddressRange range3 = new AddressRange( Address.wordAddress( 0x8180) , Address.wordAddress( 0x9000 ) );
        MemoryRegion region3 = new MemoryRegion("region #3" , TYPE_RAM , range3 );
        memory.mapRegion( region3 );
        
        AddressRange range4 = new AddressRange( Address.wordAddress( 0x9000 ) , Address.wordAddress( 0x9001 ) );
        MemoryRegion region4 = new MemoryRegion("region #4" , TYPE_RAM , range4 );
        memory.mapRegion( region4 );
        
        AddressRange range5 = new AddressRange( Address.wordAddress( 0x9001 ) , Address.wordAddress( 0x10000 ) );
        MemoryRegion region5 = new MemoryRegion("region #5" , TYPE_RAM , range5 );
        memory.mapRegion( region5 );        
        
        fillMemoryRegion( memory, region1 , 0x1234 );
        fillMemoryRegion( memory, region2 , 0x5678 );
        fillMemoryRegion( memory, region3 , 0x90ab );
        fillMemoryRegion( memory, region4 , 0xcdef );
        fillMemoryRegion( memory, region5 , 0xbeef );

        assertMemoryContains( memory , region1.getAddressRange().getStartAddress() , region1.getSize() , 0x1234 );
        assertMemoryContains( memory , region2.getAddressRange().getStartAddress() , region2.getSize()  , 0x5678 );
        assertMemoryContains( memory , region3.getAddressRange().getStartAddress() , region3.getSize()  , 0x90ab );
        assertMemoryContains( memory , region4.getAddressRange().getStartAddress() , region4.getSize()  , 0xcdef );
        assertMemoryContains( memory , region5.getAddressRange().getStartAddress() , region5.getSize()  , 0xbeef );
        
        memory.unmapRegion( region3 );
        memory.unmapRegion( region5 );
        memory.unmapRegion( region2 );
        memory.unmapRegion( region1 );
        memory.unmapRegion( region4 );
        
        System.out.println("Memory layout");
        memory.dumpMemoryLayout( new PrintStreamLogger( System.out ) );
        
        assertMemoryContains( memory , region1.getAddressRange().getStartAddress() , region1.getSize() , 0x1234 );
        assertMemoryContains( memory , region2.getAddressRange().getStartAddress() , region2.getSize()  , 0x5678 );
        assertMemoryContains( memory , region3.getAddressRange().getStartAddress() , region3.getSize()  , 0x90ab );
        assertMemoryContains( memory , region4.getAddressRange().getStartAddress() , region4.getSize()  , 0xcdef );
        assertMemoryContains( memory , region5.getAddressRange().getStartAddress() , region5.getSize()  , 0xbeef );        
    }	
}
