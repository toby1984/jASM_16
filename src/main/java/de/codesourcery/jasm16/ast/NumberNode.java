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
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Operator;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.NumberLiteralHelper;
import de.codesourcery.jasm16.utils.TextRegion;

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
    
    public NumberNode(long value,ITextRegion range) 
    {
    	super( new TextRegion( range ) );
    	this.value = value;
    }
    
    @Override
	protected NumberNode parseInternal(IParseContext context) throws ParseException
    {
        final boolean isNegativeNumber;
        final IToken nextToken = context.peek();
        
        if ( ! nextToken.hasType( TokenType.NUMBER_LITERAL) ) 
        {
            if ( ! nextToken.hasType( TokenType.OPERATOR) || Operator.fromString( nextToken.getContents() ) != Operator.MINUS )
            {
                throw new ParseException("Expected a number literal",nextToken);
            }
            mergeWithAllTokensTextRegion( context.read() );
            isNegativeNumber = true;
        } else {
            isNegativeNumber = false;
        }
        
        final int offset = context.currentParseIndex();
    	final IToken token = context.read( "Expected a number",TokenType.NUMBER_LITERAL );
    	try {
    	    this.value = NumberLiteralHelper.parseValue( token.getContents() );
    	    if ( isNegativeNumber ) {
    	        this.value = this.value * -1;
    	    }
    	} catch(ParseException e) {
    	    throw new ParseException( e.getMessage() , offset + e.getTextRegion().getStartingOffset() , e.getTextRegion().getLength() );
    	}
   		mergeWithAllTokensTextRegion( token );
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
		if ( value < Byte.MIN_VALUE | value > 255 ) {
			throw new ParseException("8-bit value expected but found: "+value,getTextRegion() );
		}
		return (int) value;
	}
	
	public long getValue() {
		return value;
	}
	
    public int getAsWord() throws ParseException 
    {
		if ( value < Short.MIN_VALUE || value > 65535 ) {
			throw new ParseException("16-bit value expected but found: "+value,getTextRegion());
		}
        return (int) value;
    }
    
    public void convertToNegativeNumber() 
    {
         value = value * -1;
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
	protected NumberNode copySingleNode()
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

	@Override
	public Long calculate(ISymbolTable symbolTable) {
		return value;
	}    
}
