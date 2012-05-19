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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import junit.framework.TestCase;

import org.apache.commons.lang.ArrayUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.utils.Misc;

public class FileObjectCodeWriterTest extends TestCase
{
    private ByteArrayWriter writer;
    
    private class ByteArrayWriter extends FileObjectCodeWriter  {

        private ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        public ByteArrayWriter()
        {
        }
        
        
        public void deleteOutputHook() throws IOException
        {
            out = new ByteArrayOutputStream();
        }
        
        public byte[] getBytes() {
            return out.toByteArray();
        }
        
        
        protected OutputStream createOutputStream() throws IOException
        {
            return out;
        }
        
    }
    
    
    protected void setUp() throws Exception
    {
        this.writer = new ByteArrayWriter();
    }
    
    public void testSkipToZeroBeforeWriting() throws Exception {
        
        writer.advanceToWriteOffset( Address.ZERO );
        writer.writeObjectCode( "test".getBytes() );
        assertEquals( Address.byteAddress( 4 ) , writer.getCurrentWriteOffset() );
        writer.close();
        
        assertEquals( "test" , new String( writer.getBytes() ) );
    }
    
    public void testSkipToZeroAfterWritingFails() throws Exception {
        
        writer.writeObjectCode( "test".getBytes() );
        assertEquals( Address.byteAddress( 4 ) , writer.getCurrentWriteOffset() );
        
        try {
            writer.advanceToWriteOffset( Address.ZERO );
            fail("Should've failed");
        } catch(IllegalStateException e) {
            // ok
        }
        writer.close();
    }   
    
    public void testSkipToOffsetSevenBeforeWrite() throws Exception {

        final byte[] input = "test".getBytes();
        final int offset = 7;
        
        writer.advanceToWriteOffset( Address.byteAddress( offset ) );

        writer.writeObjectCode( input );
        assertEquals( Address.byteAddress( offset+input.length ) , writer.getCurrentWriteOffset() );
        writer.close();
        
        final byte[] data = writer.getBytes();
//        System.out.println( Misc.toHexDumpWithAddresses( offset , data , 8 ) );
        assertEquals( offset + input.length , data.length ); 
        byte[] zeros = new byte[ offset ];
        assertTrue( ArrayUtils.isEquals( zeros , ArrayUtils.subarray( data , 0 , offset ) ) );
        assertTrue( ArrayUtils.isEquals( input , ArrayUtils.subarray( data , offset , data.length ) ) );
    }     
    
    public void testSkipToOffset3500BeforeWrite() throws Exception {

        final byte[] input = "test".getBytes();
        final int offset = 3500;
        
        writer.advanceToWriteOffset( Address.byteAddress( offset ) );

        writer.writeObjectCode( input );
        assertEquals( Address.byteAddress( offset+input.length ) , writer.getCurrentWriteOffset() );
        writer.close();
        
        final byte[] data = writer.getBytes();
//        System.out.println( Misc.toHexDumpWithAddresses( offset , data , 8 ) );
        assertEquals( offset + input.length , data.length ); 
        byte[] zeros = new byte[ offset ];
        assertTrue( ArrayUtils.isEquals( zeros , ArrayUtils.subarray( data , 0 , offset ) ) );
        assertTrue( ArrayUtils.isEquals( input , ArrayUtils.subarray( data , offset , data.length ) ) );
    }    
    
    public void testSkipToOffsetAfterWrite() throws Exception {

        final byte[] input = "test".getBytes();
        final int offset = 8;
        
        writer.writeObjectCode( input );
        writer.advanceToWriteOffset( Address.byteAddress( offset ) );
        writer.writeObjectCode( input );
        
        assertEquals( Address.byteAddress( offset+input.length ) , writer.getCurrentWriteOffset() );
        writer.close();
        
        final byte[] data = writer.getBytes();
        System.out.println( Misc.toHexDumpWithAddresses( Address.byteAddress( offset ) , data , 8 ) );
        assertEquals( offset + input.length , data.length ); 
        byte[] zeros = new byte[ 4 ];
        assertTrue( ArrayUtils.isEquals( zeros , ArrayUtils.subarray( data , input.length , offset ) ) );
        assertTrue( ArrayUtils.isEquals( input , ArrayUtils.subarray( data , offset , data.length ) ) );
    }     
}
