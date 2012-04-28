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

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.ICompilationContext;

public class ByteArrayObjectCodeWriterFactory extends AbstractObjectCodeWriterFactory
{
    private ByteArrayOutputStream out;
    
    private int firstWriteOffset = 0;
    
    @Override
    protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context)
    {
        if ( out == null ) {
            out = new ByteArrayOutputStream();
        }
        return new AbstractObjectCodeWriter() {

            
            @Override
            public void advanceToWriteOffset(Address offset) throws IOException
            {
                super.advanceToWriteOffset(offset);
                if ( firstWriteOffset == 0 ) {
                    firstWriteOffset = offset.getValue();
                }
            }
            
            @Override
            protected void closeHook() throws IOException
            {
            }

            @Override
            protected OutputStream createOutputStream() throws IOException
            {
                return out;
            }

            @Override
            protected void deleteOutputHook() throws IOException
            {
                out = null;
            }
        };
    }
    
    /**
     * Returns the first write offset in BYTES.
     * 
     * @return
     */
    public int getFirstWriteOffset() {
        return firstWriteOffset;
    }

    @Override
    protected void deleteOutputHook() throws IOException
    {
        firstWriteOffset = -1;
        out = null;
    }

    public byte[] getBytes() {
        return out == null ? null : out.toByteArray();
    }
}
