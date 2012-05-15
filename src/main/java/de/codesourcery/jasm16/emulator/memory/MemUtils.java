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

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.utils.IMemoryIterator;

/**
 * Utility methods related to the emulator's DCPU-16  memory emulation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class MemUtils
{
    /**
     * Copies data from a byte array into memory.
     *  
     * @param memory memory to copy data into
     * @param startingOffset the memory start address where data should be copied to 
     * @param data the data to be copied to the given memory location
     */
    @SuppressWarnings("deprecation")
    public static void bulkLoad(IMemory memory, Address startingOffset, byte[] data) {

        int current = startingOffset.toWordAddress().getValue();
        int pointer=0;
        int value=0;
        while ( pointer < data.length ) 
        {
            value= data[pointer++];
            if ( pointer < data.length ) {
                value = (value << 8) | (0xff & data[pointer++]);
            }
            memory.write( current++ , value );
        }
    }
    
    /**
     * Read data from memory into a byte array.
     * 
     * <p>
     * Note that this method assumes the memory (that only stores 16-bit words) to 
     * store data in big-endian format (so the least-significant byte comes first). 
     * </p>
     * 
     * @param memory
     * @param startAddress address to start reading at
     * @param numberOfBytesToRead
     * @return
     */
    public static byte[] getBytes(IReadOnlyMemory memory, 
    		Address startAddress, 
    		Size numBytesToRead,boolean wrap)
    {
    	final int numberOfBytesToRead = numBytesToRead.toSizeInBytes().getValue();
        final byte[] result = new byte[numberOfBytesToRead];
        
        final IMemoryIterator iterator = new MemoryIterator(memory,startAddress,
        		Size.bytes( numberOfBytesToRead) , wrap ); 

        int bytesLeft = numberOfBytesToRead;
        for ( int index = 0 ; iterator.hasNext() && bytesLeft > 0 ; ) 
        {
            final int value = iterator.nextWord();
            result[index++] = (byte) ( ( value  >>> 8 ) & 0xff );      
            bytesLeft--;
            if ( bytesLeft <= 0 ) {
                break;
            }
            result[index++] = (byte) ( value & 0xff );
            bytesLeft--;
        }
        return result;        
    }    
    
    @SuppressWarnings("deprecation")
    public static void memCopy(IReadOnlyMemory source,IMemory target,Address startAddress,Size length) {
        
        int start = startAddress.toWordAddress().getValue();
        final int lengthInWords = length.toSizeInWords().getValue();
        for ( int i = 0 ; i < lengthInWords ; i++ ) {
            target.write( i , source.read( start++ ) );
        }
    }
}
