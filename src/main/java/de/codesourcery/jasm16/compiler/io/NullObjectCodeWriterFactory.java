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

import de.codesourcery.jasm16.compiler.ICompilationContext;

/**
 * {@link IObjectCodeWriterFactory} that creates writers which silently discard
 * any data written to them.  
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class NullObjectCodeWriterFactory extends AbstractObjectCodeWriterFactory
{
    @Override
    protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context)
    {
        return new AbstractObjectCodeWriter() {
            
            @Override
            protected void deleteOutputHook() throws IOException
            {
            }
            
            @Override
            protected OutputStream createOutputStream() throws IOException
            {
                return new OutputStream() {

                    @Override
                    public void write(int b) throws IOException
                    {
                    }};
            }
            
            @Override
            protected void closeHook() throws IOException
            {
            }
        };
    }

    @Override
    protected void deleteOutputHook() throws IOException
    {
    }
}
