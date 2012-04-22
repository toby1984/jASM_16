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
package de.codesourcery.jasm16.ast;

import org.apache.commons.lang.ObjectUtils;

import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.IValueSymbol;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * An AST node that represents a references to a symbol.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class SymbolReferenceNode extends ConstantValueNode
{
	private Identifier identifier;

	public Identifier getIdentifier()
	{
		return identifier;
	}

	@Override
	protected SymbolReferenceNode parseInternal(IParseContext context) throws ParseException
	{
		final int startOffset = context.currentParseIndex();
		this.identifier = context.parseIdentifier( null );
		mergeWithAllTokensTextRegion( new TextRegion( startOffset , identifier.getRawValue().length() ) );
		return this;
	}

    
    @Override
    public boolean equals(Object obj)
    {
        if ( obj == this ) {
            return true;
        }
        if ( obj instanceof SymbolReferenceNode) {
            return ObjectUtils.equals( this.identifier , ((SymbolReferenceNode) obj).identifier );
        }
        return false; 
    }
    
	@Override
	public SymbolReferenceNode reduce(ICompilationContext context) {
		return (SymbolReferenceNode) createCopy(false);
	}

	@Override
	public SymbolReferenceNode copySingleNode()
	{
		final SymbolReferenceNode result = new SymbolReferenceNode();
		result.identifier = identifier;
		return result;
	}

	@Override
	public Long getNumericValue(ISymbolTable table)
	{
		final ISymbol symbol = table.getSymbol( this.identifier );
		if ( symbol == null ) {
		    return null;
		}
		if ( !( symbol instanceof IValueSymbol ) ) {
			throw new RuntimeException("Internal error, symbol reference does not refer to a value symbol but to "+symbol);        	
		}
		return ((IValueSymbol) symbol).getValue( table );
	}
	
	@Override
	public String toString() {
		return identifier != null ? identifier.toString() : "<null identifier?>";
	}
	
    @Override
    public boolean supportsChildNodes() {
        return false;
    }

	@Override
	public Long calculate(ISymbolTable symbolTable) 
	{
		return getNumericValue( symbolTable );
	}	
}
