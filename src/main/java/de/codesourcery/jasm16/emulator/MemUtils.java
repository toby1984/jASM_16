package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;

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
    public static byte[] getBytes(IReadOnlyMemory memory, Address startAddress, int numberOfBytesToRead)
    {
        final byte[] result = new byte[numberOfBytesToRead];

        final int lengthInWords = numberOfBytesToRead >>> 1;

        final int end = startAddress.toWordAddress().getValue()+lengthInWords;

        int bytesLeft = numberOfBytesToRead;
        int index = 0;
        for ( int currentWord = startAddress.toWordAddress().getValue() ; currentWord < end && bytesLeft > 0; currentWord++ ) 
        {
            @SuppressWarnings("deprecation")
            final int value = memory.read( (int) ( currentWord % (WordAddress.MAX_ADDRESS+1) ) );
            result[index++] = (byte) ( ( value  >>> 8 ) & 0xff );      
            bytesLeft--;
            if ( bytesLeft <= 0 ) {
                break;
            }
            result[index++] = (byte) ( value & 0xff );                 
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
