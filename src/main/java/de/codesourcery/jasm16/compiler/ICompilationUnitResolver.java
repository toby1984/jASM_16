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

import java.io.IOException;

import de.codesourcery.jasm16.ast.IncludeSourceFileNode;
import de.codesourcery.jasm16.compiler.io.IResource;

/**
 * Used to look-up the {@link ICompilationUnit} associated with a
 * specific {@link IResource}.
 * 
 * <p>Implementations of this interface are used when parsing {@link IncludeSourceFileNode}
 * AST nodes.
 * </p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationUnitResolver {

	/**
	 * Locates up a compilation unit by resource, either returning an already existing one
	 * or creating a new instance that will be resolvable by future calls. 
	 * 
	 * @param resource
	 * @return
	 * @throws IOException
	 */
	public ICompilationUnit getOrCreateCompilationUnit(IResource resource) throws IOException;
	
	/**
	 * 
	 * @param resource
	 * @return compilation unit or <code>null</code>
	 * @throws IOException
	 */
	public ICompilationUnit getCompilationUnit(IResource resource) throws IOException;	
}
