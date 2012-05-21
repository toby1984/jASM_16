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
package de.codesourcery.jasm16.compiler;


/**
 * Generic compilation error that is not related to a specific source-code location.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see CompilationError
 */
public class GenericCompilationError extends CompilationMarker implements ICompilationError
{
    public GenericCompilationError(String markerType, String message, ICompilationUnit unit)
    {
        this(markerType,message, unit, null );
    }

    public GenericCompilationError(String message, ICompilationUnit unit)
    {
        this(message, unit, null);
    }

    public GenericCompilationError(String message, ICompilationUnit unit, Throwable cause)
    {
        super(IMarker.TYPE_GENERIC_COMPILATION_ERROR, message, unit, cause,Severity.ERROR);
    }
    
    protected GenericCompilationError(String markerType , String message, ICompilationUnit unit, Throwable cause)
    {
        super( markerType , message, unit, cause,Severity.ERROR);
    }    

}
