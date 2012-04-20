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
import java.io.InputStream;
import java.io.OutputStream;

import de.codesourcery.jasm16.utils.ITextRange;

/**
 * A generic resource that one can read from / write to (e.g. a file). 
 * 
 * @author tobias.gierke@code-sourcery.de
 * 
 * @see FileResource
 * @see StringResource
 */
public interface IResource
{
    /**
     * Returns an input stream to read from.
     * 
     * @return
     * @throws IOException
     */
    public InputStream createInputStream() throws IOException;
    
    /**
     * Returns an outut stream to write to.
     * 
     * @boolean append
     * @return
     * @throws IOException
     */
    public OutputStream createOutputStream(boolean append) throws IOException;    
    
    /**
     * Returns data from a specific location of this resource, converting 
     * the results to a string.
     * 
     * @param range
     * @return
     * @throws IOException
     */
    public String readText(ITextRange range) throws IOException;    
    
    /**
     * Returns an estimate of the number of bytes that can be read 
     * from this resource without blocking.
     * 
     * @return available bytes
     * @throws IOException
     */
    public long getAvailableBytes() throws IOException;
}
