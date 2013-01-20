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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import de.codesourcery.jasm16.compiler.ICompilationContext;

public abstract class AbstractObjectCodeWriterFactory implements IObjectCodeWriterFactory
{
	// key is ICompilationUnit#getIdentifier()
	
	// we NEED to use a LinkedHashMap here since the object files need to be linked in creation order later on
	protected final Map<String,IObjectCodeWriter> objectCodeWriters = new LinkedHashMap<String,IObjectCodeWriter>();
	
    public AbstractObjectCodeWriterFactory() {
    }
    
    @Override
    public final IObjectCodeWriter getWriter(ICompilationContext context)
    {
    	final String identifier = context.getCurrentCompilationUnit().getIdentifier();
    	IObjectCodeWriter result = objectCodeWriters.get( identifier );
    	if ( result == null ) {
            result = createObjectCodeWriter( context );
            objectCodeWriters.put( identifier , result );
        }
        return result;
    }

    protected abstract IObjectCodeWriter createObjectCodeWriter(ICompilationContext context);

    @Override
    public final void closeObjectWriters() throws IOException
    {
    	for (Iterator<IObjectCodeWriter> it = objectCodeWriters.values().iterator(); it.hasNext();) 
    	{
			final IObjectCodeWriter writer = it.next();
			writer.close();
			it.remove();
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