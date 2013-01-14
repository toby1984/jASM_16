package de.codesourcery.jasm16.emulator.devices.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class FileBasedFloppyDisk extends FloppyDisk
{
    private final File file;

    public FileBasedFloppyDisk(File file) 
    {
        this( file , false );
    }
    
    public FileBasedFloppyDisk(File file,boolean writeProtected) 
    {
        super( file.getAbsolutePath() , writeProtected);
        this.file = file;
    }

    @Override
    public void read(byte[] buffer, long byteOffset) throws IOException
    {
        final int bufferSize=buffer.length;
        
        try ( RandomAccessFile raf = new RandomAccessFile(file, "r" ); ) 
        {
            final long fileSize = raf.length();
            if ( byteOffset >= fileSize ) 
            {
                for ( int i = 0 ; i < bufferSize ; i++ ) {
                    buffer[i] = 0;
                }
                return;
            } 
            
            // can read everything
            raf.seek( byteOffset );
            
            if ( (byteOffset+buffer.length) >= fileSize ) 
            {
                // partial read
                final int bytesAvailable = (int) ( fileSize - byteOffset ); 
                int bytesRead = raf.read( buffer , 0 , bytesAvailable );
                if ( bytesRead != bytesAvailable ) {
                    throw new IOException("Short read, got only "+bytesRead+" of "+bytesAvailable+" bytes");
                }
                for ( int i = bytesAvailable ; i < bufferSize ; i++ ) {
                    buffer[i] = 0;
                }
                return;
            }
            
            int bytesRead = raf.read( buffer );
            if ( bytesRead != buffer.length ) {
                throw new IOException("Short read, got only "+bytesRead+" of "+buffer.length+" bytes");
            }
        }
    }
    
    @Override
    public void write(byte[] buffer, long byteOffset) throws IOException
    {
        if ( isWriteProtected() ) {
            throw new IOException("Refusing to write to is write-protected disk "+this);
        }
        
        try ( RandomAccessFile raf = new RandomAccessFile(file, "rw" );) 
        {
            raf.seek( byteOffset );
            raf.write( buffer );
        }
    }
}