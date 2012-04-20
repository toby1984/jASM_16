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

import java.io.IOException;
import java.util.Collections;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.Compiler;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.io.NullObjectCodeWriterFactory;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.DebugCompilationListener;

public class AddressCalculationTest extends TestHelper 
{
	
	public void testLongForm() throws Exception  {

		final String source = "SET PC , label\n"+
		                      "label: .word 0x1234";
		
		ICompilationUnit unit = CompilationUnit.createInstance( "string" , source );
		
		final ICompilationListener listener = new DebugCompilationListener(true);
		
		final Compiler compiler = new Compiler();
		compiler.setObjectCodeWriterFactory( new NullObjectCodeWriterFactory() );
		compiler.setResourceResolver( RESOURCE_RESOLVER );
		
		final ICompilerPhase debugPhase = new CompilerPhase("debug-phase") {

			@Override
			protected void run(ICompilationUnit unit, ICompilationContext context) throws IOException 
			{
				Label label = null;
				try {
					label = (Label) context.getSymbolTable().getSymbol( new Identifier("label") );
				} catch (ParseException e) {
					fail( e.getMessage() );
				}
				System.out.println("Symbol-table: "+context.getSymbolTable() );
				
				assertEquals( "Label address mismatch" , Address.valueOf( 1 ) , label.getAddress() );  
			}
			
		};
		compiler.insertCompilerPhaseAfter( debugPhase , ICompilerPhase.PHASE_GENERATE_CODE );
		
		compiler.compile( Collections.singletonList( unit ) , listener );
		
		assertNotNull( unit.getAST() );
		assertFalse( unit.hasErrors() );
	}
	
}
