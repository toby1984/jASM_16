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
package de.codesourcery.jasm16.parser;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.ICompilationUnit;

public class ASTTest extends TestHelper {

	public void testValidOrg() throws Exception {
		
		final String source = ".org 0x2000";
		
		ICompilationUnit unit = compile( source );
		assertFalse( unit.hasErrors() );
		assertEquals( Address.valueOf( 0x2000 ) , unit.getObjectCodeStartOffset() );
	}
	
	public void testValidOrg2() throws Exception {
		
		final String source = ".org 0x2000\n" +
				".org 0x2001";
		
		ICompilationUnit unit = compile( source );
		assertFalse( unit.hasErrors() );
		assertEquals( Address.valueOf( 0x2000 ) , unit.getObjectCodeStartOffset() );
	}	
	public void testInvalidOrg1() throws Exception {
		
		final String source = ".org 0x2000\n" +
				".org 0x2000";
		
		ICompilationUnit unit = compile( source );
		assertTrue( unit.hasErrors() );
		assertEquals( Address.valueOf( 0x2000 ) , unit.getObjectCodeStartOffset() );
	}		
	
	public void testInvalidOrg2() throws Exception {
		
		final String source = ".org 0x2000\n" +
				".org 0x1000";
		
		ICompilationUnit unit = compile( source );
		assertTrue( unit.hasErrors() );
		assertEquals( Address.valueOf( 0x2000 ) , unit.getObjectCodeStartOffset() );
	}	
}
