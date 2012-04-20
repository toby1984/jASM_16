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

public abstract class AbstractObjectCodeWriterFactory implements IObjectCodeWriterFactory
{
    protected IObjectCodeWriter objectCodeWriter;
    
    public AbstractObjectCodeWriterFactory() {
    }
    
    @Override
    public IObjectCodeWriter getWriter(ICompilationContext context)
    {
        if ( objectCodeWriter == null ) {
            objectCodeWriter= createObjectCodeWriter(context);
        }
        return objectCodeWriter;
    }

    protected abstract IObjectCodeWriter createObjectCodeWriter(ICompilationContext context);

    @Override
    public final void closeObjectWriters() throws IOException
    {
        if ( objectCodeWriter != null ) 
        {
            try {
                objectCodeWriter.close();
            } finally {
                objectCodeWriter = null;
            }
        }        
    }
    
    @Override
    public final void deleteOutput() throws IOException
    {
        closeObjectWriters();
        deleteOutputHook();
    }
    
    protected abstract void deleteOutputHook() throws IOException;

}
