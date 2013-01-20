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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import de.codesourcery.jasm16.WordAddress;

/**
 * {@link IObjectCodeWriter} that writes to a file in the local filesystem.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class FileObjectCodeWriter extends AbstractObjectCodeWriter
{
	private final File outputFile;
	private final boolean append;
	
	protected FileObjectCodeWriter() {
		this(WordAddress.ZERO);
	}
	
	protected FileObjectCodeWriter(WordAddress offset) {
		super(offset);
	    this.outputFile = null;
	    this.append = false;
	}	
	
	public FileObjectCodeWriter(File outputFile,boolean append) 
	{
		this(outputFile,WordAddress.ZERO , append );
	}
	
	public FileObjectCodeWriter(File outputFile,WordAddress offset, boolean append) 
	{
		super(offset);
		if (outputFile == null) {
			throw new IllegalArgumentException("outputFile must not be NULL");
		}
		this.append = append;
		this.outputFile = outputFile;
	}
	
	public File getOutputFile() {
		return outputFile;
	}
	
   protected OutputStream createOutputStream() throws IOException
    {
       if ( this.outputFile.getParentFile() != null && ! this.outputFile.getParentFile().exists() ) {
           if ( ! this.outputFile.getParentFile().mkdirs() ) {
               throw new IOException("Unable to create output target directory "+this.outputFile.getParentFile().getAbsolutePath() );
           }
       }          
       return new FileOutputStream( outputFile , append );
    }
	  
    @Override
    protected void closeHook() throws IOException
    {
        // nothing to do
    }

    @Override
    protected void deleteOutputHook() throws IOException
    {
        if ( outputFile.exists() && ! outputFile.delete() ) {
            throw new IOException("Failed to delete file "+outputFile.getAbsolutePath() );
        }
    }
}
