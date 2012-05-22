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
    public static final WordAddress ZERO = new WordAddress(0);
    
    public static WordAddress wordAddress( long value) 
    {
    	if ( value == 0 ) {
    		return ZERO;
    	}
        return new WordAddress( value );
    }
    
    public static ByteAddress byteAddress( long value) 
    {
    	if ( value == 0 ) {
    		return ByteAddress.ZERO;
    	}
        return new ByteAddress( value );
    }    
    
    @Override
    public int compareTo(Address other)
    {
        final int value1 = this.getByteAddressValue();
        final int value2 = other.getByteAddressValue();
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
        return this.getByteAddressValue() < other.getByteAddressValue();
    }
    
    public final boolean isEqualOrLessThan(Address other) 
    {
        return this.getByteAddressValue() <= other.getByteAddressValue();
    }    
    
    public final boolean isGreaterThan(Address other) 
    {
        return this.getByteAddressValue() > other.getByteAddressValue();        
    }    
    
    public final boolean isEqualOrGreaterThan(Address other) 
    {
        return this.getByteAddressValue() >= other.getByteAddressValue();         
    }     
    
    @Override
    public final boolean equals(Object that)
    {
        if ( that == this ) {
            return true;
        }
        if ( that instanceof Address) 
        {
        	return this.getByteAddressValue() == ((Address) that).getByteAddressValue();
        }
        return false;
    }
    
    @Override
    public final int hashCode()
    {
        return getByteAddressValue();
    }
    
    /**
     * Increments this address by one (with optional wrapping at end of DCPU16 address space).
     *  
     * <p>The DCPU address space is currently 64k words (=128 KB).</p>
     * @return
     */
    public abstract Address incrementByOne(boolean wrap);
    
    /**
     * Decrements this address by one (with optional wrapping at end of DCPU16 address space).
     *  
     * <p>The DCPU address space is currently 64k words (=128 KB).</p>
     * @return
     */
    public abstract Address decrementByOne();    
    
    /**
     * Add another address to this one (while wrapping at end of DCPU16 address space).
     *  
     * <p>The DCPU address space is currently 64k words (=128 KB).</p>
     * @return
     */    
    public abstract Address plus(Address other,boolean wrap);
    
    /**
     * Subtract another address from this one (while wrapping at start of DCPU16 address space).
     *  
     * <p>The DCPU address space is currently 64k words (=128 KB).</p>
     * @return
     */     
    public abstract Address minus(Address other);    
    
    public abstract Address minus(Size size);
    
    public abstract Address plus(Size size,boolean wrap);    
    
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
    
    public abstract int getByteAddressValue();
    
    public abstract int getWordAddressValue();    
    
	public static int alignTo16Bit(int sizeInBytes) 
	{
		int result = sizeInBytes;
		while ( (result % 2 ) != 0 ) {
			result++;
		}
		return result;
	}
	
	public static Size calcDistanceInBytes(Address start, Address end) {
	    
	    int startNormalized = start.getByteAddressValue();
	    int endNormalized = end.getByteAddressValue();
	    
	    if ( startNormalized <= endNormalized ) {
	        return Size.bytes( endNormalized - startNormalized  );
	    }
	    
	    final int len1 = (int) ((ByteAddress.MAX_ADDRESS+1) - startNormalized);
	    final int len2 = endNormalized;
	    return Size.bytes( len1+len2 );
	}
    
}
