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

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

/**
 * An AST node that represents a character literal (characters enclosed by 
 * {@link TokenType#STRING_DELIMITER}.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class CharacterLiteralNode extends ASTNode {

	private String value;
	
	public CharacterLiteralNode() {
	}
	
	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
	    mergeWithAllTokensTextRange( context.read("Expected a character literal but string delimiter is missing",TokenType.STRING_DELIMITER) );
	    
        final StringBuilder contents = new StringBuilder();
        while ( ! context.eof() ) 
        {
            IToken tok = context.peek();
            
            if ( tok.hasType( TokenType.EOL ) ) {
                throw new ParseException("Character literal lacks closing delimiter",getTextRange());                
            } 
            else if ( tok.hasType( TokenType.STRING_DELIMITER ) ) 
            {
            	mergeWithAllTokensTextRange( context.read() );
                value = contents.toString();
                return this;
            } 
            mergeWithAllTokensTextRange( context.read() );
            contents.append( tok.getContents() );
        }
        throw new ParseException("Premature end of input, character literal lacks closing delimiter",getTextRange());            
	}
	
	public List<Integer> getBytes() throws ParseException 
	{
	    List<Integer>  result = new ArrayList<Integer>();
	    int offset = 0;
	    for ( char c : value.toCharArray() ) {
	        int value = c;
	        if ( value < 0 ) {
	            value += 256;
	        }
	        if ( value < 0 || value > 255 ) {
	            throw new ParseException("Invalid character '"+c+"' at index "+offset,offset,1);
	        }
	        /*
	         * Characters in DCPU are assumed to always require 16-bit with
	         * the first byte being used for foreground/background color and
	         * and second byte containing the ASCII code.
	         * 
	         * TODO: Check character literal conversion .... actual character conversion being used is unconfirmed yet...
	         * 
	         * http://stackoverflow.com/questions/10038816/is-dcpu-16-assembler-dat-with-a-string-supposed-to-generate-a-byte-or-word-per
	         */	        
	        result.add( 0 ); // no color flags
	        result.add( value );
	        offset++;
	    }
		return result;
	}

    @Override
    public CharacterLiteralNode copySingleNode()
    {
    	final CharacterLiteralNode result=new CharacterLiteralNode();
    	result.value = value;
    	return result;
    }

    @Override
    public boolean supportsChildNodes() {
        return false;
    }    
}
