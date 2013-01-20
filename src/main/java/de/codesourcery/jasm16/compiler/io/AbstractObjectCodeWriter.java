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
package de.codesourcery.jasm16.compiler.io;

import java.io.IOException;
import java.io.OutputStream;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.WordAddress;

/**
 * Abstract base-class for implementing {@link IObjectCodeWriter}s.
 * 
 * <p>This class takes care of properly handling {@link #advanceToWriteOffset(Address)}
 * and should be subclasses whenever possible.</p>
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AbstractObjectCodeWriter implements IObjectCodeWriter 
{
    private OutputStream writer;

    private int currentWriteOffset = 0;
    private int firstWriteOffset=0;
    
    private int initialOffset;
    
    protected AbstractObjectCodeWriter() {
    	this( WordAddress.ZERO );
    }
    
    protected AbstractObjectCodeWriter(WordAddress initialOffset) 
    {
    	this.initialOffset = initialOffset.getByteAddressValue();
    }
    
    @Override
    public final void close() throws IOException
    {
        try {
            closeHook();
        } 
        finally 
        {
            currentWriteOffset = 0;
            if ( writer != null ) {
                writer.close();
                writer = null;
            }
        }
    }
    
    protected abstract void closeHook() throws IOException;
    
    protected final OutputStream getOutputStream() throws IOException
    {
        if ( writer == null ) 
        {
            writer = createOutputStream();
            if ( currentWriteOffset != 0 ) 
            {
            	if ( currentWriteOffset > initialOffset ) {
            		writeZeros( currentWriteOffset - initialOffset );
            	}
            }
        }
        return writer;
    }
    
    @Override
    public Address getFirstWriteOffset()
    {
        return Address.byteAddress( firstWriteOffset+initialOffset );
    }
    
    private int getActualOffset() {
    	return currentWriteOffset+initialOffset;
    }
    
   protected abstract OutputStream createOutputStream() throws IOException;
    
    @Override
    public final void writeObjectCode(byte[] data) throws IOException
    {
        writeObjectCode( data , 0 , data.length );
    }

    @Override
    public final void writeObjectCode(byte[] data, int offset, int length) throws IOException
    {
        writeObjectCodeHook(data,offset,length);
        currentWriteOffset += length;
    }
    
    protected void writeObjectCodeHook(byte[] data, int offset, int length) throws IOException
    {    
        getOutputStream().write( data ,offset,length );
    }

    @Override
    public final void deleteOutput() throws IOException
    {
        this.currentWriteOffset = 0;
        try {
            close();
        } catch(IOException e) {
            // ok
        } 
        deleteOutputHook();
    }
        
    protected abstract void deleteOutputHook() throws IOException;        

    @Override
    public final Address getCurrentWriteOffset()
    {
        return Address.byteAddress( getActualOffset() );
    }

    @Override
    public void advanceToWriteOffset(Address offset) throws IOException
    {
        if (offset == null) {
            throw new IllegalArgumentException("offset must not be NULL.");
        }
        
        if ( offset.getValue() < getActualOffset() ) {
            throw new IllegalStateException("Writer "+this+" is already at "+this.currentWriteOffset+" , cannot output object code at "+offset);
        }
        
        if ( offset.getValue() == getActualOffset() ) {
            return;
        }
        
        if ( firstWriteOffset == 0 ) {
            firstWriteOffset  = offset.getValue();
        }
        
        if ( this.writer == null ) {
        	// delay writing zeros until the writer is actually opened, see getOutputStream()
            this.currentWriteOffset = offset.getValue();
            return;
        }
        
        final int delta = offset.getValue() - getActualOffset();
        writeZeros( delta );
        this.currentWriteOffset = offset.getValue();
    }
    
    protected final void writeZeros(int byteCount) throws IOException 
    {
        if ( byteCount == 0 ) {
            return;
        }
        
        int delta = byteCount;
        int bufferSize = ( byteCount >> 1) << 1;
        do {
            delta = writeZeros( delta , bufferSize);
            bufferSize = bufferSize >> 1;
        } while( delta > 0 && bufferSize > 0);
        
        while( delta > 0 ) {
            writer.write( 0 );
            delta--;
        }
        if ( delta != 0 ) {
            throw new RuntimeException("Internal error, delta !=  0 , was: "+delta);
        }
    }
    
    protected final int writeZeros(int byteCount, int bufferSize) throws IOException 
    {
        final byte[] buffer = new byte[bufferSize];
        int delta = byteCount;
        while( delta > bufferSize) {
            delta=writeZeros( buffer , delta );
        }        
        return delta;
    }    
    
    protected final int writeZeros(byte[] buffer, int byteCount) throws IOException 
    {
        int delta = byteCount;
        final int bufferSize = buffer.length;
        while( delta > bufferSize ) 
        {
            writer.write( buffer );
            delta -= bufferSize;
        }
        return delta;
    }
}
