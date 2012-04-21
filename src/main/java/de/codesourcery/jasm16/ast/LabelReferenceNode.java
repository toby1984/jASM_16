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
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * An AST node that represents a reference to a label.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class LabelReferenceNode extends ConstantValueNode
{
	private Identifier identifier;

	public Identifier getIdentifier()
	{
		return identifier;
	}

	@Override
	protected LabelReferenceNode parseInternal(IParseContext context) throws ParseException
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
        if ( obj instanceof LabelReferenceNode) {
            return ObjectUtils.equals( this.identifier , ((LabelReferenceNode) obj).identifier );
        }
        return false; 
    }
    
	@Override
	public LabelReferenceNode reduce(ICompilationContext context) {
		return (LabelReferenceNode) createCopy(false);
	}

	@Override
	public LabelReferenceNode copySingleNode()
	{
		final LabelReferenceNode result = new LabelReferenceNode();
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
		if ( !( symbol instanceof Label ) ) {
			throw new RuntimeException("Internal error, label reference does not refer to a label but to "+symbol);        	
		}
		
		final Label label = (Label) symbol;
		return label.getAddress() != null ? Long.valueOf( label.getAddress().getValue() ) : null;
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
