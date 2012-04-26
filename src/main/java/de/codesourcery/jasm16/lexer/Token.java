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
package de.codesourcery.jasm16.lexer;

import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Generic {@link IToken} implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class Token extends TextRegion implements IToken {

	private final TokenType type;
	private final String contents;
	
	public Token(TokenType type , String contents, int parseStartOffset) 
	{
	    super( parseStartOffset , contents.length() );
		this.type = type;
		this.contents = contents;
	}

	@Override
	public TokenType getType() {
		return type;
	}
	
	@Override
	public boolean hasType(TokenType t) {
		return t.equals( getType() );
	}
	
	@Override
	public String getContents() {
		return contents;
	}

	@Override
	public String toString() {
		return ">"+contents+"< ("+super.toString()+" , "+type+")";
	}
	
	@Override
	public final boolean isWhitespace() {
        return hasType( TokenType.WHITESPACE );	    
	}
	
	@Override
    public final boolean isEOL() {
        return hasType( TokenType.EOL );     
    }	
	
}
