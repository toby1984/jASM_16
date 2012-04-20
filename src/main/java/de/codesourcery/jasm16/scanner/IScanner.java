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
package de.codesourcery.jasm16.scanner;

import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.lexer.ILexer;

/**
 * Simple text scanner used by {@link ILexer} for tokenizing
 * the input stream.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IScanner {

	/**
	 * Check whether the scanner reached the
	 * end of file / end of input.
	 * @return
	 */
	public boolean eof();
	
	/**
	 * Peek at the next character in the input stream.
	 * 
	 * @return
	 * @throws EOFException
	 */
	public char peek() throws EOFException;
	
	/**
	 * Read the next character from the input stream.
	 * 
	 * @return
	 * @throws EOFException
	 */
	public char read() throws EOFException;
	
	/**
	 * Returns the index the next call to {@link #read()}
	 * or {@link #peek()} will read from.
	 * 
	 * @return
	 * @see #setCurrentParseIndex(int)
	 */
	public int currentParseIndex();
	
	/**
	 * Sets the current parse index.
	 * 
	 * @param index
	 * @see #currentParseIndex()
	 */
	public void setCurrentParseIndex(int index);
}
