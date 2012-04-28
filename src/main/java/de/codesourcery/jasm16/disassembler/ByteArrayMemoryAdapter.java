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

import org.apache.commons.lang.ArrayUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.IMemory;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Wraps a byte array into an {@link IMemory}.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ByteArrayMemoryAdapter implements IMemory {
    
	private final byte[] data;
	
	public ByteArrayMemoryAdapter(byte[] data) {
		if (data == null) {
			throw new IllegalArgumentException("data must not be NULL");
		}
		this.data = data;
	}
	
	@Override
	public int getSizeInBytes() {
		return data.length;
	}
	
    @Override
    public int readWord(Address address)
    {
        int offset = address.toByteAddress().getValue();
        if ( offset >= getSizeInBytes() ) {
            throw new IllegalArgumentException("Address "+Misc.toHexString( address )+
            		" is out-of-range (0-"+getSizeInBytes()+")");
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
    
    @Override
    public byte[] getBytes(Address startAddress, int lengthInBytes)
    {
        final int startOffset =  startAddress.toByteAddress().getValue();
        
        if ( startOffset < 0 || startOffset >= getSizeInBytes() ) {
            throw new IllegalArgumentException("Address is out-of-range: "+startAddress+"( expected 0 - "+
            		Misc.toHexString(getSizeInBytes()));
        }
        
        
        final int endOffset = (int) ( ( ( startOffset + lengthInBytes ) % getSizeInBytes() ) & 0xffff ); 
        if ( startOffset <= endOffset ) {
            return ArrayUtils.subarray( data , startOffset , endOffset-startOffset );
        }
        // need to properly wrap-around for displaying stack frames: getBytes( 65535 , 2 ) ;
        final byte[] firstArray = ArrayUtils.subarray( data , startOffset , 65536 - startOffset );
        final byte[] secondArray = ArrayUtils.subarray( data , 0 , endOffset );
        final byte[] result = new byte[ firstArray.length + secondArray.length ];
        System.arraycopy( firstArray , 0 , result , 0 , firstArray.length );
        System.arraycopy( secondArray , 0 , result , firstArray.length , secondArray.length );
        return result;
    }
    
    @Override
    public void bulkLoad(Address startingOffset, byte[] data)
    {
        throw new UnsupportedOperationException();
    }
};