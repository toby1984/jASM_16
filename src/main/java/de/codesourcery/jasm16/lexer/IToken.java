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

import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * A token in the input stream.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IToken extends ITextRegion {

	/**
	 * Returns the string contents of this token.
	 * 
	 * @return string contents, may be an empty string but never <code>null</code>.
	 * @see #getLength()
	 */
	public String getContents();
	
	/**
	 * Returns the length of this token's contents.
	 * @see #getContents()
	 */
	public int getLength();

	/**
	 * Returns the type of this token.
	 * 
	 * @return
	 */
	public TokenType getType();
	
	/**
	 * Check whether this token has a specific type.
	 * 
	 * @param type
	 * @return
	 */
	public boolean hasType(TokenType type);

	/**
	 * Returns the absolute parse starting offset of this token.
	 */
	public int getStartingOffset();
	
	/**
	 * Check whether this is the whitespace token.
	 * 
	 * @return
	 * @see TokenType#WHITESPACE
	 */
	public boolean isWhitespace();
	
	/**
	 * Check whether this is the end-of-line token.
	 * 
	 * @return
	 * @see TokenType#EOL
	 */
    public boolean isEOL();	
}
