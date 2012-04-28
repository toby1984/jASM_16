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
 * Represents an address pointing to a WORD in the DCPU-16 address space.
 * 
 * <p>Since the DCPU-16 does not support addressing individual bytes, the
 * word-address equals <code>byteAddress >> 1</code>.</p>
 * @author tobias.gierke@code-sourcery.de
 */
public class WordAddress extends Address
{
	/**
	 * Highest possible DCPU-16 Address (in 16-bit WORDS).
	 */
    public static final long MAX_ADDRESS = 65536-1; // -1 because memory starts at offset 0
    
    public static final WordAddress ZERO = new WordAddress(0);
    
    private final int value;
    
    protected WordAddress(long value) throws IllegalArgumentException 
    {
        if ( value < 0 ) {
            throw new IllegalArgumentException("Address value must be positive: "+value);
        }
        if ( value > MAX_ADDRESS ) {
            throw new IllegalArgumentException("Address value must be less than "+MAX_ADDRESS+": "+value);
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
		return new ByteAddress( getValue() << 1 );
	}

	@Override
	public WordAddress toWordAddress() {
		return this;
	}
    
}
