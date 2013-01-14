package de.codesourcery.jasm16.emulator.devices.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import junit.framework.TestCase;
import de.codesourcery.jasm16.utils.Misc;

public class FileBasedFloppyDiskTest extends TestCase 
{
    private static final int BYTES_PER_SECTOR = FloppyDisk.WORDS_PER_SECTOR*2;
    
    private File tmpFile;
    private FileBasedFloppyDisk disk;
    
    private byte[] buffer = new byte[BYTES_PER_SECTOR];
    
    @Override
    protected void setUp() throws Exception
    {
        tmpFile = File.createTempFile("test", "test");
        if ( ! tmpFile.exists() ) {
            tmpFile.createNewFile();
        }
        disk = new FileBasedFloppyDisk( tmpFile );
        clearBuffer();
    }
    
    protected void tearDown() throws Exception 
    {
        if ( tmpFile != null ) {
            try {
                tmpFile.delete();
            } finally {
                tmpFile = null;
            }
        }
    }
    
    private void clearBuffer() {
        buffer = new byte[BYTES_PER_SECTOR];
    }
    
    private void assertBufferEmpty() {
        for ( int i = 0 ; i < buffer.length ; i++ ) {
            if ( buffer[i] != 0 ) 
            {
                dumpBuffer(buffer,i);
                fail("Buffer not empty at index "+i+",value: "+buffer[i]);
            }
        }
    }
    
    private byte[] createRandomDisk(int sizeInBytes) throws IOException 
    {
        final byte[] result = new byte[ sizeInBytes ];
        final Random rnd = new Random(System.currentTimeMillis());
        rnd.nextBytes( result );
        
        final float sectorCount = sizeInBytes / (float) disk.getSectorSize().getSizeInBytes();
        System.out.println("*** created random disk with "+sizeInBytes+" bytes ("+(sizeInBytes>>1)+" words , "+sectorCount+" sectors)");
        final FileOutputStream out = new FileOutputStream( tmpFile );
        out.write(result);
        out.close();        
        return result;
    }
    
    private static void dumpBuffer(byte[] buffer , long byteOffset) 
    {
        final int bufferLength = buffer.length;
        final int wordsPerLine = 16;
        
        int wordAddress = (int) (byteOffset >> 1);
        
        final int bytesToDump = bufferLength - wordAddress*2;
        final int sector = (int) byteOffset / (FloppyDisk.WORDS_PER_SECTOR*2);
        System.out.println("---- Dump starting at "+Misc.toHexString( wordAddress )+" , sector "+sector+" ----");
        System.out.println( Misc.toHexDumpWithAddresses( wordAddress*2 , buffer, bytesToDump , wordsPerLine , false ) ); 
    }
    
    public void testReadStartOfEmptyFile() throws IOException 
    {
        disk.read(buffer , 0 );
        assertBufferEmpty();
    }
    
    public void testReadEndOfEmptyFile() throws IOException 
    {
        disk.read(buffer , (disk.getSectorCount()-1) * BYTES_PER_SECTOR );
        assertBufferEmpty();
    }  
    
    public void testRead() throws IOException 
    {
        final byte[] expected = createRandomDisk( disk.getSectorCount() * disk.getSectorSize().getSizeInBytes() );
        final int byteOffset = (disk.getSectorCount()-1) * BYTES_PER_SECTOR;
        disk.read( buffer , byteOffset );
        assertArraysEqual( expected , byteOffset , buffer , 0 , buffer.length );
    }
    
    public void testPartialRead1() throws IOException 
    {
        createRandomDisk( ( (disk.getSectorCount()-1) * disk.getSectorSize().getSizeInBytes() ) );
        final int byteOffset = (disk.getSectorCount()-1) * BYTES_PER_SECTOR;
        disk.read( buffer , byteOffset );
        assertBufferEmpty();
    }    
    
    public void testPartialRead2() throws IOException 
    {
        final int bytesPerSector = disk.getSectorSize().getSizeInBytes();
        final int halfSectorSize = bytesPerSector >> 1;
        
        final int lastSector = disk.getSectorCount()-1;
        
        final byte[] diskContents = createRandomDisk( ( disk.getSectorCount() * bytesPerSector ) - halfSectorSize );
        
        final int offsetOfLastSector = lastSector * BYTES_PER_SECTOR;
        
        disk.read( buffer , offsetOfLastSector );
        
        assertArraysEqual( diskContents , offsetOfLastSector , buffer , 0 , halfSectorSize );
    }    
    
    private void assertArraysEqual(byte[] expected,int expectedOffset, byte[] actual, int actualOffset , int length) {
        
        for ( int i = 0  ;  i < length ; i++ ) 
        {
            final byte val1 = expected[expectedOffset+i];
            final byte val2 = actual[actualOffset+i];
            if ( val1 != val2 ) {
                System.out.println("*** expected ***");
                dumpBuffer(expected,expectedOffset+i);
                System.out.println("*** actual ***");
                dumpBuffer(actual,actualOffset+i);                
                final int adr1 = expectedOffset + i;
                final int adr2 = actualOffset + i;
                fail("Value mismatch at actual offset #"+i+" "+Misc.toHexString( adr2  )+
                     " / expected offset "+Misc.toHexString( adr1 )+
                     " , got: "+Misc.toHexString(val2) +" but expected "+Misc.toHexString(val1));
            }
        }
    }
}
