package de.codesourcery.jasm16;

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

import de.codesourcery.jasm16.utils.Misc;

/**
 * Represents an address pointing to a specific byte.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ByteAddress extends Address
{
    private final int value;
    
    /**
     * Max. address in DCPU16 address space (byte-sized addressing).
     */
    public static final long MAX_ADDRESS = (65536*2)-1; // -1 because memory starts at offset 0      

    public static final ByteAddress ZERO = new ByteAddress(0);
    
    protected ByteAddress(long value) 
    {
    	if ( value < 0 || value > Integer.MAX_VALUE ) 
    	{
    		throw new IllegalArgumentException("Address value out-of-range: "+value);
    	}
    	this.value = (int) value;
    }
    
    public int getValue() {
        return value;
    }
    
    @Override
    public String toString()
    {
        return "0x"+Misc.toHexString( this.value );
    }

	@Override
	public ByteAddress toByteAddress() {
		return this;
	}

	@Override
	public WordAddress toWordAddress() 
	{
		final int words = value >> 1;
		if ( words != value ) {
			throw new RuntimeException("Internal error, byte address "+this+" is not on a 16-bit boundary");
		}
		return new WordAddress( value >> 1 );
	}
    
}
