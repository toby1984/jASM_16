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

import de.codesourcery.jasm16.utils.Misc;

/**
 * Represents an address in the DCPU-16 address space.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Address
{
	/**
	 * DCPU-16 Address space size.
	 */
    public static final long MAX_ADDRESS = 65535;
    
    public static final Address ZERO = new Address(0);
    
    private final int value;
    
    public static Address valueOf( long value) 
    {
        if ( value == 0 ) {
            return ZERO;
        }
        return new Address( value );
    }
    
    public boolean isLessThan(Address other) {
        return this.value < other.value;
    }
    
    /**
     * Create a new instance.
     * 
     * @param value
     * @throws IllegalArgumentException if the address is not within
     * the DCPU-16 address space.
     */
    private Address(long value) throws IllegalArgumentException 
    {
        if ( value < 0 ) {
            throw new IllegalArgumentException("Address value must be positive: "+value);
        }
        if ( value > MAX_ADDRESS ) {
            throw new IllegalArgumentException("Address value must be less than "+MAX_ADDRESS+": "+value);
        }        
        this.value = (int) value;
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if ( obj == this ) {
            return true;
        }
        if ( obj instanceof Address) {
            return this.value == ((Address) obj).value;
        }
        return false;
    }
    
    public int getValue() {
        return value;
    }
    
    @Override
    public String toString()
    {
        return "0x"+Misc.toHexString( this.value );
    }

	public static int alignTo16Bit(int sizeInBytes) 
	{
		int result = sizeInBytes;
		while ( (result % 2 ) != 0 ) {
			result++;
		}
		return result;
	}
    
}
