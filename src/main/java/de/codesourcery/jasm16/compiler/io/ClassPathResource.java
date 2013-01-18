package de.codesourcery.jasm16.compiler.io;

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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Misc;

/**
 * {@link IResource} implementation that wraps a resource from 
 * the classpath.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ClassPathResource extends AbstractResource
{
    private final String classpathLocation;
    
    private byte[] contents;
    
    public ClassPathResource(String classpathLocation,ResourceType type)
    {
    	super(type);
    	
		if (StringUtils.isBlank(classpathLocation)) {
			throw new IllegalArgumentException(
					"classpathLocation must not be NULL/blank");
		}
        this.classpathLocation = classpathLocation;
    }

    @Override
    public InputStream createInputStream() throws IOException
    {
        final InputStream stream = getClass().getClassLoader().getResourceAsStream( classpathLocation );
        if ( stream == null ) {
        	throw new FileNotFoundException("Unable to load classpath resource '"+classpathLocation+"'");
        }
        return stream;
    }

    @Override
    public OutputStream createOutputStream(boolean append) throws IOException
    {
        throw new IOException("Cannot write to classpath resource '"+classpathLocation+"'");
    }

    private byte[] loadContents() throws IOException 
    {
        if ( contents == null ) 
        {
       		contents = Misc.readBytes( this );
        }
        return contents;
    }
    
    @Override
    public String readText(ITextRegion range) throws IOException
    {
        loadContents();
        return range.apply( new String( contents ) );
    }
    
    @Override
    public String toString()
    {
        return classpathLocation;
    }

    @Override
    public long getAvailableBytes() throws IOException
    {
    	loadContents();
        return this.contents.length;
    }

	@Override
	public String getIdentifier() {
		return classpathLocation;
	}
}
