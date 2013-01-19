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
package de.codesourcery.jasm16.exceptions;

import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * Thrown when a resource could not be found.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see IResourceResolver#resolve(String)
 */
public class ResourceNotFoundException extends RuntimeException
{
    private final String identifier;
    
    public ResourceNotFoundException(String message, String identifier)
    {
        super( message );
        this.identifier = identifier;
    }
    
    public ResourceNotFoundException(String message, String identifier,Throwable cause)
    {
        super( message , cause );
        this.identifier = identifier;
    }    

    public String getIdentifier()
    {
        return identifier;
    }
    
}
