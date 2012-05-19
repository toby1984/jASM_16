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
package de.codesourcery.jasm16.ast;

import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.Equation;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * A '.equ' variable assignment.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class EquationNode extends ConstantValueNode {

	private Identifier identifier;
	
	
	public Long getNumericValue(ISymbolTable symbolTable) {
		return calculate( symbolTable );
	}

	
	public Long calculate(ISymbolTable symbolTable) 
	{
		if ( getChildCount() == 1) 
		{
			return ((TermNode) child(0)).calculate( symbolTable );
		}
		return null;
	}

	
	public TermNode reduce(ICompilationContext context) 
	{
		// currently not supported, just return a copy
		return (TermNode) createCopy( true );
	}

	
	public ASTNode createCopy(boolean shallow) {
		return super.createCopy(true); // ALWAYS do a deep copy since the child nodes hold the actual value of this expression
	}
	
	
	public ASTNode copySingleNode() {
		final EquationNode result = new EquationNode();
		result.identifier = identifier;
		return result;
	}

	
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
		final ITextRegion region = new TextRegion( context.read( TokenType.EQUATION ) );
		region.merge( context.read( TokenType.WHITESPACE) );
		
		identifier = context.parseIdentifier( region );
		region.merge( context.read( TokenType.WHITESPACE) );		
		
		final ASTNode expr = new ExpressionNode().parseInternal( context );
		if ( ASTUtils.getRegisterReferenceCount( expr ) > 0 ) {
			context.getCompilationUnit().addMarker( new CompilationError("Equation must not refer to a register",
					context.getCompilationUnit() , expr ) );
		}
		
		addChild( expr , context );
		mergeWithAllTokensTextRegion( region );		
		
		if ( context.getSymbolTable().containsSymbol( identifier ) ) {
			context.getCompilationUnit().addMarker(
				new CompilationError("Duplicate symbol with identifier '"+identifier+"'",
					context.getCompilationUnit() , expr ) );			
		} 
		else 
		{ 
			final TermNode termNode;
			if ( expr instanceof TermNode ) {
				termNode = (TermNode) expr;
			} else {
				termNode = new NumberNode( 0 , expr.getTextRegion() );
			}
			final ISymbol equation= new Equation( 
					context.getCompilationUnit() , 
					getTextRegion() ,
					identifier,
					termNode );
			
			context.getSymbolTable().defineSymbol( equation );
			
			Equation.checkCyclicDependencies( equation.getIdentifier() , context.getSymbolTable() );
		}
		return this;
	}

	
	public boolean supportsChildNodes() {
		return true;
	}
}