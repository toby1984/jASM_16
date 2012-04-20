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

import junit.framework.TestCase;
import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.scanner.Scanner;

public class ScannerTest extends TestCase {

	public void testReadEmptyString() {
		
		Scanner scanner = new Scanner("");
		
		assertTrue( scanner.eof() );
		
		try {
			scanner.peek();
		} catch(EOFException e) {
			// ok
		}
		
		try {
			scanner.read();
		} catch(EOFException e) {
			// ok
		}
	}
	
	public void testSetCurrentParseIndex() {
	       
        final Scanner scanner = new Scanner("012345");
        scanner.setCurrentParseIndex( 3);
        assertEquals( '3' , scanner.read() );
	}
	
	public void testReadOneCharacterString() {
		
		Scanner scanner = new Scanner("x");
		
		assertFalse( scanner.eof() );
		assertEquals( 0 , scanner.currentParseIndex() );
		assertEquals( 'x' , scanner.peek() );
		assertEquals( 0 , scanner.currentParseIndex() );
		assertEquals( 'x' , scanner.read() );
		assertEquals( 1 , scanner.currentParseIndex() );
		assertTrue( scanner.eof() );
	}	
	
	public void testReadTwoCharacterString() {
		
		Scanner scanner = new Scanner("xy");
		
		assertFalse( scanner.eof() );
		assertEquals( 0 , scanner.currentParseIndex() );
		assertEquals( 'x' , scanner.peek() );
		assertEquals( 0 , scanner.currentParseIndex() );
		assertEquals( 'x' , scanner.read() );
		assertEquals( 1 , scanner.currentParseIndex() );
		
		assertFalse( scanner.eof() );
		assertEquals( 'y' , scanner.peek() );
		assertEquals( 1 , scanner.currentParseIndex() );
		assertEquals( 'y' , scanner.read() );
		assertEquals( 2 , scanner.currentParseIndex() );		
		
		assertTrue( scanner.eof() );
	}	
}
