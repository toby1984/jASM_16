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

import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * An AST node that represents a character literal (characters enclosed by 
 * {@link TokenType#STRING_DELIMITER}.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class CharacterLiteralNode extends ConstantValueNode {

	public static final int UNLIMITED_LENGTH = -1;
	
	private final int maxLength;
	private String value;
	private byte[] bytes;
	
	public CharacterLiteralNode() {
		maxLength = UNLIMITED_LENGTH;
	}
	
	public CharacterLiteralNode(int maxLength) 
	{
		if ( maxLength <= 0 && maxLength != UNLIMITED_LENGTH ) {
			throw new IllegalArgumentException("Invalid max. length: "+maxLength);
		}
		this.maxLength = maxLength;
	}	
	
	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
	    if ( ! context.peek().hasType( TokenType.STRING_DELIMITER ) ) {
	        throw new ParseException("Expected a character literal but string delimiter is missing",context.currentParseIndex(),1);
	    }
	    
	    TextRegion region = new TextRegion( context.currentParseIndex() , 0 );
	    
	    value = context.parseString( region );
	    mergeWithAllTokensTextRegion( region );
	    
        if ( maxLength != UNLIMITED_LENGTH && value.length() > maxLength ) {
            context.addCompilationError( "String literal too long, expected at most "+maxLength+" characters",this);
        }                
        this.bytes = toByteArray( getBytes() );
        return this;
	}
	
	private byte[] toByteArray(List<Integer> data) {
		final byte[] result = new byte[data.size()];
		int index = 0;
		for ( Integer value : data ) {
			result[index++] = (byte) ( value & 0xff);
		}
		return result;
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
	        if ( value < 0 || value > 255 ) 
	        {
	        	
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
	protected CharacterLiteralNode copySingleNode()
    {
    	final CharacterLiteralNode result=new CharacterLiteralNode(this.maxLength);
    	result.value = value;
    	if ( this.bytes != null ) {
    		result.bytes = new byte[ this.bytes.length ];
    		System.arraycopy( this.bytes , 0 , result.bytes , 0 , this.bytes.length );
    	}
    	return result;
    }

    @Override
    public boolean supportsChildNodes() {
        return false;
    }

	@Override
	public Long getNumericValue(ISymbolTable symbolTable) 
	{
		return calculate( symbolTable );
	}

	@Override
	public Long calculate(ISymbolTable symbolTable) {
		if ( this.bytes == null || this.bytes.length > 2 ) {
			return null;
		}
		final int hi = bytes[0] & 0xff;
		final int lo = bytes[1] & 0xff;
		return (long) (hi << 8 ) | lo;
	}

	@Override
	public TermNode reduce(ICompilationContext context) {
		return (TermNode) createCopy( true );
	}    
}
