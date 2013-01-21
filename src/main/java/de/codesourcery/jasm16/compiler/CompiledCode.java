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

import de.codesourcery.jasm16.compiler.io.IResource;


public final class CompiledCode 
{
    private final IResource objectCode;
    private final ICompilationUnit compilationUnit;

    @Override
    public String toString() {
    	return "CompiledCode[ "+compilationUnit.getResource()+" => "+objectCode+" ]";
    }
    
    public CompiledCode(ICompilationUnit compilationUnit, IResource objectCode)
    {
        if ( compilationUnit == null ) {
            throw new IllegalArgumentException("compilationUnit must not be NULL.");
        }
        if ( objectCode == null ) {
            throw new IllegalArgumentException("objectCode must not be NULL.");
        }
        this.compilationUnit = compilationUnit;
        this.objectCode = objectCode;
    }
    
    public IResource getObjectCode()
    {
        return objectCode;
    }
    
    public ICompilationUnit getCompilationUnit()
    {
        return compilationUnit;
    }
} 