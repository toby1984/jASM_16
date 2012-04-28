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

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;

public class FileResourceResolver implements IResourceResolver 
{

    private File baseDir;
    public FileResourceResolver() {
    }
    
    public FileResourceResolver(File baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir must not be NULL.");
        }
        if ( ! baseDir.exists() ) {
            throw new IllegalArgumentException("Directory '"+baseDir.getAbsolutePath()+"' does not exist");
        }
        if ( baseDir.isDirectory() ) {
            throw new IllegalArgumentException("'"+baseDir.getAbsolutePath()+"' is no directory?");
        }        
        this.baseDir = baseDir;
    }    
    
    @Override
    public IResource resolve(String identifier) throws ResourceNotFoundException
    {
        if (StringUtils.isBlank(identifier)) {
            throw new IllegalArgumentException("identifier must not be NULL/blank.");
        }

        final File file = new File(identifier);
        if ( ! file.isFile() ) {
            throw new ResourceNotFoundException( "File "+file.getAbsolutePath()+" does not exist." , identifier );
        }
        
        if ( ! file.isFile() ) {
            throw new ResourceNotFoundException( file.getAbsolutePath()+" is not a regular file." , identifier );
        }        
        
        return new FileResource( file , ResourceType.UNKNOWN);
    }

    @Override
    public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException
    {
        if ( ! (parent instanceof FileResource) ) {
            throw new IllegalArgumentException("Called with non-file resource "+parent);
        }
        if ( identifier.startsWith( File.pathSeparator ) ) {
            return resolve( identifier );
        }
        final File parentFile;
        if ( baseDir == null ) {
            parentFile= ((FileResource) parent).getFile().getParentFile();
        } else {
            parentFile = baseDir;
        }
        if ( parentFile == null ) {
            return resolve( identifier );
        }
        return new FileResource( new File( parentFile , identifier ) , ResourceType.UNKNOWN );
    }
}