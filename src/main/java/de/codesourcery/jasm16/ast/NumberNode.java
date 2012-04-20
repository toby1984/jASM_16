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

import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.NumberToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.utils.ITextRange;
import de.codesourcery.jasm16.utils.TextRange;

/**
 * An AST node that represents a number literal.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class NumberNode extends ConstantValueNode
{
    private long value;
    
    public NumberNode() {
    }
    
    public NumberNode(long value,ITextRange range) 
    {
    	super( new TextRange( range ) );
    	this.value = value;
    }
    
    @Override
	protected NumberNode parseInternal(IParseContext context) throws ParseException
    {
    	final NumberToken token = (NumberToken) context.read( "Expected a number",TokenType.NUMBER_LITERAL );
   		this.value = token.getValue();
   		mergeWithAllTokensTextRange( token );
    	return this;
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if ( obj == this ) {
            return true;
        }
        if ( obj instanceof NumberNode) {
            return value == ((NumberNode) obj).value;
        }
        return false; 
    }
    
	public int getAsByte() throws ParseException 
	{
		if ( value < 0 | value > 255 ) {
			throw new ParseException("8-bit value expected but found: "+value,getTextRange() );
		}
		return (int) value;
	}
	
	public long getValue() {
		return value;
	}
	
    public int getAsWord() throws ParseException 
    {
		if ( value < 0 | value > 65535 ) {
			throw new ParseException("16-bit value expected but found: "+value,getTextRange());
		}
        return (int) value;
    }

	@Override
	public NumberNode reduce(ICompilationContext context) {
		return (NumberNode) createCopy( false );
	}
	
	@Override
	public boolean isNumberLiteral() {
		return true;
	}

    @Override
    public NumberNode copySingleNode()
    {
    	final NumberNode result = new NumberNode();
    	result.value = value;
    	return result;
    }

    @Override
    public Long getNumericValue(ISymbolTable context)
    {
    	return value;
    }
    
    @Override
    public String toString() {
    	return Long.toString( value );
    }
    
    @Override
    public boolean supportsChildNodes() {
        return false;
    }    
}
