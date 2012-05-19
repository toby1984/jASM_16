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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.ArrayUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressingMode;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.OperandNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.SimpleFileObjectCodeWriterFactory;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.utils.Misc;

public class InstructionNodeTest extends TestHelper
{
	
	public void testDetermineAddressingModeWithLabel() throws Exception {
		
		ICompilationUnit unit = compile( "    SET a , label\n"+
		         ":label .word 0x1234" );
		
		assertFalse( unit.getAST().hasErrors() );
		
		StatementNode stmt1 = (StatementNode) unit.getAST().child(0);
		InstructionNode instr = (InstructionNode) stmt1.child(0);
		
		assertEquals( AddressingMode.REGISTER , instr.getOperand( 0 ).getAddressingMode() );
		assertEquals( AddressingMode.IMMEDIATE, instr.getOperand( 1 ).getAddressingMode() );
	}
	
	public void testDetermineAddressingModeWithLabelExpression1() throws Exception {
		
		ICompilationUnit unit = compile( "    SET a , label+10\n"+
		         ":label .word 0x1234" );
		
		assertFalse( unit.getAST().hasErrors() );
		
		StatementNode stmt1 = (StatementNode) unit.getAST().child(0);
		InstructionNode instr = (InstructionNode) stmt1.child(0);
		
		assertEquals( AddressingMode.REGISTER , instr.getOperand( 0 ).getAddressingMode() );
		assertEquals( AddressingMode.IMMEDIATE, instr.getOperand( 1 ).getAddressingMode() );
	}	
	
	
	public void testDetermineAddressingModeWithLabelExpression2() throws Exception {
		
		ICompilationUnit unit = compile( "    SET PC , 10+label\n"+
		         ":label .word 0x1234" );
		
		assertFalse( unit.getAST().hasErrors() );
		
		StatementNode stmt1 = (StatementNode) unit.getAST().child(0);
		InstructionNode instr = (InstructionNode) stmt1.child(0);
		
		assertEquals( AddressingMode.REGISTER , instr.getOperand( 0 ).getAddressingMode() );
		assertEquals( AddressingMode.IMMEDIATE, instr.getOperand( 1 ).getAddressingMode() );
	}		
	
	public void testDetermineAddressingMode() throws ParseException 
	{
		InstructionNode instruction = compileInstruction("SET PUSH , 10");
		assertEquals(AddressingMode.INDIRECT_REGISTER_PREDECREMENT, instruction.getOperand( 0 ).getAddressingMode() );
		assertEquals(AddressingMode.IMMEDIATE, instruction.getOperand( 1 ).getAddressingMode() );	
		
		 instruction = compileInstruction("SET a , b");
		assertEquals(AddressingMode.REGISTER , instruction.getOperand( 0 ).getAddressingMode() );
		assertEquals(AddressingMode.REGISTER, instruction.getOperand( 1 ).getAddressingMode() );	
		
		instruction = compileInstruction("SET [a] , 10");
		assertEquals(AddressingMode.INDIRECT_REGISTER , instruction.getOperand( 0 ).getAddressingMode() );
		assertEquals(AddressingMode.IMMEDIATE, instruction.getOperand( 1 ).getAddressingMode() );	
		
		instruction = compileInstruction("SET [sp++] , 10");
		assertEquals(AddressingMode.INDIRECT_REGISTER_POSTINCREMENT , instruction.getOperand( 0 ).getAddressingMode() );
		assertEquals(AddressingMode.IMMEDIATE, instruction.getOperand( 1 ).getAddressingMode() );			
		
		instruction = compileInstruction("SET [0x2000+a] , 10");
		assertEquals(AddressingMode.INDIRECT_REGISTER_OFFSET, instruction.getOperand( 0 ).getAddressingMode() );
		assertEquals(AddressingMode.IMMEDIATE, instruction.getOperand( 1 ).getAddressingMode() );	
		
		instruction = compileInstruction("SET [0x2000] , 10");
		assertEquals(AddressingMode.INDIRECT, instruction.getOperand( 0 ).getAddressingMode() );
		assertEquals(AddressingMode.IMMEDIATE, instruction.getOperand( 1 ).getAddressingMode() );	
		
		instruction = compileInstruction("SET a, 10");
		assertEquals(AddressingMode.REGISTER, instruction.getOperand( 0 ).getAddressingMode() );
		assertEquals(AddressingMode.IMMEDIATE, instruction.getOperand( 1 ).getAddressingMode() );			
	}
	
	private InstructionNode compileInstruction(String source) {
		ASTNode node = new InstructionNode().parse(createParseContext( source ) );
		
		if ( node.hasErrors() ) {
			ASTUtils.printAST( node );
			Misc.printCompilationErrors( CompilationUnit.createInstance("string" , source ) , source , true );
		}
		assertFalse( node.hasErrors() );
		assertEquals( InstructionNode.class , node.getClass() );
		return (InstructionNode) node;
	}
	
	public void testTextRegion() throws Exception {
		final String source = "SET [0x2000+I], [A]";
		
		ICompilationUnit unit = compile( source );
		assertFalse( unit.hasErrors() );
		
		AST ast = unit.getAST();
		
		final InstructionNode instruction = ((StatementNode) unit.getAST().child(0)).getInstructionNode();
		ASTUtils.printAST( instruction );
		
		assertSourceCode( source , instruction );
		
		// operand A checks
		ASTNode operandA = ast.getNodeInRange( 4 );
		assertNotNull( operandA );
		assertEquals( OperandNode.class , operandA.getClass() );
		
		assertSourceCode( "[0x2000+I]" , source , operandA );
		
		// operand B checks
		ASTNode operandB = ast.getNodeInRange( 16 );
		assertNotNull( operandB );
		assertEquals( OperandNode.class , operandB.getClass() );	
		
		assertSourceCode( "[A]" , source , operandB );
		
		assertSourceCode( source , unit.getAST() );
		assertSourceCode( source , instruction );
	}
	
    public void testConditionalExpressionsParsing() throws Exception 
    {
        assertCompiles(":test SET A , 3 > 2");
        assertCompiles(":test SET [ 3 > 2 ] , 1 == 2");
        assertDoesNotCompile(":test SET [ a < 3 ] , 1 == 2");        
    }
	
    public void testAddressingModesParsing() throws Exception {

    	assertCompiles("SET PICK 3,a");
    	assertCompiles("SET a,PICK 3");  
    	
    	assertCompiles("SET [SP+3],a");
    	assertCompiles("label: SET [SP+3+4+label],a");
    	assertCompiles("label: SET PICK 3+4,a");       	
    	assertCompiles("SET a,[SP+3]");  
    	
        assertCompiles(":test SET A, 2+test");
        
        assertDoesNotCompile(":sub SET a,1") ; // sub is an opcode !
        assertDoesNotCompile(":x SET a,1") ; // x is a register !        
        
        assertDoesNotCompile("SET [A] , [--SP]");    
        
    	assertCompiles("ifn A, \"=\"");
    	assertDoesNotCompile("SET a , 1+a " );
    	
    	assertCompiles("SET [10+A],10");    	
    	assertCompiles("SET [10+A+10],10");
    	
    	assertCompiles("SET [0x10],3");
    	
    	assertCompiles("SET A , 10 ");
    	assertDoesNotCompile("SET [A++] , 10 ");
    	assertDoesNotCompile("SET [B] , [A++]");
    	
    	assertDoesNotCompile("SET [--A] , 10 ");
    	assertDoesNotCompile("SET [B] , [--A]");    	
    	
    	assertCompiles("SET PC,4");

    	assertCompiles("SET [A] , 10 ");
    	assertCompiles("SET [A+10] , 10 ");
    	
    	assertDoesNotCompile("SET [A+A],10)");
    	assertDoesNotCompile("SET [1+A+2+A],10)");

    	assertCompiles("SET [A+10],10");
    	
    	assertDoesNotCompile("SET 10,A");
    	assertDoesNotCompile("SET 10,[A]");
    	assertDoesNotCompile("SET 10,[A+10]");
    	assertDoesNotCompile("SET 10,[10+A]");    
    	
    	assertCompiles("SET [A] , [B] ");
    	assertCompiles("SET [A+5] , [b+10] "); 
    	assertCompiles("SET [A+10] , [b+5] "); 
    	assertCompiles("SET [A+10] , [B+10] ");
    	
    	assertCompiles("SET [6+A] , [6+b] ");
        assertDoesNotCompile("SET [A++] , 1");   
        assertDoesNotCompile("SET [--A] , 1");
        assertCompiles("SET [--SP] , 1");
        
    	assertCompiles("SET PUSH, [A]"); // PUSH      
    	assertDoesNotCompile("SET [A] , PUSH "); // PUSH    	
    	assertDoesNotCompile("SET [A] , [--SP] "); // PUSH
    	
    	assertCompiles("SET [A], PEEK "); // PEEK   
    	assertCompiles("SET PEEK , [A] "); // PEEK    	
    	assertCompiles("SET [SP] , [A] "); // PEEK
    	
    	assertCompiles("SET PC,POP");    	
    	assertCompiles("SET [A] , POP"); // POP        	
    	assertDoesNotCompile("SET POP , [A] "); // POP    	
    	assertDoesNotCompile("SET [SP++] , [A] "); // POP    
    }
    
    public void testPush() throws Exception {

        final String source = "SET PUSH,10";

        final IParseContext context = createParseContext( source );

        final ASTNode result = new InstructionNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;
        
        assertEquals( Register.SP , instruction.getOperand( 0 ).getRegister() );
        assertEquals( AddressingMode.INDIRECT_REGISTER_PREDECREMENT , instruction.getOperand( 0 ).getAddressingMode());
        
        assertSourceCode( "SET PUSH,10" , result );
    }
    
    public void testPop() throws Exception {

        final String source = "SET a,pop";

        final IParseContext context = createParseContext( source );

        final ASTNode result = new InstructionNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;
        
        assertEquals( Register.SP , instruction.getOperand( 1 ).getRegister() );
        assertEquals( AddressingMode.INDIRECT_REGISTER_POSTINCREMENT , instruction.getOperand( 1 ).getAddressingMode());
        
        assertSourceCode( "SET a,pop" , result );
    }  
    
    public void testPop2() throws Exception {
        assertDoesNotCompile("SET POP,o");
    }     
    
    public void testNoOperandInlining() throws Exception {

        final String source = "SET [0x1000], 0x20";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);        
        final IParseContext context = createParseContext( unit );

        final ASTNode result = new InstructionNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;
        resolveSymbols( unit , instruction );
        final int size = instruction.getSizeInBytes(0);
        assertEquals( 6 , size );
        assertSourceCode( "SET [0x1000], 0x20" , result );
    }   
    
    public void testOperandInlining1() throws Exception {

        final String source = "SET [0x1000], 0x1e";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);        
        final IParseContext context = createParseContext( unit );

        final ASTNode result = new InstructionNode().parse( context );
        
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;
        resolveSymbols( unit , instruction );
        
        final int size = instruction.getSizeInBytes(0);
        assertEquals( 4 , size );
        assertSourceCode( "SET [0x1000], 0x1f" , result );
    }   
    
    public void testOperandInlining2() throws Exception {

        final String source = "SET PC, 30";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);        
        final IParseContext context = createParseContext( unit );

        final ASTNode result = new InstructionNode().parse( context );
        
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;
        resolveSymbols( unit , instruction );
        
        final int size = instruction.getSizeInBytes(0);
        assertEquals( 2 , size );
        assertSourceCode( "SET PC, 30" , result );
    }  
    
    public void testOperandInlining3() throws Exception {

        final String source = "SET PC, -1";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);        
        final IParseContext context = createParseContext( unit );

        final ASTNode result = new InstructionNode().parse( context );
        
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;
        resolveSymbols( unit , instruction );
        
        final int size = instruction.getSizeInBytes(0);
        assertEquals( 2 , size );
        assertSourceCode( "SET PC, -1" , result );
    } 
    
    public void testExtendedInstructionParsing() throws Exception {

        final String source = "JSR 0x1000";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);        
        final IParseContext context = createParseContext( unit );

        final ASTNode result = new InstructionNode().parse( context );
        
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;
        resolveSymbols( unit , instruction );
        
        final int size = instruction.getSizeInBytes(0);
        assertEquals( 4 , size );
        assertSourceCode( "JSR 0x1000" , result );
    }     
    
    public void testPeek() throws Exception {

        final String source = "SET a,peek";

        final IParseContext context = createParseContext( source );

        final ASTNode result = new InstructionNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;
        
        assertEquals( Register.SP , instruction.getOperand( 1 ).getRegister() );
        assertEquals( AddressingMode.INDIRECT_REGISTER , instruction.getOperand( 1 ).getAddressingMode());
        
        assertSourceCode( "SET a,peek" , result );
    }     
    
    public void testParseNestedImmediateExpression() throws Exception {

        final String source = "SET I, 4+5*3";

        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );

        final IParseContext context = createParseContext( source );

        final ASTNode result = new InstructionNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InstructionNode.class , result.getClass() );
        
        final InstructionNode instruction = (InstructionNode) result;

        final AtomicReference<byte[]> objcode = new AtomicReference<byte[]>();
        
        final IObjectCodeWriter writer = new IObjectCodeWriter() {
			
			
			public void close() throws IOException { }
			
			
			public void writeObjectCode(byte[] data, int offset, int length)
					throws IOException 
			{
				objcode.set( ArrayUtils.subarray( data , offset , offset+length ) );
			}
			
			
			public void writeObjectCode(byte[] data) throws IOException {
				writeObjectCode( data ,0,data.length );
			}
			
			
			public void deleteOutput() throws IOException {
				// TODO Auto-generated method stub
				
			}

            
            public Address getCurrentWriteOffset()
            {
                return Address.ZERO;
            }
            
            
            public Address getFirstWriteOffset()
            {
                return Address.ZERO;
            }            

            
            public void advanceToWriteOffset(Address offset) throws IOException
            {
                throw new UnsupportedOperationException("Not implemented");                
            }
		};
		
		final IObjectCodeWriterFactory factory = new SimpleFileObjectCodeWriterFactory() {
		    
		    protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context)
		    {
		        return writer;
		    }
		};
		
		final ICompilationContext compContext = createCompilationContext( unit );
		
		final OperandNode operand = instruction.getOperand( 1 );
		final TermNode oldExpression = (TermNode) operand.child(0);
		final TermNode newExpression = oldExpression.reduce( compContext );
		if ( newExpression != oldExpression ) {
		    operand.setChild( 0 , newExpression );
		}
		instruction.symbolsResolved( compContext );
		instruction.writeObjectCode( writer, compContext );
        assertNotNull( objcode.get() );
        assertEquals( source , toSourceCode( result , source ) );
    }     
}
