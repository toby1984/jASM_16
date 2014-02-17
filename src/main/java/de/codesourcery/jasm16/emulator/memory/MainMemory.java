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
package de.codesourcery.jasm16.emulator.memory;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.*;
import de.codesourcery.jasm16.emulator.ILogger;
import de.codesourcery.jasm16.emulator.exceptions.MemoryProtectionFaultException;
import de.codesourcery.jasm16.utils.Bitfield;
import de.codesourcery.jasm16.utils.Misc;

/**
 * DCPU-16 main memory emulation.
 * 
 * <p>Note that the DCPU-16 only supports word-sized addressing. This memory implementation supports
 * overlay memory (see {@link IMemoryRegion} to enable features like video RAM etc.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class MainMemory implements IMemory, IMemoryTypes
{
	private static final Logger LOG = Logger.getLogger(MainMemory.class);

	private static final int ADR_CACHE_BIT_COUNT = 4;
	private static final int ADR_CACHE_MASK = ( (1<<ADR_CACHE_BIT_COUNT)-1 ) << (16-ADR_CACHE_BIT_COUNT);

	// GuardedBy( regions )
	private final FastList regions;

	protected final class FastList 
	{
		public final List<IMemoryRegion> regionList = new ArrayList<>();

		@SuppressWarnings("unchecked")
		public final ArrayList<IMemoryRegion>[] regionCache = new ArrayList[1<<ADR_CACHE_BIT_COUNT];	

		public FastList(IMemoryRegion mainMemory) 
		{
			regionList.add( mainMemory );
			for ( int i = 0 ; i < regionCache.length ; i++) 
			{
				final ArrayList<IMemoryRegion> list = new ArrayList<IMemoryRegion>();
				list.add( mainMemory );
				regionCache[i] = list;
			}
		}    

		public IMemoryRegion getRegion(final int wordAddress) 
		{
			final int slotIdx = wordAddressToSlotIndex( wordAddress );
			final List<IMemoryRegion> list = regionCache[slotIdx];
			final int len = list.size();
			for ( int i = 0 ; i < len ; i++ ) 
			{
				final IMemoryRegion current = list.get(i);
				if ( current.contains( wordAddress ) ) {
					return current;
				}
			}	
			
			// address not mapped...
			LOG.error("getRegion(): Access to unmapped address 0x"+Misc.toHexString( wordAddress ) );
			LOG.error("getRegion(): Memory layout:\n\n");
			for ( IMemoryRegion r : regionList ) 
			{
				LOG.error("getRegion(): " + r );
			}
			
			final int slotCount = 1 << ADR_CACHE_BIT_COUNT;
			final Size sizePerSlot = Size.words( 65536 / slotCount );
			for ( int i =0 ; i < regionCache.length ; i++ ) 
			{
				final Address start = Address.wordAddress( i*sizePerSlot.getSizeInWords() );
				final AddressRange range = new AddressRange(start,sizePerSlot);
				LOG.error("getRegion(): Slot #"+i+" ("+range+") : "+regionCache[i]);
			}
			throw new RuntimeException("Internal error, address not mapped: 0x"+Misc.toHexString( wordAddress ));				
		}

		public void replace(IMemoryRegion oldRegion,IMemoryRegion newRegion,int idx) 
		{
			regionList.set(idx , newRegion);
			
			int startIndex = wordAddressToSlotIndex( oldRegion.getAddressRange().getStartAddress().getWordAddressValue() );
			int endIndex = wordAddressToSlotIndex( oldRegion.getAddressRange().getEndAddress().getWordAddressValue()-1 );
			for ( int i = startIndex ; i <= endIndex ; i++ ) 
			{
				regionCache[i].remove(oldRegion);
			}			

			startIndex = wordAddressToSlotIndex( newRegion.getAddressRange().getStartAddress().getWordAddressValue() );
			endIndex = wordAddressToSlotIndex( newRegion.getAddressRange().getEndAddress().getWordAddressValue()-1 );
			for ( int i = startIndex ; i <= endIndex ; i++ ) 
			{
				regionCache[i].add( newRegion );
			}			
		}	

		public void replace(IMemoryRegion oldRegion,List<IMemoryRegion> newRegions,int idx) 
		{
			regionList.remove(idx);
			
			int startIndex = wordAddressToSlotIndex( oldRegion.getAddressRange().getStartAddress().getWordAddressValue() );
			int endIndex = wordAddressToSlotIndex( oldRegion.getAddressRange().getEndAddress().getWordAddressValue()-1 );
			for ( int i = startIndex ; i <= endIndex ; i++ ) 
			{
				regionCache[i].remove(oldRegion);
			}	
			
			final int len2 = newRegions.size();
			for (int i = 0; i < len2; i++) 
			{
				final IMemoryRegion newRegion = newRegions.get(i);
				regionList.add( newRegion );
				
				startIndex = wordAddressToSlotIndex( newRegion.getAddressRange().getStartAddress().getWordAddressValue() );
				endIndex = wordAddressToSlotIndex( newRegion.getAddressRange().getEndAddress().getWordAddressValue()-1 );
				for ( int j = startIndex ; j <= endIndex ; j++ ) {
					regionCache[j].add( newRegion );
				}		
			}
		}			

		public void add(IMemoryRegion r) 
		{
			regionList.add( r );
			
			final int startIndex = wordAddressToSlotIndex( r.getAddressRange().getStartAddress().getWordAddressValue() );
			final int endIndex = wordAddressToSlotIndex( r.getAddressRange().getEndAddress().getWordAddressValue()-1 );
			for ( int j = startIndex ; j <= endIndex ; j++ ) {
				regionCache[j].add( r );
			}				
		}			
	}

	protected static final int createAdressCacheMask() 
	{
		int mask=0;
		for ( int i = 0 ; i < ADR_CACHE_BIT_COUNT ; i++ ) 
		{
			mask = mask << 1;
			mask |= 1;
		}
		return mask << ( 15 - ADR_CACHE_BIT_COUNT );
	}

	protected static final int wordAddressToSlotIndex(int wordAddress) {
		return (wordAddress & ADR_CACHE_MASK) >> (16-ADR_CACHE_BIT_COUNT);
	}

	protected volatile boolean checkWriteAccess;

	// list of AddressRange instances that will trigger an exception 
	// when being written to
	protected final Bitfield writeProtectedMemoryRanges;

	public MainMemory(int sizeInWords) 
	{
		this(sizeInWords , false );
	}

	public MainMemory(int sizeInWords,boolean checkWriteAccess) 
	{
		final IMemoryRegion mainMemory = createMainMemory( new AddressRange( WordAddress.ZERO , Size.words( 65536 ) ) );
		this.regions = new FastList(mainMemory);
		this.writeProtectedMemoryRanges = new Bitfield( sizeInWords );
		this.checkWriteAccess = checkWriteAccess;
	}    

	public void setCheckWriteAccess(boolean onOff) {
		this.checkWriteAccess = onOff;
	}

	private static IMemoryRegion createMainMemory(AddressRange range) {
		return new MemoryRegion( "main memory" , TYPE_RAM , range , MemoryRegion.Flag.SUPPORTS_MERGING  );
	}

	public void dumpMemoryLayout(ILogger logger) 
	{
		logger.debug("Memory layout");
		synchronized( regions ) 
		{
			for ( IMemoryRegion region : regions.regionList ) {
				logger.debug( region.toString() );
			}
		}
	}

	@Override
	public void clear()
	{
		synchronized(regions) {
			for ( IMemory r : regions.regionList ) {
				r.clear();
			}
		}
	}

	/**
	 * Marks an address range as being write-protected.
	 * 
	 * <p>Subsequent writes to this address range will trigger a {@link MemoryProtectionFaultException}.</p>
	 * 
	 * @param range address range to check
	 * @throws IllegalStateException if this instance was not created with the <code>checkWrites</code> flag set to <code>true</code>
	 * @see #resetWriteProtection()
	 */
	public void writeProtect(AddressRange range) {
		if ( ! checkWriteAccess ) {
			throw new IllegalStateException("Trying to mark an address range as write-protected " +
					"while checkWrites == false ");
		}
		if (range == null) {
			throw new IllegalArgumentException("range must not be NULL.");
		}

		int start = range.getStartAddress().getWordAddressValue();
		final int len = range.getSize().getSizeInWords();
		for ( int i = 0 ; i < len ;i++ ) {
			writeProtectedMemoryRanges.setBit( start );
			start++;
		}
	}

	/**
	 * Discards all information about write-protected address ranges.
	 * 
	 * @see #writeProtect(AddressRange)
	 * @see #setCheckWriteAccess(boolean)
	 */
	public void resetWriteProtection() {
		writeProtectedMemoryRanges.clear();
	}

	/**
	 * Replaces a mapped memory region with plain (unmapped) main-memory.
	 * 
	 * @param region
	 */
	public void unmapRegion(IMemoryRegion region) {

		if (region == null) {
			throw new IllegalArgumentException("region must not be NULL.");
		}

		final int slotIdx = wordAddressToSlotIndex( region.getAddressRange().getStartAddress().getWordAddressValue() );		
		synchronized(regions) 
		{
			final List<IMemoryRegion> list = regions.regionCache[slotIdx];
			for ( int i = 0 ; i < list.size() ; i++ ) {
				if ( list.get(i) == region ) 
				{
					mapRegion( createMainMemory( region.getAddressRange() ) , true );		    		
					return;
				}
			}
			throw new IllegalArgumentException("Cannot unmap unknown region "+region);			    
		}
	}

	/**
	 * Maps main memory to a specific region.
	 * 
	 * @param newRegion
	 * @see #unmapRegion(IMemoryRegion)
	 */
	public void mapRegion(IMemoryRegion newRegion) 
	{
		mapRegion(newRegion,false);
	}

	private void mapRegion(final IMemoryRegion newRegion,final boolean calledByUnmap) 
	{
		if (newRegion == null) {
			throw new IllegalArgumentException("region must not be NULL.");
		}

		synchronized( regions ) 
		{
			// copy existing memory contents into new region
			MemUtils.memCopy( this , newRegion , newRegion.getAddressRange().getStartAddress() , newRegion.getSize() );
			
			final List<IMemoryRegion> regions = MainMemory.this.regions.regionList;

			final long newTypeId = newRegion.getTypeId();

			boolean added = false;
			int len = regions.size();
			for ( int i = 0 ; i < len ; i++ ) 
			{
				final IMemoryRegion existing = regions.get(i);
				if ( existing.getAddressRange().intersectsWith( newRegion.getAddressRange() ) ) 
				{
					if ( existing == newRegion ) {
						LOG.error("Region "+newRegion+" is already mapped.");
						throw new IllegalStateException("Region "+newRegion+" is already mapped.");
					}
					
					if ( ! calledByUnmap && existing.getTypeId() != TYPE_RAM && existing.getTypeId() != newTypeId ) 
					{
						LOG.error("Cannot map region "+newRegion+" , address range already holds region "+existing);
						throw new IllegalStateException("Cannot map region "+newRegion+" , address range already holds region "+existing);							
					}
					
					if ( existing.getAddressRange().equals( newRegion.getAddressRange() ) ) {
						// simple case, just replacing an existing region
						MainMemory.this.regions.replace( existing , newRegion , i );
						return;
					}
					
					final List<IMemoryRegion> split = existing.split( newRegion.getAddressRange() );
					MainMemory.this.regions.replace( existing , split , i );
					len = regions.size();
					added = true;
					i = i+split.size()-1; // skip newly inserted regions
				}
			}
			// should never happen because every address needs to be mapped to SOME memory all the time			
			if ( ! added ) 
			{
				LOG.error("Internal error, failed to map region "+newRegion);
				throw new IllegalStateException("Internal error, failed to map region "+newRegion);
			}
			MainMemory.this.regions.add( newRegion );
		}
	}

	private IMemoryRegion getReadRegion(int wordAddress) 
	{
		synchronized( regions ) 
		{
			return regions.getRegion( wordAddress );
		}
	}

	private IMemoryRegion getWriteRegion(int wordAddress) 
	{
		synchronized( regions ) 
		{
			return regions.getRegion( wordAddress );
		}
	}	

	@Override
	public int read(Address adr)
	{
		return read( adr.getWordAddressValue() );
	}

	@SuppressWarnings("deprecation")
	@Override
	public int read(int address)
	{
		final IMemoryRegion region = getReadRegion( address );
		return region.read( (address - region.getAddressRange().getStartAddress().getWordAddressValue() ));
	}	

	private void checkWritePermitted(int wordAddress, int value ) throws MemoryProtectionFaultException
	{
		if ( writeProtectedMemoryRanges.isSet( wordAddress ) ) 
		{
			throw new MemoryProtectionFaultException("Intercepted write of value "+
					Misc.toHexString( value )+" to address 0x"+Misc.toHexString( wordAddress )
					+" that is part of a write-protected memory range.",Address.wordAddress( wordAddress ));
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void write(int wordAddress, int value) throws MemoryProtectionFaultException
	{
		final IMemoryRegion region = getWriteRegion( wordAddress );
		if ( checkWriteAccess ) {
			checkWritePermitted(wordAddress,value);
		}
		region.write( ( wordAddress - region.getAddressRange().getStartAddress().getWordAddressValue() ) , value );
	}

	@Override
	public void write(Address adr, int value) throws MemoryProtectionFaultException
	{
		write( adr.getWordAddressValue() , value );
	}

	@Override
	public Size getSize()
	{
		Size result = Size.bytes( 0 );
		synchronized( regions ) {
			for ( IMemory r : regions.regionList ) {
				result = result.plus( r.getSize() );
			}        
		}
		return result;
	}
}