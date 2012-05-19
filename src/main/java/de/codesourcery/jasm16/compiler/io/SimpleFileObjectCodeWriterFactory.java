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
import java.io.IOException;

import de.codesourcery.jasm16.compiler.ICompilationContext;

public class SimpleFileObjectCodeWriterFactory extends AbstractObjectCodeWriterFactory
{
    private File outputFile;
    private boolean append;
    
    protected SimpleFileObjectCodeWriterFactory() {
    }
    
    public SimpleFileObjectCodeWriterFactory(File outputFile,boolean append) {
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile must not be NULL.");
        }
        this.append = append;
        this.outputFile = outputFile;
    }
    
    protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context)
    {
        return new FileObjectCodeWriter( this.outputFile  , append );
    }

    
    protected void deleteOutputHook() throws IOException
    {
        if ( outputFile != null && outputFile.isFile() && outputFile.exists() ) {
            outputFile.delete();
        }
    }

}
