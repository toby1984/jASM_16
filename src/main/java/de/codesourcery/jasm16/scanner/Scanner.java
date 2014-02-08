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

public class Scanner implements IScanner {

	private final String input;
	private int currentIndex = 0;
	
	public Scanner(final String input) 
	{
		this.input = input;
	}
	
	@Override
	public boolean eof() {
		return currentIndex >= input.length();
	}

	@Override
	public char peek() throws EOFException
	{
		if ( eof() ) {
			throw new EOFException("End of input reached",currentIndex);
		}
		return input.charAt(currentIndex);
	}

	@Override
	public char read() throws EOFException {
		if ( eof() ) {
			throw new EOFException("End of input reached",currentIndex);
		}
		return input.charAt(currentIndex++);		
	}

	@Override
	public int currentParseIndex() {
		return currentIndex;
	}

	@Override
	public void setCurrentParseIndex(int index) {
		if ( index < 0 ) 
		{
			throw new IllegalArgumentException("Invalid index "+index);
		}
		this.currentIndex = index;
	}

    @Override
    public String toString()
    {
        return eof() ? "Scanner is at EOF" : ""+peek();
    }	
}
