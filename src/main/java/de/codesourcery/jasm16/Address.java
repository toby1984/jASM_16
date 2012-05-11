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


/**
 * Represents an address (immutable object).
 * 
 * <p>This class provides an abstraction for address values to avoid conversion errors 
 * when converting byte-sized addresses to/from word-sized addresses or doing address calculations.</p>
 * 
 * <p>Conversion between these types is possible using the {@link #toByteAddress()} and
 * {@link #toWordAddress()} methods.
 * </p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class Address implements Comparable<Address>
{
    public static final Address ZERO = new WordAddress(0);
    
    public static WordAddress wordAddress( long value) 
    {
        return new WordAddress( value );
    }
    
    public static ByteAddress byteAddress( long value) 
    {
        return new ByteAddress( value );
    }    
    
    @Override
    public int compareTo(Address other)
    {
        final int value1 = this.toByteAddress().getValue();
        final int value2 = other.toByteAddress().getValue();
        if ( value1 < value2 ) {
            return -1;
        } 
        
        if ( value1 > value2 ) {
            return 1;
        }
        return 0;
    }
    
    public final boolean isLessThan(Address other) 
    {
        return this.compareTo( other ) < 0;
    }
    
    public final boolean isEqualOrLessThan(Address other) 
    {
        return this.compareTo( other ) <= 0;
    }    
    
    public final boolean isGreaterThan(Address other) 
    {
        return this.compareTo( other ) > 0;
    }    
    
    public final boolean isEqualOrGreaterThan(Address other) 
    {
        return this.compareTo( other ) >= 0;
    }     
    
    @Override
    public final boolean equals(Object obj)
    {
        if ( obj == this ) {
            return true;
        }
        if ( obj instanceof Address) 
        {
        	final int value1 = this.toByteAddress().getValue();
        	final int value2 = ((Address) obj).toByteAddress().getValue();
            return value1 == value2;
        }
        return false;
    }
    
    @Override
    public final int hashCode()
    {
        return toByteAddress().getValue();
    }
    
    /**
     * Increments this address by one (with wrapping at end of DCPU16 address space).
     *  
     * <p>The DCPU address space is currently 64k words (=128 MB).</p>
     * @return
     */
    public abstract Address incrementByOne();
    
    /**
     * Decrements this address by one (with wrapping at end of DCPU16 address space).
     *  
     * <p>The DCPU address space is currently 64k words (=128 MB).</p>
     * @return
     */
    public abstract Address decrementByOne();    
    
    /**
     * Add another address to this one (while wrapping at end of DCPU16 address space).
     *  
     * <p>The DCPU address space is currently 64k words (=128 MB).</p>
     * @return
     */    
    public abstract Address plus(Address other);
    
    /**
     * Subtract another address from this one (while wrapping at start of DCPU16 address space).
     *  
     * <p>The DCPU address space is currently 64k words (=128 MB).</p>
     * @return
     */     
    public abstract Address minus(Address other);    
    
    public abstract Address minus(Size size);
    
    public abstract Address plus(Size size);    
    
    /**
     * Returns the raw value of this address.
     * 
     * <p>Make sure you know whether this is a word-sized or byte-sized
     * address!
     * </p>
     * @return
     */
    public abstract int getValue();
    
    public abstract ByteAddress toByteAddress();
    	
    public abstract WordAddress toWordAddress();
    
	public static int alignTo16Bit(int sizeInBytes) 
	{
		int result = sizeInBytes;
		while ( (result % 2 ) != 0 ) {
			result++;
		}
		return result;
	}
	
	public static int calcDistanceInBytes(Address start, Address end) {
	    
	    Address startNormalized = start.toByteAddress();
	    Address endNormalized = end.toByteAddress();
	    
	    if ( startNormalized.getValue() <= endNormalized.getValue() ) {
	        return endNormalized.getValue() - startNormalized.getValue();
	    }
	    
	    final int len1 = (int) ((ByteAddress.MAX_ADDRESS+1) - startNormalized.getValue());
	    final int len2 = endNormalized.getValue();
	    return len1+len2;
	}
    
}
