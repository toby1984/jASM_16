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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.Size.SizeInWords;

/**
 * A memory region.
 * 
 * <p>Each region has a (non-unique) name for informational purposes along with
 * the address range covered by this memory region.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class MemoryRegion implements IMemoryRegion , IMemoryTypes {
    
    private final String regionName;
    private final AddressRange addressRange;
    private final AtomicIntegerArray memory;
    private final boolean supportsMerging; // distinct field and not just a flag for speed reasons
    private final Set<Flag> flags = new HashSet<>();
    private final long typeId;
    
    public MemoryRegion(String regionName , long typeId , AddressRange range,Flag... flags) 
    {
        if (StringUtils.isBlank(regionName)) {
            throw new IllegalArgumentException("regionName must not be NULL/blank.");
        }
        if ( range == null ) {
            throw new IllegalArgumentException("startingAddress must not be NULL.");
        }
        final SizeInWords sizeInWords = range.getSize().toSizeInWords();
        if ( sizeInWords.getValue() < 1 ) {
            throw new IllegalArgumentException("Memory size must be >= 1 word(s), invalid address range passed: "+range);
        }
        if ( flags != null ) {
        	this.flags.addAll( Arrays.asList( flags ) );
        }
        this.typeId = typeId;
    	this.supportsMerging = this.flags.contains( Flag.SUPPORTS_MERGING );
        this.memory = new  AtomicIntegerArray( sizeInWords.getValue() );
        this.addressRange = range;
        this.regionName = regionName;    	
    }

    @Override
    public boolean contains(int wordAddress) {
    	return addressRange.contains( wordAddress );
    }
    
    @Override
    public long getTypeId() {
    	return typeId;
    }
    
    public Set<Flag> getFlags() {
		return new HashSet<Flag>( this.flags );
	}
    
    public boolean hasFlag(Flag flag) {
    	if (flag == null) {
			throw new IllegalArgumentException("flag must not be null");
		}
    	return flags.contains( flag );
    }
    
    @Override
    public boolean supportsMerging()
    {
        return supportsMerging;
    }
    
    public int read(int wordAddress) {
        return memory.get( wordAddress );
    }
    
    public int read(Address address) {
        return memory.get( address.toWordAddress().getValue() );
    }
 
    public void write(int wordAddress,int value) {
        memory.set( wordAddress , value & 0xffff );
    }
    
    public void write(Address address,int value) {
        memory.set( address.toWordAddress().getValue() , value & 0xffff);
    }

    @Override
    public void clear()
    {
        final int len = memory.length();
        for ( int i = 0 ; i < len ; i++ ) {
            memory.set(i,0);
        }
    }

    @Override
    public Size getSize()
    {
        return addressRange.getSize();
    }

    @Override
    public String getRegionName()
    {
        return regionName;
    }
    
    private IMemoryRegion createCopy(AddressRange range) 
    {
        final MemoryRegion result = new MemoryRegion( regionName , this.typeId , range , this.flags.toArray(new Flag[this.flags.size()]));

        final int numberOfMemWordsToCopy= range.getSize().toSizeInWords().getValue();
        int readAddress = ( range.getStartAddress().minus( getAddressRange().getStartAddress() ) ).toWordAddress().getValue();
        
        for ( int index = 0 ; index < numberOfMemWordsToCopy; index++ ) 
        {
            result.memory.set( index , memory.get( readAddress++ ) );
        }
        return result;
    }

    @Override
    public List<IMemoryRegion> split(AddressRange gap)
    {
        final List<AddressRange> regions = addressRange.subtract( gap );
        final List<IMemoryRegion> result = new ArrayList<>();
        
        for ( AddressRange region : regions ) {
            result.add( createCopy( region ) );
        }
        return result;
    }

    @Override
    public AddressRange getAddressRange()
    {
        return addressRange;
    }
    
    @Override
    public String toString()
    {
        return getRegionName()+" - "+getAddressRange().toString();
    }    
}