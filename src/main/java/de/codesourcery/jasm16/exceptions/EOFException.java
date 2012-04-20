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
package de.codesourcery.jasm16.exceptions;

import de.codesourcery.jasm16.lexer.ILexer;

/**
 * Thrown by {@link ILexer} methods when an unexpected EOF is encountered.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class EOFException extends RuntimeException {

	private final int errorOffset;
	
	public EOFException(String s, int errorOffset) 
	{
		super(s);
		this.errorOffset = errorOffset;
	}
	
	public int getErrorOffset() {
		return errorOffset;
	}
}
