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

import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.NumberNode;
import de.codesourcery.jasm16.ast.OperandNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.compiler.CompilationContext;
import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.Compiler;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.phases.FoldExpressionsPhase;
import de.codesourcery.jasm16.parser.Parser;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.TextRange;

public class FoldExpressionsPhaseTest extends TestHelper
{

    public void testFoldSimpleExpression() throws IOException 
    {
        final String source ="SET I, 4+5*3";
        
        ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        
        Compiler comp = new Compiler();
        comp.removeCompilerPhase( ICompilerPhase.PHASE_GENERATE_CODE );
        
        comp.setObjectCodeWriterFactory( NOP_WRITER );
        comp.compile( Collections.singletonList( unit ) , new CompilationListener() );
        
        assertFalse( unit.getAST().hasErrors() );
        ASTUtils.printAST( unit.getAST(), source ); 
        
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        new FoldExpressionsPhase().execute( Collections.singletonList( unit ) , symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS );
        
        System.out.println("----------------------------");
        ASTUtils.printAST( unit.getAST() , source );        
    }
    
    public void testFoldSimpleRegisterExpressionWithWhitespace2() throws IOException 
    {
        final String source =" SET I, [ 4 + 5 * 3+A ] ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        ICompilationContext compContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER, OPTIONS );
        
        AST ast = new Parser().parse( compContext );
        unit.setAST( ast );
        assertFalse( unit.getAST().hasErrors() );
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        new FoldExpressionsPhase().execute( Collections.singletonList( unit ) , symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS );
        
        ASTUtils.printAST( ast , source );
        
        assertEquals(StatementNode.class ,             ast.child(0).getClass() );
        assertEquals(InstructionNode.class ,           ast.child(0).child(0).getClass() );
        
        assertEquals(OperandNode.class ,               ast.child(0).child(0).child(0).getClass() );
        assertEquals(RegisterReferenceNode.class ,     ast.child(0).child(0).child(0).child(0).getClass() );        
        
        assertEquals(OperandNode.class ,               ast.child(0).child(0).child(1).getClass() );
        OperandNode op = (OperandNode) ast.child(0).child(0).child(1);
        
        assertTextRange( new TextRange(8,15), op.getTextRange() , source );
        
        assertEquals(OperatorNode.class   ,          ast.child(0).child(0).child(1).child(0).getClass() );  
        
        OperatorNode expr = (OperatorNode) ast.child(0).child(0).child(1).child(0);
        assertTextRange( new TextRange(9,13) , expr.getTextRange() , source );
        
        assertEquals(NumberNode.class   ,              ast.child(0).child(0).child(1).child(0).child(0).getClass() );           
        final NumberNode numberNode = (NumberNode) ast.child(0).child(0).child(1).child(0).child(0);
        assertEquals( 19L , numberNode.getValue() );        
    }     
    
    public void testFoldSimpleRegisterExpressionWithWhitespace() throws IOException 
    {
        final String source =" SET I, [ A + 4 + 5 * 3 ] ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        ICompilationContext compContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER, OPTIONS );
        
        AST ast = new Parser().parse( compContext );
        unit.setAST( ast );
        assertFalse( unit.getAST().hasErrors() );
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        ASTUtils.printAST( ast , source );
        
        new FoldExpressionsPhase().execute( Collections.singletonList( unit ) , symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS );
        
        assertEquals(StatementNode.class ,             ast.child(0).getClass() );
        assertEquals(InstructionNode.class ,           ast.child(0).child(0).getClass() );
        
        assertEquals(OperandNode.class ,               ast.child(0).child(0).child(0).getClass() );
        assertEquals(RegisterReferenceNode.class ,     ast.child(0).child(0).child(0).child(0).getClass() );        
        
        assertEquals(OperandNode.class ,               ast.child(0).child(0).child(1).getClass() );
        OperandNode op = (OperandNode) ast.child(0).child(0).child(1);
        
        assertTextRange( new TextRange(8,17), op.getTextRange() , source );
        
        assertEquals(OperatorNode.class   ,          ast.child(0).child(0).child(1).child(0).getClass() );  
        
        OperatorNode expr = (OperatorNode) ast.child(0).child(0).child(1).child(0);
        assertTextRange( new TextRange(9,15) , expr.getTextRange() , source );
        
        assertEquals(NumberNode.class   ,              ast.child(0).child(0).child(1).child(0).child(0).getClass() );           
        final NumberNode numberNode = (NumberNode) ast.child(0).child(0).child(1).child(0).child(0);
        assertEquals( 19L , numberNode.getValue() );        
    }    

    public void testFoldSimpleRegisterExpression2() throws IOException 
    {
        final String source ="SET I, [4+5*3+A]";
        
        ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        ICompilationContext compContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER, OPTIONS );
        
        AST ast = new Parser().parse( compContext );
        unit.setAST( ast );
        assertFalse( unit.getAST().hasErrors() );
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        new FoldExpressionsPhase().execute( Collections.singletonList( unit ) , symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS );
        ASTUtils.printAST( ast );
        
        assertEquals(StatementNode.class ,             ast.child(0).getClass() );
        assertEquals(InstructionNode.class ,           ast.child(0).child(0).getClass() );
        assertEquals(OperandNode.class ,               ast.child(0).child(0).child(0).getClass() );
        assertEquals(RegisterReferenceNode.class ,     ast.child(0).child(0).child(0).child(0).getClass() );        
        
        assertEquals(OperandNode.class ,               ast.child(0).child(0).child(1).getClass() );
        assertEquals(OperatorNode.class   ,            ast.child(0).child(0).child(1).child(0).getClass() );    
        
        assertEquals(NumberNode.class   ,              ast.child(0).child(0).child(1).child(0).child(0).getClass() );   
        assertEquals( 19L , ((NumberNode)              ast.child(0).child(0).child(1).child(0).child(0)).getValue() );   
        
        assertEquals(RegisterReferenceNode.class   ,   ast.child(0).child(0).child(1).child(0).child(1).getClass() );   
        assertEquals( Register.A  ,   ((RegisterReferenceNode) ast.child(0).child(0).child(1).child(0).child(1) ).getRegister() );        
    }     
    
    public void testFoldSimpleRegisterExpression3() throws IOException 
    {
        final String source ="SET I, [4+5*3+A+19]";
        
        // 4 + 15 + a + 19 = 4+15+19+A = 19+19+a = 38
        
        ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        ICompilationContext compContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER, OPTIONS );
        
        AST ast = new Parser().parse( compContext );
        unit.setAST( ast );
        assertFalse( unit.getAST().hasErrors() );
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        new FoldExpressionsPhase().execute( Collections.singletonList( unit ) , symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS );
        ASTUtils.printAST( ast );
        
        assertEquals(StatementNode.class ,             ast.child(0).getClass() );
        assertEquals(InstructionNode.class ,           ast.child(0).child(0).getClass() );
        
        assertEquals(OperandNode.class ,               ast.child(0).child(0).child(0).getClass() );
        assertEquals(RegisterReferenceNode.class ,     ast.child(0).child(0).child(0).child(0).getClass() );        
        
        assertEquals(OperandNode.class ,               ast.child(0).child(0).child(1).getClass() );
        
        assertEquals(OperatorNode.class   ,            ast.child(0).child(0).child(1).child(0).getClass() );     
        assertEquals(NumberNode.class   ,              ast.child(0).child(0).child(1).child(0).child(0).getClass() );     
        assertEquals( 38L , ((NumberNode)              ast.child(0).child(0).child(1).child(0).child(0)).getValue() );
        
        assertEquals(RegisterReferenceNode.class ,      ast.child(0).child(0).child(1).child(0).child(1).getClass() );
        assertEquals(Register.A ,((RegisterReferenceNode)ast.child(0).child(0).child(1).child(0).child(1) ).getRegister() );
    }     
}
