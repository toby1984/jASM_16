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

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.parser.TestHelper;

public class EquationNodeTest extends TestHelper {

	public void testCyclicExpression() throws Exception {
		
		final String source = ".equ value1 value2\n" +
				".equ value2 value3\n" +
				".equ value3 value1";

		ICompilationUnit unit = compile( source );
		assertTrue( unit.hasErrors() );
	}
	
	public void testCyclicExpression2() throws Exception {
		
		final String source = ".equ value1 value2\n" +
				".equ value2 value1\n";

		ICompilationUnit unit = compile( source );
		assertTrue( unit.hasErrors() );
	}	
	
	public void testParseNumberLiteralExpression() throws Exception {
		
		final String source = ".equ value 10";

		final ICompilationUnit unit = CompilationUnit.createInstance("string",source);
		final IParseContext context = createParseContext(unit);
		
		final ASTNode result = new StatementNode().parse( context );
		
		assertFalse( unit.hasErrors() );

		ASTUtils.printAST( result );
		
		final ISymbol symbol = context.getSymbolTable().getSymbol( new Identifier("value" ) );
		if ( symbol == null ) {
			throw new IllegalArgumentException("symbol must not be NULL");
		}
		assertEquals( Equation.class , symbol.getClass() );
		assertEquals( Long.valueOf(10) , ((Equation) symbol).getValue( symbolTable ) );
	}
	
	public void testParseSimpleExpression() throws Exception {
		
		final String source = ".equ value 1+3*5+4";

		final ICompilationUnit unit = CompilationUnit.createInstance("string",source);
		final IParseContext context = createParseContext(unit);
		
		final ASTNode result = new StatementNode().parse( context );
		
		assertFalse( unit.hasErrors() );

		ASTUtils.printAST( result );
		
		final ISymbol symbol = context.getSymbolTable().getSymbol( new Identifier("value" ) );
		if ( symbol == null ) {
			throw new IllegalArgumentException("symbol must not be NULL");
		}
		assertEquals( Equation.class , symbol.getClass() );
		assertEquals( Long.valueOf(20) , ((Equation) symbol).getValue( symbolTable ) );
	}	
	
	public void testParseExpressionWithLabelReference() throws Exception {
		
		final String source = "SET A,1\n" +
							  "label: \n"+
							   ".equ value 1+label";

		ICompilationUnit unit = compile( source );
		
		assertFalse( unit.hasErrors() );

		final ISymbol symbol = symbolTable.getSymbol( new Identifier("value" ) );
		assertNotNull( symbol );
		assertEquals( Equation.class , symbol.getClass() );
		assertEquals( Long.valueOf(2) , ((Equation) symbol).getValue( symbolTable ) );
	}	
}
