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
import java.util.Iterator;
import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.emulator.Emulator.MemoryProtectionFaultException;
import de.codesourcery.jasm16.emulator.ILogger;
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
public class MainMemory implements IMemory
{
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
        regions.add( createMainMemory( new AddressRange( WordAddress.ZERO , Size.words( 65536 ) ) ) );
        this.writeProtectedMemoryRanges = new Bitfield( sizeInWords );
        this.checkWriteAccess = checkWriteAccess;
    }    
    
    public void setCheckWriteAccess(boolean onOff) {
        this.checkWriteAccess = onOff;
    }
    
    private static IMemoryRegion createMainMemory(AddressRange range) {
        return new MemoryRegion( "main memory" , range );
    }
    
    public void dumpMemoryLayout(ILogger logger) 
    {
    	logger.debug("Memory layout");
    	for ( IMemoryRegion region : regions ) {
    		logger.debug( region.toString() );
    	}
    }
    
    @Override
    public void clear()
    {
        for ( IMemory r : regions ) {
            r.clear();
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
        
        for (Iterator<IMemoryRegion> it = regions.iterator(); it.hasNext();) {
            final IMemoryRegion existing = it.next();
            if ( existing == region ) 
            {
                it.remove();
                
                final IMemoryRegion mainMemory = createMainMemory( existing.getAddressRange() );
                // preserve memory contents
                MemUtils.memCopy( existing , mainMemory , WordAddress.ZERO , existing.getSize() );
                mapRegion( mainMemory );
                return;
            }
        }
        throw new IllegalArgumentException("Cannot unmap unknown region "+region);
    }
    
    /**
     * Maps main memory to a specific region.
     * 
     * @param newRegion
     * @see #unmapRegion(IMemoryRegion)
     */
    public void mapRegion(IMemoryRegion newRegion) 
    {
        if (newRegion == null) {
            throw new IllegalArgumentException("region must not be NULL.");
        }
        boolean intersects = false;
        do 
        {
            intersects = false;
            int index = 0;
            for ( IMemoryRegion existing : regions ) 
            {
                if ( existing.getAddressRange().intersectsWith( newRegion.getAddressRange() ) ) 
                {
                    regions.remove( index ); // remove existing region
                    
                    if ( existing.getAddressRange().equals( newRegion.getAddressRange() ) ) {
                        // exactly the same address range, just copy memory contents from existing => new region
                        MemUtils.memCopy( existing , newRegion , Address.wordAddress( 0 ) , existing.getSize() );
                    } else {
                        regions.addAll( index , existing.subtract( newRegion.getAddressRange() ) );
                    }
                    intersects = true;
                    break;
                }
                index++;
            }
        } while ( intersects );
        
        // no intersection, just insert into the list
        int index = 0;
        for ( IMemoryRegion existing : regions ) 
        {
            if ( newRegion.getAddressRange().getStartAddress().isLessThan( existing.getAddressRange().getStartAddress() ) ) {
                regions.add( index , newRegion );
                return;
            }
            index++;
        }
        regions.add( newRegion );
    }

    @Override
    public int read(int wordAddress)
    {
        final WordAddress address = Address.wordAddress( wordAddress );
        final IMemoryRegion region = getRegion( address );
        return region.read( address.minus( region.getAddressRange().getStartAddress() ) );
    }
    
    private IMemoryRegion getRegion(Address address) 
    {
        for ( IMemoryRegion r : regions ) 
        {
            if ( r.getAddressRange().contains( address ) ) {
                return r;
            }
        }
        
        // address not mapped...
        
        System.err.println( "ERROR: Access to unmapped address "+address);
        System.err.println( "\nMemory layout:\n\n");
        for ( IMemoryRegion r : regions ) 
        {
            System.err.println( r );
        }
        throw new RuntimeException("Address not mapped: "+address);
    }

    @Override
    public int read(Address adr)
    {
        final WordAddress address = adr.toWordAddress();
        final IMemoryRegion region = getRegion( address );
        return region.read( address.minus( region.getAddressRange().getStartAddress() ) );
    }

    @Override
    public void write(int wordAddress, int value) throws MemoryProtectionFaultException
    {
        final WordAddress address = Address.wordAddress( wordAddress );
        
        if ( checkWriteAccess ) {
            checkWritePermitted(address,value);
        }
        
        final IMemoryRegion region = getRegion( address );
        region.write( address.minus( region.getAddressRange().getStartAddress() ) , value );           
    }

    private void checkWritePermitted(WordAddress address, int value ) throws MemoryProtectionFaultException
    {
    	if ( writeProtectedMemoryRanges.isSet( address.getWordAddressValue() ) ) 
    	{
            throw new MemoryProtectionFaultException("Trying to write value "+
    	Misc.toHexString( value )+" to address 0x"+Misc.toHexString( address )
                    +" that is part of write-protected range.",address);
        }
    }

    @Override
    public void write(Address adr, int value) throws MemoryProtectionFaultException
    {
        final WordAddress address = adr.toWordAddress();
        
        if ( checkWriteAccess ) {
            checkWritePermitted(address,value);
        }
        
        final IMemoryRegion region = getRegion( address );
        region.write( address.minus( region.getAddressRange().getStartAddress() ) , value );        
    }

    @Override
    public Size getSize()
    {
        Size result = Size.bytes( 0 );
        for ( IMemory r : regions ) {
            result = result.plus( r.getSize() );
        }        
        return result;
    }

}