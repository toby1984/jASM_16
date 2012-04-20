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

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ExpressionNode;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.NumberNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.compiler.CompilationContext;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.IParseContext;

public class ExpressionNodeTest extends TestHelper {

	public void testParseNumberLiteral() {
		
		final String expression = "0x1234";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		
		assertNotNull( result );
		assertFalse( result.hasErrors() );
		
		assertEquals( expression , toSourceCode( result , expression ) );
	}
	
	public void testParseRegisterReference() {
		
		final String expression = "  SP  ";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		assertNotNull( result );
		assertFalse( result.hasErrors() );
		
		assertEquals( expression , toSourceCode( result , expression ) );
	}	
	
	public void testParseAddition() {
		
		final String expression = "1 + 2";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		assertNotNull( result );
		assertFalse( result.hasErrors() );
		
		assertEquals( expression , toSourceCode( result , expression ) );
	}	
	
	public void testParseExpressionWithParens() {
		
		final String expression = "(1 + 2)";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		ASTUtils.printAST( result );
		assertNotNull( result );
		assertFalse( result.hasErrors() );
		assertEquals( OperatorNode.class , result.getClass() );
		OperatorNode op = (OperatorNode) result;
		assertEquals( 2 , op.getChildCount() );
		assertEquals( NumberNode.class ,op.child(0).getClass() );
		assertEquals( NumberNode.class , op.child(1).getClass() );
		assertEquals( expression , toSourceCode( result , expression ) );
	}	
	
   public void testParseIncompleteExpression() {
        
        final String expression = "1+2*";
        
        final IParseContext parseContext = createParseContext( expression );
        
        final ASTNode result = new ExpressionNode().parse( parseContext );
        assertNotNull( result );
        assertTrue( result.hasErrors() );
    }   
	
	public void testOperatorPrecedence() {
		final String expression = " (4+5*(3+2))";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		assertNotNull( result );
		assertFalse( result.hasErrors() );
		assertEquals( expression , toSourceCode( result , expression ) );		
	}
	
	public void testParseExpressionWithNestedParens() {
		
		final String expression = "(((1 + 2)))";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		assertNotNull( result );
		assertFalse( result.hasErrors() );
		
		final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {
			
			@Override
			public boolean visit(ASTNode node) 
			{
				System.out.println( node.getClass().getName() +" '"+node.toString()+"'" );
				return true;
			}
		};
		ASTUtils.visitInOrder( result , visitor );

		assertEquals( expression , toSourceCode( result , expression ) );
	}	
	
	public void testParseExpressionWithNestedParens2() {
		
		final String expression = "((( 1+2 )))";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		assertNotNull( result );
		assertFalse( result.hasErrors() );
		
		final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {
			
			@Override
			public boolean visit(ASTNode node) 
			{
				System.out.println( node.getClass().getName() +" '"+node.toString()+"'" );
				return true;
			}
		};
		ASTUtils.visitInOrder( result , visitor );

		assertEquals( expression , toSourceCode( result , expression ) );
	}	
	
	public void testParseSubtraction() {
		
		final String expression = "1 - 2";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		assertNotNull( result );
		assertFalse( result.hasErrors() );
		assertEquals( expression , toSourceCode( result , expression ) );
	}	
	
	
	public void testParseMultiplication() {
		
		final String expression = "1 * 2";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		assertNotNull( result );
		
		assertEquals( expression , toSourceCode( result , expression ) );
	}	
	
	public void testParseDivision() {
		
		final String expression = "1 / 2";
		
		final IParseContext parseContext = createParseContext( expression );
		
		final ASTNode result = new ExpressionNode().parse( parseContext );
		assertNotNull( result );
		assertFalse( result.hasErrors() );		
		
		assertEquals( expression , toSourceCode( result , expression ) );
	}
	
    public void testParseMismatchedClosingParensFails() {
        
        final String expression = "((1+2)";
        
        final IParseContext parseContext = createParseContext( expression );
        
        final ASTNode result = new ExpressionNode().parse( parseContext );
        assertNotNull( result );
        assertTrue( result.hasErrors() );      
        
        assertEquals( expression , toSourceCode( result , expression ) );
    }
    
    public void testParseMismatchedOpeningParensFails() {
        
        assertDoesNotCompile( "SET A ,1+2)" );
    }    
	
	public void testFoldSimpleConstantExpression() throws ParseException, IOException {

		final String source = " 1 + 1 ";
		
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
		
		final IParseContext context = createParseContext( source );
		
		final ASTNode result = new ExpressionNode().parse( context );
		assertFalse( result.hasErrors() );
		
		final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
		
		assertEquals( NumberNode.class , foldingResult.getClass() );
		assertEquals( 2L , ((NumberNode) foldingResult).getValue() );
	}
	
    public void testFoldConstantExpression1j() throws ParseException, IOException {

        final String source = " 1 + 4 + 5 * 3 ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        ASTUtils.printAST( result );
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 20L , ((NumberNode) foldingResult).getValue() );
    }	
	
    public void testFoldConstantExpression1a() throws ParseException, IOException {

        final String source = " ( 3+3 ) *2 ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 12L , ((NumberNode) foldingResult).getValue() );
    }	
    
    public void testFoldConstantExpression1c() throws ParseException, IOException {

        final String source = " ( 3+(3*2)) ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 9L , ((NumberNode) foldingResult).getValue() );
    }    
    
    public void testFoldConstantExpression1d() throws ParseException, IOException {

        final String source = " ( (3+3)*2) ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 12L , ((NumberNode) foldingResult).getValue() );
    }  
    
    public void testFoldConstantExpression1e() throws ParseException, IOException {

        final String source = " 4-3 ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 1L , ((NumberNode) foldingResult).getValue() );
    }   
    
    public void testFoldConstantExpression1f() throws ParseException, IOException {

        final String source = " 13-(3*3) ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 4L , ((NumberNode) foldingResult).getValue() );
    }  
    
    public void testFoldConstantExpression1g() throws ParseException, IOException {

        final String source = " (13-3)*3 ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 30L , ((NumberNode) foldingResult).getValue() );
    }   
    
    public void testFoldConstantExpression1h() throws ParseException, IOException {

        final String source = " (3 + ((4 * 2) / ( 1 - 5 ) )) "; // 3 + ( 8 / -4 ) = 3 + -2 = 1 
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        ASTUtils.printAST( result );        
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 1L , ((NumberNode) foldingResult).getValue() );
    }    
	
	public void testFoldConstantExpression() throws ParseException, IOException {

		final String source = " ( ( ( 3+3 ) *2 ) - 4 ) / ( 1 + 1)";
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
		
		final IParseContext context = createParseContext( source );
		
		final ASTNode result = new ExpressionNode().parse( context );
		assertFalse( result.hasErrors() );
		
		final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
		
		assertEquals( NumberNode.class , foldingResult.getClass() );
		assertEquals( 4L , ((NumberNode) foldingResult).getValue() );
	}	
	
    public void testFoldConstantExpression1b() throws ParseException, IOException {

        final String source = "( ( 3+3 ) *2 ) - 4";  // 
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        ASTUtils.printAST( result );
        assertFalse( result.hasErrors() );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 8L , ((NumberNode) foldingResult).getValue() );
    }	
	
	public void testFoldRegisterExpression1() throws ParseException, IOException {

		final String source = " 3 + a ";
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
		
		final IParseContext context = createParseContext( source );
		
		final ASTNode result = new ExpressionNode().parse( context );
		assertFalse( result.hasErrors() );
		assertEquals( source , toSourceCode( result , source  ) );
		
		final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
		assertEquals( source , toSourceCode( foldingResult , source ) );
	}	
	
	public void testFoldRegisterExpression2() throws ParseException, IOException {

		final String source = " a + 3 ";
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
		
		final IParseContext context = createParseContext( source );
		
		final ASTNode result = new ExpressionNode().parse( context );
		assertFalse( result.hasErrors() );
		
		final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
		assertEquals( source , toSourceCode( foldingResult , source ) );
	}
	
    public void testFoldRegisterExpression3a() throws ParseException, IOException {

        final String source = " 3+3 ";
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
        
        final IParseContext context = createParseContext( source );
        
        final ASTNode result = new ExpressionNode().parse( context );
        assertFalse( result.hasErrors() );
        assertEquals( source , toSourceCode( result , source ) );
        
        final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
        
        assertEquals( NumberNode.class , foldingResult.getClass() );
        assertEquals( 6L , ((NumberNode) foldingResult).getValue() );
        
        assertEquals( source , toSourceCode( foldingResult , source ) );
    }   	
	
	public void testFoldRegisterExpression3() throws ParseException, IOException {

		final String source = " a+3+3 ";
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
		
		final IParseContext context = createParseContext( source );
		
		final ASTNode result = new ExpressionNode().parse( context );
		assertFalse( result.hasErrors() );
		assertEquals( source , toSourceCode( result , source ) );
		
		final ASTNode foldingResult = ((TermNode) result).reduce( compilationContext );
		assertEquals( source , toSourceCode( foldingResult , source ) );
	}	
	
	public void testFoldRegisterExpression4() throws ParseException, IOException {

		final String source = " 2*3 + a ";
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
		
		final IParseContext context = createParseContext( source );
		
		final ASTNode result = new ExpressionNode().parse( context );
		assertFalse( result.hasErrors() );
		
		final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
		assertEquals( source , toSourceCode( foldingResult , source ) );
	}	
	
	public void testFoldRegisterExpression5() throws ParseException, IOException {

		final String source = " 3 + a + 3 ";
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS);
		
		final IParseContext context = createParseContext( source );
		
		final ASTNode result = new ExpressionNode().parse( context );
		assertFalse( result.hasErrors() );
		
		final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
		assertEquals( source , toSourceCode( foldingResult , source ) );
	}	
	
	public void testFoldRegisterExpression6() throws ParseException, IOException {

		final String source = " 1 + 2 + 3 + a + 4 + 5 + 6 ";
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		final ICompilationContext compilationContext = new CompilationContext( unit , symbolTable , NOP_WRITER , RESOURCE_RESOLVER , OPTIONS );
		
		final IParseContext context = createParseContext( source );
		
		final ASTNode result = new ExpressionNode().parse( context );
		assertFalse( result.hasErrors() );
		
		final TermNode foldingResult = ((TermNode) result).reduce( compilationContext );
		assertEquals( source , toSourceCode( foldingResult , source ) );
	}	
}
