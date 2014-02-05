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
package de.codesourcery.jasm16.compiler;

import junit.framework.TestCase;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.TextRegion;

public class SymbolTableTest extends TestCase {

	private SymbolTable table;
	private ICompilationUnit unit;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		table = new SymbolTable("SymbolTableTest");
		unit = CompilationUnit.createInstance("id" , "test" );
	}
	
	public void testStoreGlobaleSymbol() throws ParseException {
		
		Label label = new Label(  unit , new TextRegion(0,4) , new Identifier( "test" ) , null );
		table.defineSymbol( label );
		assertTrue( table.containsSymbol( label.getName() , null ) );
	}
	
	public void testStoreLocalSymbolWithoutGlobalFails() throws ParseException {
		
		Label globalLabel = new Label(  unit , new TextRegion(0,4) , new Identifier( "globalTest" ) , null );
		Label localLabel = new Label(  unit , new TextRegion(0,4) , new Identifier( "localTest" ) , globalLabel );
		try {
			table.defineSymbol( localLabel );
			fail("Should've failed");
		} catch(Exception e) {
			// ok
		}
	}	
	
	public void testStoreLocalSymbol() throws ParseException {
		
		Label globalLabel = new Label(  unit , new TextRegion(0,4) , new Identifier( "globalTest" ) , null );
		table.defineSymbol( globalLabel );
		Label localLabel = new Label(  unit , new TextRegion(0,4) , new Identifier( "localTest" ) , globalLabel );
		table.defineSymbol( localLabel );
		
		assertTrue( table.containsSymbol( localLabel.getName() , globalLabel ) );
	}		
}
