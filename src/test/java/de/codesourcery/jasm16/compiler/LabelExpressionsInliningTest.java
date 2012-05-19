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

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.Misc;

public class LabelExpressionsInliningTest extends TestHelper {

	public void testSimpleInlining() throws Exception {
		
		final String source="label:        JSR label+1"; 

		final Compiler compiler = new Compiler();
		compiler.setCompilerOption( CompilerOption.DEBUG_MODE  , true );
		compiler.setObjectCodeWriterFactory( NOP_WRITER );
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		
		final ISymbolTable[] symbolTable = new ISymbolTable[1];
		compiler.insertCompilerPhaseAfter( new CompilerPhase("pry-on-symbol-table") {

			
			protected void run(ICompilationUnit unit,ICompilationContext context) throws IOException 
			{
				symbolTable[0] = context.getSymbolTable();
			}
		},ICompilerPhase.PHASE_GENERATE_CODE );
		
		compiler.compile( Collections.singletonList( unit ) , new CompilationListener() );
		
		Misc.printCompilationErrors( unit , source , true );
        assertSourceCode( source , unit.getAST() ); 
        
		assertFalse( unit.hasErrors() );
		assertNotNull( symbolTable[0] );
		
		final InstructionNode instruction = (InstructionNode) unit.getAST().child(0).child(1);
		
		final ASTNode valueNode = instruction.getOperand( 0 ).child( 0 );
		assertTrue( valueNode.getClass().getName() , valueNode instanceof TermNode );
		Long value = ((TermNode) valueNode).calculate( symbolTable[0] );
		
		assertEquals( "0001" , Misc.toHexString( value.intValue() ) );
	}
	
	public void testInliningWithForwardReference1() throws Exception {
		
		final String source="         JSR label+1 ; 0000\n" + 
				"         SET a,1 ; 0001\n" + 
				"         SET a,1 ; 0002\n" + 
				"         SET a,1 ; 0003\n" + 
				"         SET a,1 ; 0004\n" + 
				"         SET a,1 ; 0005\n" + 
				"         SET a,1 ; 0006\n" + 
				"         SET a,1 ; 0007\n" + 
				"         SET a,1 ; 0008\n" + 
				"         SET a,1 ; 0009\n" + 
				"         SET a,1 ; 000a\n" + 
				"         SET a,1 ; 000b\n" + 
				"         SET a,1 ; 000c\n" + 
				"         SET a,1 ; 000d\n" + 
				"         SET a,1 ; 000e\n" + 
				"         SET a,1 ; 000f\n" + 
				"         SET a,1 ; 0010\n" + 
				"         SET a,1 ; 0011\n" + 
				"         SET a,1 ; 0012\n" + 
				"         SET a,1 ; 0013\n" + 
				"         SET a,1 ; 0014\n" + 
				"         SET a,1 ; 0015\n" + 
				"         SET a,1 ; 0016\n" + 
				"         SET a,1 ; 0017\n" + 
				"         SET a,1 ; 0018\n" + 
				"         SET a,1 ; 0019\n" + 
				"         SET a,1 ; 001a\n" + 
				"         SET a,1 ; 001b\n" + 
				"         SET a,1 ; 001c\n" + 
				"         SET a,1 ; 001d\n" + 
				"         SET a,1 ; 001e\n" + 
				"label:   SET b,1 ; 001f";

		final Compiler compiler = new Compiler();
		compiler.setCompilerOption( CompilerOption.DEBUG_MODE  , true );
		compiler.setObjectCodeWriterFactory( NOP_WRITER );
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		
		final ISymbolTable[] symbolTable = new ISymbolTable[1];
		compiler.insertCompilerPhaseAfter( new CompilerPhase("pry-on-symbol-table") {

			
			protected void run(ICompilationUnit unit,ICompilationContext context) throws IOException 
			{
				symbolTable[0] = context.getSymbolTable();
			}
		},ICompilerPhase.PHASE_GENERATE_CODE );
		
		compiler.compile( Collections.singletonList( unit ) , new CompilationListener() );
		
		Misc.printCompilationErrors( unit , source , true );
        assertSourceCode( source , unit.getAST() ); 
        
		assertFalse( unit.hasErrors() );
		assertNotNull( symbolTable[0] );
		
		final InstructionNode instruction = (InstructionNode) unit.getAST().child(0).child(0);
		
		final ASTNode valueNode = instruction.getOperand( 0 ).child( 0 );
		assertTrue( valueNode.getClass().getName() , valueNode instanceof TermNode );
		Long value = ((TermNode) valueNode).calculate( symbolTable[0] );
		
		final Label label = (Label) symbolTable[0].getSymbol(new Identifier("label"));
		assertEquals(  label.getAddress().getValue()+1 , value.intValue() );
	}
	
	public void testInliningWithForwardReference2() throws Exception {
		
		final String source="         JSR label+1 ; 0000\n" + 
				"         SET a,1 ; 0001\n" + 
				"         SET a,1 ; 0002\n" + 
				"         SET a,1 ; 0003\n" + 
				"         SET a,1 ; 0004\n" + 
				"         SET a,1 ; 0005\n" + 
				"         SET a,1 ; 0006\n" + 
				"         SET a,1 ; 0007\n" + 
				"         SET a,1 ; 0008\n" + 
				"         SET a,1 ; 0009\n" + 
				"         SET a,1 ; 000a\n" + 
				"         SET a,1 ; 000b\n" + 
				"         SET a,1 ; 000c\n" + 
				"         SET a,1 ; 000d\n" + 
				"         SET a,1 ; 000e\n" + 
				"         SET a,1 ; 000f\n" + 
				"         SET a,1 ; 0010\n" + 
				"         SET a,1 ; 0011\n" + 
				"         SET a,1 ; 0012\n" + 
				"         SET a,1 ; 0013\n" + 
				"         SET a,1 ; 0014\n" + 
				"         SET a,1 ; 0015\n" + 
				"         SET a,1 ; 0016\n" + 
				"         SET a,1 ; 0017\n" + 
				"         SET a,1 ; 0018\n" + 
				"         SET a,1 ; 0019\n" + 
				"         SET a,1 ; 001a\n" + 
				"         SET a,1 ; 001b\n" + 
				"         SET a,1 ; 001c\n" + 
				"         SET a,1 ; 001d\n" + 
				"label:   SET b,1 ; 001e";

		final Compiler compiler = new Compiler();
		compiler.setCompilerOption( CompilerOption.DEBUG_MODE  , true );
		compiler.setObjectCodeWriterFactory( NOP_WRITER );
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		
		final ISymbolTable[] symbolTable = new ISymbolTable[1];
		compiler.insertCompilerPhaseAfter( new CompilerPhase("pry-on-symbol-table") {

			
			protected void run(ICompilationUnit unit,ICompilationContext context) throws IOException 
			{
				symbolTable[0] = context.getSymbolTable();
			}
		},ICompilerPhase.PHASE_GENERATE_CODE );
		
		compiler.compile( Collections.singletonList( unit ) , new CompilationListener() );
		
		Misc.printCompilationErrors( unit , source , true );
        assertSourceCode( source , unit.getAST() ); 
        
		assertFalse( unit.hasErrors() );
		assertNotNull( symbolTable[0] );
		
		final InstructionNode instruction = (InstructionNode) unit.getAST().child(0).child(0);
		
		final ASTNode valueNode = instruction.getOperand( 0 ).child( 0 );
		assertTrue( valueNode.getClass().getName() , valueNode instanceof TermNode );
		Long value = ((TermNode) valueNode).calculate( symbolTable[0] );
		
		final Label label = (Label) symbolTable[0].getSymbol(new Identifier("label"));
		assertEquals(  label.getAddress().getValue()+1 , value.intValue() );
	}	
}
