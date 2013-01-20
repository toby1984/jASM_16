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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * {@link IResource} implementation that wraps a plain string.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class StringResource extends AbstractResource
{
    private String data;
    private final String identifier;
    
    public StringResource(String identifier, String data,ResourceType type)
    {
    	super( type );
    	
		if (StringUtils.isBlank(identifier)) {
			throw new IllegalArgumentException(
					"identifier must not be NULL/blank");
		}
        if (data == null) {
            throw new IllegalArgumentException("data must not be NULL.");
        }
        this.identifier = identifier;
        this.data = data;
    }

    @Override
    public InputStream createInputStream() throws IOException
    {
        return new ByteArrayInputStream( data.getBytes() );
    }

    @Override
    public OutputStream createOutputStream(boolean append) throws IOException
    {
        final OutputStream result = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException
            {
                super.close();
                data = new String( toByteArray() );
            }
        };
        return result;
    }

    @Override
    public String readText(ITextRegion range) throws IOException
    {
        if (range == null) {
            throw new IllegalArgumentException("text range must not be NULL.");
        }
        return range.apply( data );
    }

    @Override
    public String toString()
    {
        return "string resource ("+identifier+")";
    }

    @Override
    public long getAvailableBytes() throws IOException
    {
        return data.getBytes().length;
    }

	@Override
	public String getIdentifier() {
		return identifier;
	}	
}
