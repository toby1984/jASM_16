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

import de.codesourcery.jasm16.compiler.ICompilationContext;

/**
 * Factory for {@link IObjectCodeWriter} instances.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IObjectCodeWriterFactory
{
    /**
     * Returns an object code writer for the current compilation context.
     * 
     * @param context
     * @return
     */
    public IObjectCodeWriter getWriter(ICompilationContext context);
    
    /**
     * Closes any active object writers that were obtained by calling
     * {@link #getObjectCodeWriterFactory()}.
     *   
     * @throws IOException
     */
    public void closeObjectWriters() throws IOException;   
    
    /**
     * Deletes all output written by any of the writers returned
     * by this class since the last call to {@link #closeObjectWriters()}.   
     * 
     * @throws IOException
     */
    public void deleteOutput() throws IOException;    
}
