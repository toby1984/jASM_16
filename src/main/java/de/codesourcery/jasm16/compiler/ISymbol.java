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
	 * Creates a copy of this symbol.
	 * 
	 * <p>Note that for performance reasons, the returned copy still retains a
	 * reference to the same <code>ICompilationUnit</code>. 
	 * </p>
	 * 
	 * @return
	 */
	public ISymbol createCopy();
	
	/**
	 * Returns this symbol's scope or <code>null</code> if this is a global symbol.
	 * 
	 * @return scope or <code>null</code> if this is a global symbol.
	 * @see #isLocalSymbol()
	 * @see #getScopeIdentifier()
	 */
	public ISymbol getScope();
	
	/**
	 * For local symbols, returns the global symbol the symbol is attached to.
	 * 
	 * @return global symbol name or <code>null</code>
	 * @see #isLocalSymbol()
	 */
	public Identifier getScopeName();
	
	/**
	 * Returns whether this is a local symbol.
	 * 
	 * @return local symbols are defined relative to a {@link #getScope() scope}.
	 */
	public boolean isLocalSymbol();
	
	/**
	 * Returns whether this is a local symbol.
	 * 
	 * @return global symbols are defined in the top-level scope (NULL)
	 */	
	public boolean isGlobalSymbol();
	
	/**
	 * Returns this symbol's unique identifier.
	 * 
	 * @return identifier, never <code>null</code>
	 */
	public Identifier getName();
	
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
	
	/**
	 * Creates a copy of this symbol but with a new identifier assigned.
	 * 
	 * @param newIdentifier
	 * @return
	 * @see #getIdentifier()
	 */
	public ISymbol withIdentifier(Identifier newIdentifier);
	
	/**
	 * Creates a copy of this symbol with a new scope assigned.
	 * 
	 * <p>Note that it's not possible to use this method to turn a local
	 * symbol into a global one or vice versa.</p>
	 * @param newScope
	 * @return
	 */
	public ISymbol withScope(ISymbol newScope);
	
	/**
	 * Returns this symbol's fully-qualified name.
	 * 
	 * <p>A symbol's fully-qualified name consists of it's identifier prefixed
	 * by the identifiers of any higher-up namespaces/scopes if may have. Name components
	 * are separated by dots.</p> 
	 * @return
	 */
	public String getFullyQualifiedName();
}
