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
package de.codesourcery.jasm16.disassembler;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.emulator.memory.IReadOnlyMemory;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Wraps a byte array into an {@link IReadOnlyMemory}.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ByteArrayMemoryAdapter implements IReadOnlyMemory {
    
	private final byte[] data;
	
	public ByteArrayMemoryAdapter(byte[] data) {
		if (data == null) {
			throw new IllegalArgumentException("data must not be NULL");
		}
		this.data = data;
	}
	
	
	public Size getSize() {
		return Size.bytes( data.length );
	}
	
	private int getSizeInBytes() {
	    return getSize().toSizeInBytes().getValue();
	}
	
    
    public int read(Address address)
    {
        int offset = address.toByteAddress().getValue();
        if ( offset >= getSizeInBytes() ) {
            throw new IllegalArgumentException("Address "+Misc.toHexString( address )+
            		" is out-of-range (0-"+getSize()+")");
        }
        int hi = data[offset++];
        if ( hi < 0 ) {
            hi+=256;
        }
        if ( offset >= getSizeInBytes() ) {
            return ( hi & 0x00ff);
        }                
        int lo = data[offset];
        if ( lo < 0 ) {
            lo += 256;
        }
        final int result= (( hi << 8 ) | lo) & 0xffff;
        return result;
    }

    
    public int read(int wordAddress)
    {
        return read( Address.wordAddress( wordAddress ) );
    }
    
};