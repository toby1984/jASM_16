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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Misc;

/**
 * {@link IResource} implementation that wraps a {@link java.io.File}.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class FileResource extends AbstractResource 
{
    private final File file;
    
    private String contents;
    
    public FileResource(File file,ResourceType type)
    {
    	super( type );
        if (file == null) {
            throw new IllegalArgumentException("file must not be NULL.");
        }
        this.file = file;
    }

    
    public InputStream createInputStream() throws IOException
    {
        if ( contents == null ) {
        	return new FileInputStream( file );
        }
        return new ByteArrayInputStream( contents.getBytes() );
    }

    
    public OutputStream createOutputStream(boolean append) throws IOException
    {
        return new FileOutputStream( file , append);
    }

    private String loadContents() throws IOException {
        if ( contents == null ) {
           contents = Misc.readSource( this );
        }
        return contents;
    }
    
    
    public String readText(ITextRegion range) throws IOException
    {
        loadContents();
        return range.apply( contents );
    }
    
    
    public String toString()
    {
        return file.getAbsolutePath();
    }

    
    public long getAvailableBytes() throws IOException
    {
        return file.length();
    }
    
    public File getAbsoluteFile()
    {
        return file.getAbsoluteFile();
    }    

    
    public boolean supportsDelete() {
    	return true;
    }
    
    
    public void delete() throws IOException 
    {
    	if ( file.exists() ) {
    		if ( ! file.delete() ) {
    			throw new IOException("Failed to delete "+file.getAbsolutePath());
    		}
    	}
    }

    public File getFile()
    {
        return file;
    }

	
	public String getIdentifier() {
		return file.getAbsolutePath();
	}

    
    public boolean isSame(IResource other)
    {
        if ( other == this ) {
            return true;
        }
        if ( other instanceof FileResource) {
            return this.file.getAbsolutePath().equals( ((FileResource) other).file.getAbsolutePath() );
        }
        return false;
    }	
}
