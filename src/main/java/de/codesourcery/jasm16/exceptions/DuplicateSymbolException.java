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

import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;

/**
 * Thrown when trying to add a duplicate symbol to a symbol table.
 *  
 * @author tobias.gierke@code-sourcery.de
 * @see ISymbolTable#defineSymbol(ISymbol)
 */
public class DuplicateSymbolException extends RuntimeException {

	private final ISymbol existing;
	private final ISymbol duplicate;
	
	public DuplicateSymbolException(ISymbol existing , ISymbol duplicate) 
	{
		super("Duplicate symbol '"+duplicate+"' in compilation unit "+duplicate.getCompilationUnit()+", previous definition: "+existing );
		this.duplicate = duplicate;
		this.existing = existing;
	}
	
	public ISymbol getDuplicateDefinition() {
		return duplicate;
	}
	
	public ISymbol getExistingDefinition() {
		return existing;
	}
}
