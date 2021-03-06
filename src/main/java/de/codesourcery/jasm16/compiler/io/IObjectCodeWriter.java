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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import de.codesourcery.jasm16.Address;

/**
 * Writes object-code to an output stream.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IObjectCodeWriter extends Closeable
{
	/**
	 * Output object code.
	 *      
	 * Behaves like {@link OutputStream#write(byte[])}.
	 * 
	 * @param data
	 * @throws IOException
	 */
    public void writeObjectCode(byte[] data) throws IOException;

    /**
     * Output object code.
     * 
     * Behaves like {@link OutputStream#write(byte[], int, int)}.
     * 
     * @param data
     * @param offset
     * @param length
     * @throws IOException
     */
    public void writeObjectCode(byte[] data,int offset,int length) throws IOException;
    
    /**
     * Deletes any output generated by this writer.
     * 
     * @throws IOException
     */
    public void deleteOutput() throws IOException;
    
    /**
     * Returns the address where the first byte was written to
     * by this writer.
     * 
     * @return first write offset (BYTE address !) or <code>null</code> if nothing has been written to this writer
     */
    public Address getFirstWriteOffset();
    
    /**
     * Returns the next memory location where object code would be written.
     * 
     * @return BYTE address !
     */
    public Address getCurrentWriteOffset();
    
    /**
     * Advances writing so that the next call to <code>writeObjectCode</code> will generate
     * output at the specified memory location.
     *  
     * <p>Note that currently you can only <b>advance</b> to a location that hasn't already been output.</p>
     * @param offset address (BYTE offset!)
     * @throws IllegalStateException if this writer is already past the given address
     */
    public void advanceToWriteOffset(Address offset) throws IOException;
}
