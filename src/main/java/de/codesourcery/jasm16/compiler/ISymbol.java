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

import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * A symbol.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface ISymbol {

	/**
	 * Returns this symbol's unique identifier.
	 * 
	 * @return identifier, never <code>null</code>
	 */
	public Identifier getIdentifier();
	
	/**
	 * Returns the compilation unit where this symbol was defined.
	 * 
	 * @return
	 */
	public ICompilationUnit getCompilationUnit();
	
	/**
	 * Returns the source location where this
	 * label was defined within the compilation unit. 
	 * @return
	 */
	public ITextRegion getLocation();
}
