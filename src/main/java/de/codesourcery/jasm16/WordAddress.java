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
        if ( value > (MAX_ADDRESS+1) ) { // I don't check for ( value > MAX_ADDRESS ) because the AddressRange class does ( start , end[ and thus the end address may be MAX_ADDRESS + 1
            throw new IllegalArgumentException("Address value must be <= "+MAX_ADDRESS+": "+value);
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

    @Override
    public Address incrementByOne(boolean wrap)
    {
        final int newValue;
        if ( wrap ) {
            newValue = (int) ( (getValue()+1) % (WordAddress.MAX_ADDRESS+1) );
        } else {
            newValue = getValue()+1;
        }
        return new WordAddress( newValue );
    }

    @Override
    public Address plus(Address other,boolean wrap)
    {
        final int sum = other.getWordAddressValue() + getValue();
        final int newValue;
        if ( wrap ) {
            newValue = (int) ( sum % (WordAddress.MAX_ADDRESS+1) );
        } else {
            newValue = sum;
        }
        return new WordAddress( newValue );
    }
    
    @Override
    public Address plus(Size size,boolean wrap)
    {
        final int sum = getValue() + size.getSizeInWords();
        final int newValue;
        if ( wrap ) {
            newValue = (int) ( sum % (WordAddress.MAX_ADDRESS+1) );            
        } else {
            newValue = sum;
        }
        return new WordAddress( newValue );
    }       
    
    public int getByteAddressValue() {
        return value <<1;
    }
    
    public int getWordAddressValue() {
        return value;
    }
    
    @Override
    public Address decrementByOne()
    {
        int newValue = getValue() - 1;
        if ( newValue < 0 ) {
            newValue = (int) ( (WordAddress.MAX_ADDRESS+1) + newValue );
        }
        return new WordAddress( newValue );
    }

    @Override
    public Address minus(Address other)
    {
        int newValue = getValue() - other.getWordAddressValue();
        if ( newValue < 0 ) {
            newValue = (int) ( (WordAddress.MAX_ADDRESS+1)+newValue );
        }
        return new WordAddress( newValue );
    }

    @Override
    public Address minus(Size size)
    {
        int newValue = getValue() - size.getSizeInWords();
        if ( newValue < 0 ) {
            newValue = (int) ( (WordAddress.MAX_ADDRESS+1)+newValue );
        }
        return new WordAddress( newValue );
    }

}
