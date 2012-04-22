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

import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;

public class Equation extends AbstractSymbol implements IValueSymbol {

	private final TermNode expression;
	
	public Equation(ICompilationUnit unit, 
			ITextRegion location,
			Identifier identifier,
			TermNode expression) 
	{
		super(unit, location, identifier);
		if ( expression == null ) {
			throw new IllegalArgumentException("expression must not be NULL");
		}
		this.expression = expression;
	}

	@Override
	public Long getValue(ISymbolTable symbolTable) 
	{
		return expression.calculate( symbolTable );
	}

	@Override
	public void setValue(Long value) {
		throw new UnsupportedOperationException( "cannot set value of constant equation");
	}
}
