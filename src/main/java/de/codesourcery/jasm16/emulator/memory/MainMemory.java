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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.emulator.ILogger;
import de.codesourcery.jasm16.emulator.exceptions.MemoryProtectionFaultException;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion.Flag;
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
	
	private final IMemoryRegion NOP_RANGE = new IMemoryRegion() {

		@Override
		public void clear() {
			throw new UnsupportedOperationException("Not implemented");
		}

		@Override
		public void write(int wordAddress, int value) {
			throw new UnsupportedOperationException("Not implemented");
		}

		@Override
		public void write(Address address, int value) {
			throw new UnsupportedOperationException("Not implemented");
		}

		@Override
		public Size getSize() {
			throw new UnsupportedOperationException("Not implemented");			
		}

		@Override
		public int read(int wordAddress) {
			throw new UnsupportedOperationException("Not implemented");			
		}

		@Override
		public int read(Address address) {
			throw new UnsupportedOperationException("Not implemented");
		}

		@Override
		public Set<Flag> getFlags() {
			return Collections.emptySet();
		}

		@Override
		public boolean hasFlag(Flag flag) {
			return false;
		}

		@Override
		public long getTypeId() {
			throw new UnsupportedOperationException("Not implemented");			
		}

		@Override
		public AddressRange getAddressRange() {
			throw new UnsupportedOperationException("Not implemented");			
		}

		@Override
		public String getRegionName() {
			throw new UnsupportedOperationException("Not implemented");			
		}

		@Override
		public boolean supportsMerging() {
			throw new UnsupportedOperationException("Not implemented");			
		}

		@Override
		public List<IMemoryRegion> split(AddressRange gap) throws IllegalArgumentException 
		{
			throw new UnsupportedOperationException("Not implemented");			
		}

		@Override
		public boolean contains(int wordAddress) {
			return false;
		}		
	};
	
	private IMemoryRegion lastReadLookup = NOP_RANGE;
	private IMemoryRegion lastWriteLookup = NOP_RANGE;	
	
	// GuardedBy( regions )
	private final List<IMemoryRegion> regions = new ArrayList<IMemoryRegion>(); 
	private volatile boolean checkWriteAccess;

	// list of AddressRange instances that will trigger an exception 
	// when being written to
	private final Bitfield writeProtectedMemoryRanges;

	public MainMemory(int sizeInWords) 
	{
		this(sizeInWords , false );
	}

	public MainMemory(int sizeInWords,boolean checkWriteAccess) 
	{
		final IMemoryRegion mainMemory = createMainMemory( new AddressRange( WordAddress.ZERO , Size.words( 65536 ) ) );
		regions.add( mainMemory );
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
		synchronized( regions ) {
			for ( IMemoryRegion region : regions ) {
				logger.debug( region.toString() );
			}
		}
	}

	@Override
	public void clear()
	{
		synchronized(regions) {
			lastReadLookup = lastWriteLookup = NOP_RANGE;
			for ( IMemory r : regions ) {
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

		synchronized(regions) 
		{
			lastReadLookup = lastWriteLookup = NOP_RANGE;			
			
		    boolean found = false;
			for (Iterator<IMemoryRegion> it = regions.iterator(); it.hasNext();) {
				final IMemoryRegion existing = it.next();
				if ( existing == region ) 
				{
					mapRegion( createMainMemory( existing.getAddressRange() ) , true );
					found = true;
					return; // *RETURNS* from method , do not try merging adjacent memory regions because of performance hit on double-buffering
				}
			}
			
			if ( ! found ) {
		        throw new IllegalArgumentException("Cannot unmap unknown region "+region);			    
			}
			
			// TODO: this code is currently never executed because
			// TODO: it incurs a HEAVY performance hit on 
			// TODO: emulation performance for applications that
			// TODO: do double-buffering (=remapping VRAM constantly)
			
            // merge adjactant memory regions that support it
            for ( int index = 1 ; index < regions.size() ; index++ )
            {
                final IMemoryRegion previous = regions.get(index-1);
                final IMemoryRegion current = regions.get(index);
                
                if ( supportMerging( previous,current ) && 
                     previous.getAddressRange().getEndAddress().equals( current.getAddressRange().getStartAddress() ) ) 
                {
                    final AddressRange mergedRange = new AddressRange( previous.getAddressRange().getStartAddress() , current.getAddressRange().getEndAddress() );
                    final IMemoryRegion combined = createMainMemory( mergedRange );
                    MemUtils.memCopy( this , combined , mergedRange.getStartAddress() , mergedRange.getSize() );
                    regions.remove( index );
                    regions.set( index -1 , combined );
                    index--;
                }
            }   	
		}
	}
	
	private boolean supportMerging(IMemoryRegion r1,IMemoryRegion r2) 
	{
		if ( ! r1.supportsMerging() || ! r2.supportsMerging() ) {
			return false;
		}
		if ( r1.hasFlag(Flag.MEMORY_MAPPED_HW) || r2.hasFlag( Flag.MEMORY_MAPPED_HW ) ) {
			return false;
		}
		return true;
	}

	/**
	 * Returns all memory regions that are mapped
	 * to a specific address range.
	 * 
	 * @param range
	 * @return
	 */
	public List<IMemoryRegion> getRegions(AddressRange range) {
		
		List<IMemoryRegion> result = new ArrayList<>();
		synchronized( regions ) 
		{
			for ( IMemoryRegion existing : regions ) 
			{
				if ( existing.getAddressRange().intersectsWith( range ) ) {
					result.add( existing );
				}
			}
		}
		return result;
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
	
	private void mapRegion(IMemoryRegion newRegion,boolean calledByUnmap) 
	{
		if (newRegion == null) {
			throw new IllegalArgumentException("region must not be NULL.");
		}

		// refuse mapping if address holds regions of different types
		if ( ! calledByUnmap ) 
		{
			final long newTypeId = newRegion.getTypeId();
			for ( IMemoryRegion existing : getRegions( newRegion.getAddressRange() ) ) 
			{
				if ( existing.getTypeId() != TYPE_RAM && existing.getTypeId() != newTypeId ) 
				{
					LOG.error("Cannot map region "+newRegion+" , address range already holds region "+existing);
					throw new IllegalStateException("Cannot map region "+newRegion+" , address range already holds region "+existing);
				}
			}
		}
		
		synchronized( regions ) 
		{
			lastReadLookup = lastWriteLookup = NOP_RANGE;
			
			// copy existing memory contents into new region
			MemUtils.memCopy( this , newRegion , newRegion.getAddressRange().getStartAddress() , newRegion.getSize() );
			
			boolean intersects = false;
			do 
			{
				intersects = false;
				int index = 0;
				
				final int len = regions.size();
				for ( int i = 0 ; i < len ; i++ ) 
				{
					final IMemoryRegion existing = regions.get(i);
					if ( existing.getAddressRange().intersectsWith( newRegion.getAddressRange() ) ) 
					{
						regions.remove( index ); // remove existing region

						if ( existing.getAddressRange().equals( newRegion.getAddressRange() ) ) {
							// simple case, just replacing an existing region
							regions.add( index , newRegion );
							return;
						}

						regions.addAll( index , existing.split( newRegion.getAddressRange() ) );
						intersects = true;
						break;
					}
					index++;
				}
			} while ( intersects );

			// no intersection, just insert into the list
			final int len = regions.size();
			for ( int index = 0 ; index < len ; index++) 
			{
				IMemoryRegion existing = regions.get(index);
				if ( newRegion.getAddressRange().getStartAddress().isLessThan( existing.getAddressRange().getStartAddress() ) ) {
					regions.add( index , newRegion );
					return;
				}
				index++;
			}
			regions.add( newRegion );
		}
	}

	private IMemoryRegion getReadRegion(int wordAddress) 
	{
		synchronized( regions ) 
		{
			// TODO: performance - Maybe replace with binary search ??
			if ( lastReadLookup.contains( wordAddress ) ) {
				return lastReadLookup;
			}
			final int len = regions.size();
			for ( int i = 0 ; i < len ; i++ )
			{
				final IMemoryRegion r = regions.get(i);
				if ( r.contains( wordAddress ) ) {
					lastReadLookup = r;
					return r;
				}
			}
		}

		// address not mapped...
		LOG.error("getRegion(): Access to unmapped address 0x"+Misc.toHexString( wordAddress ) );
		LOG.error("getRegion(): Memory layout:\n\n");
		synchronized( regions ) 
		{
			for ( IMemoryRegion r : regions ) 
			{
				LOG.error("getRegion(): " + r );
			}
		}
		throw new RuntimeException("Address not mapped: 0x"+Misc.toHexString( wordAddress ));		
	}
	
	private IMemoryRegion getWriteRegion(int wordAddress) 
	{
		synchronized( regions ) 
		{
			// TODO: performance - Maybe replace with binary search ??
			if ( lastWriteLookup.contains( wordAddress ) ) {
				return lastWriteLookup;
			}
			final int len = regions.size();
			for ( int i = 0 ; i < len ; i++ )
			{
				final IMemoryRegion r = regions.get(i);
				if ( r.contains( wordAddress ) ) {
					lastWriteLookup = r;
					return r;
				}
			}
		}

		// address not mapped...
		LOG.error("getRegion(): Access to unmapped address 0x"+Misc.toHexString( wordAddress ) );
		LOG.error("getRegion(): Memory layout:\n\n");
		synchronized( regions ) 
		{
			for ( IMemoryRegion r : regions ) 
			{
				LOG.error("getRegion(): " + r );
			}
		}
		throw new RuntimeException("Address not mapped: 0x"+Misc.toHexString( wordAddress ));		
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
        int newValue = address - region.getAddressRange().getStartAddress().getWordAddressValue();
        if ( newValue < 0 ) {
            newValue = (int) ( (WordAddress.MAX_ADDRESS+1)+newValue );
        }		
		return region.read( newValue );
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

        int newValue = wordAddress - region.getAddressRange().getStartAddress().getWordAddressValue();
        if ( newValue < 0 ) {
            newValue = (int) ( (WordAddress.MAX_ADDRESS+1)+newValue );
        }			
		region.write( newValue , value );
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
			for ( IMemory r : regions ) {
				result = result.plus( r.getSize() );
			}        
		}
		return result;
	}

}