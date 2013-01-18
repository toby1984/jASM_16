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

import java.util.List;

import de.codesourcery.jasm16.compiler.io.IResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.exceptions.UnknownCompilationOrderException;

/**
 * Used to determine the order in which <code>ICompilationUnit</code>s will be compiled.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationOrderProvider
{
    /**
     * Determine the order in which a given set of <code>ICompilationUnit</code>s should be compiled.
     * 
     * @param units
     * @param resolver resource resolver used to resolve source files while processing "includesource" directives
     * @return
     * @throws UnknownCompilationOrderException
     * @throws ResourceNotFoundException 
     */
    public List<ICompilationUnit> determineCompilationOrder(List<ICompilationUnit> units,
            IResourceResolver resolver,IResourceMatcher resourceMatcher) throws UnknownCompilationOrderException, ResourceNotFoundException;
}
