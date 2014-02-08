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
package de.codesourcery.jasm16;

import java.util.ArrayList;
import java.util.List;

/**
 * An (immutable) address range made up of starting address and size.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class AddressRange
{
    // note that client codes RELIES on this class being immutable !
    private final Address start;
    private final Address end;
    private final Size size;

    public AddressRange(Address start, Address end) {
        if ( start == null ) {
            throw new IllegalArgumentException("start of address range must not be NULL.");
        }
        if ( end == null ) {
            throw new IllegalArgumentException("end of address range must not be NULL.");
        }
        if ( start.isGreaterThan( end ) ) {
            throw new IllegalArgumentException("Start of address range must not be greater than end, start: "+start+" , end: "+end);
        }
        this.start = start;
        this.size = Size.bytes( end.toByteAddress().getValue() - start.toByteAddress().getValue() );
        this.end = start.plus( size , false );
    }    
    
    public boolean overlaps(AddressRange other) {
    	
    	if ( contains( other.getStartAddress() ) ) {
    		return true;
    	}
    	
    	if ( other.contains( getStartAddress() ) ) {
    		return true;
    	}    	
    	
    	if ( this.getStartAddress().isLessThan( other.getStartAddress() ) && 
    		 this.getEndAddress().isEqualOrGreaterThan( other.getEndAddress() ) ) 
    	{
    		return true;
    	}
    	
    	if ( other.getStartAddress().isLessThan( this.getStartAddress() ) && 
    			other.getEndAddress().isEqualOrGreaterThan( this.getEndAddress() ) ) 
       	{
    		return true;
       	}    	
    	
    	return false;
    }
    
    @Override
    public final boolean equals(Object obj)
    {
        if ( this == obj ) {
            return true;
        }
        if ( obj instanceof AddressRange) {
            final AddressRange other = (AddressRange) obj;
            return this.getStartAddress().equals( other.getStartAddress() ) &&
                   this.getSize().equals( other.getSize() );
        }
        return false;
    }
    
    /**
     * Converts a global address to a local one (relative to this adress range's start).
     * 
     * <p>If the input address 
     * </p>
     * @param address
     * @return
     */
    public int globalAddressToLocal(int address) {
        return address - start.getWordAddressValue();
    }
    
    @Override
    public final int hashCode()
    {
        int result = 31 + start.hashCode();
        result += 31*result + size.hashCode();
        return result;
    }

    public AddressRange(Address start, Size size) {
        if ( start == null ) {
            throw new IllegalArgumentException("start must not be NULL.");
        }
        if (size == null) {
            throw new IllegalArgumentException("words must not be NULL.");
        }
        this.start = start;
        this.size = size;
        this.end = start.plus( size , false );
    }

    public Address getStartAddress()
    {
        return start;
    }

    public Address getEndAddress()
    {
        return end;
    }    

    public Size getSize()
    {
        return size;
    }

    @Override
    public String toString()
    {
        return getStartAddress()+" - "+getEndAddress()+" ( "+size.toSizeInWords()+" / "+size.toSizeInBytes()+" )";
    }
    
    public boolean contains(int wordAddress) 
    {
    	return wordAddress >= this.start.getWordAddressValue() &&
    		   wordAddress < this.end.getWordAddressValue();
    }    

    public boolean contains(Address address) 
    {
        // (start,end]
        return address.isEqualOrGreaterThan( getStartAddress() ) && address.isLessThan( getEndAddress() );
    }

    public boolean intersectsWith(AddressRange other) {

        if ( contains( other.getStartAddress() ) ) 
        {
            return true;
        }
        
        if ( getStartAddress().isLessThan( other.getEndAddress() ) && other.getEndAddress().isLessThan( getEndAddress() ) ) 
        {
            return true;
        }
        
        return other.getStartAddress().isLessThan( getStartAddress() ) && other.getEndAddress().isGreaterThan( getEndAddress() );
    }
    
    public AddressRange addOffset(Size size) {
    	return new AddressRange( this.getStartAddress().plus( size , false ) , getSize() );
    }    
    
    public AddressRange addOffset(Address offset) {
    	return new AddressRange( this.getStartAddress().plus( offset , false ) , getSize() );
    }

    public List<AddressRange> subtract(AddressRange gap)
    {
        if (gap == null) {
            throw new IllegalArgumentException("gap must not be NULL.");
        }
        
        final List<AddressRange> result = new ArrayList<>();

        if ( ! intersectsWith( gap ) ) {
            throw new IllegalArgumentException("Gap "+gap+" does not intersect with memory region "+this);
        }

        final Address gapStart = gap.getStartAddress();
        final Address gapEnd = gap.getEndAddress();

        // simple cases: just shrink this region by the specified amount
        if ( gapStart.equals( getStartAddress() ) ) 
        {
            // |---- gap ----|---- a ----|            
            result.add( new AddressRange( gapEnd , getEndAddress() ) );
        }
        else if ( gapEnd.equals( getEndAddress() ) ) 
        {
            // |---- a ----|---- gap ----|
            result.add( new AddressRange( getStartAddress(), gap.getStartAddress() ) );
        } 
        else 
        {
            /* two disjoint regions.
             * 
             *   |---- a ----|---- gap ----|---- c ----|
             */
            result.add( new AddressRange( getStartAddress() , gap.getStartAddress() ) );
            result.add( new AddressRange( gap.getEndAddress() , getEndAddress() ) );            
        }
        return result;
    }
    
    public boolean isAdjactantTo(AddressRange other) {
        return this.getStartAddress().equals( other.getEndAddress() ) ||
               this.getEndAddress().equals( other.getStartAddress() );
    }

    public AddressRange mergeWith(AddressRange other)
    {
        if ( contains( other ) ) {
            return this;
        }
        
        if ( other.contains( this ) ) {
            return other;
        }
        
        Address newStart;
        Address newEnd;
        if ( getStartAddress().isEqualOrLessThan( other.getStartAddress() ) ) {
            newStart = this.start;
            newEnd = other.getEndAddress();
        } else {
            newStart = other.start;
            newEnd = this.getEndAddress();
        }
        
        final AddressRange result = new AddressRange( newStart , newEnd );
        if ( result.size.getSizeInBytes() != ( size.getSizeInBytes() + other.size.getSizeInBytes() ) ) {
            throw new IllegalArgumentException("Cannot merge non-adjactant address ranges "+this+" and "+other);
        }
        return result;
    }

    public boolean contains(AddressRange newRange)
    {
        return getStartAddress().isEqualOrLessThan( newRange.getStartAddress() ) && getEndAddress().isEqualOrGreaterThan( newRange.getEndAddress() );
    }     
    
}
