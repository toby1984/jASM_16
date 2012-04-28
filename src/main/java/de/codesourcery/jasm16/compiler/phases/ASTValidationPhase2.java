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
package de.codesourcery.jasm16.compiler.phases;

import java.io.IOException;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ASTVisitor;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.OperandNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.parser.Operator;

/**
 * AST validation phase that makes sure that all literal/address values values
 * are within the bounds of the DCPU-16 architecture.
 * 
 * <p>This phase requires expressions to already be folded/reduced to literal values
 * and labels resolved to their memory addresses so it can only run after 
 * the {@link FoldExpressionsPhase}.</p>
 *  
 * @author tobias.gierke@code-sourcery.de
 */
public class ASTValidationPhase2 extends CompilerPhase {

	public ASTValidationPhase2() {
		super(ICompilerPhase.PHASE_VALIDATE_AST2);
	}
	
	protected static class RegisterWithOffsetValidator implements ISimpleASTNodeVisitor<ASTNode> {

		private int registerRefCount=0;
		private ICompilationUnit unit;
		private ICompilationContext context;
		
		public RegisterWithOffsetValidator(ICompilationContext context) {
			this.unit = context.getCurrentCompilationUnit();
			this.context = context;
		}

		@Override
		public boolean visit(ASTNode node) 
		{
			if ( node instanceof RegisterReferenceNode) 
			{
				registerRefCount++;
				if ( registerRefCount > 1 ) {
					unit.addMarker( 
						new CompilationError("Expression must not contain more than one register reference",unit,node)
					);					
				}
				if ( node.getParent() != null && node.getParent() instanceof OperatorNode) 
				{
					if ( ((OperatorNode) node.getParent()).getOperator() != Operator.PLUS) {
						unit.addMarker( 
								new CompilationError("Register-indirect with offset did not evaluate to an addition",unit,node)
						);
					}
				}
			} 
			else if ( node instanceof OperatorNode) 
			{
				return checkValueInRange( context , (OperatorNode) node , 0L , WordAddress.MAX_ADDRESS );
			}
			return true;
		}
	};

	@Override
	protected void run(final ICompilationUnit unit , final ICompilationContext compContext) throws IOException
	{
		if ( unit.getAST() == null ) {
			return;
		}

		final ASTVisitor visitor = new ASTVisitor() 
		{
			@Override
			public void visit(OperandNode node, IIterationContext context) 
			{
				switch(  node.getAddressingMode() )
				{
					case IMMEDIATE:
						if ( node.getChildCount() != 1 ) {
							unit.addMarker(	new CompilationError("Addressing operand with != one child ?",unit,node) );        				
						}
						final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {
	
							@Override
							public boolean visit(ASTNode node) 
							{
								if ( node instanceof OperatorNode) 
								{
									return checkValueInRange( compContext , (OperatorNode) node , 0L , 65535 );
								}
								return true;
							}
						};
						ASTUtils.visitInOrder( node , visitor );					
						break;
					case INDIRECT: 
						if ( node.getChildCount() != 1 ) {
							unit.addMarker( new CompilationError("Addressing operand with != one child ?",unit,node) );        				
						}
						final ISimpleASTNodeVisitor<ASTNode> visitor2 = new ISimpleASTNodeVisitor<ASTNode>() {
	
							@Override
							public boolean visit(ASTNode node) 
							{
								if ( node instanceof OperatorNode) 
								{
									return checkValueInRange( compContext , (OperatorNode) node , 0L , WordAddress.MAX_ADDRESS );
								}
								return true;
							}
						};
						ASTUtils.visitInOrder( node , visitor2 );						
						break;
					case INDIRECT_REGISTER_OFFSET: 
						ASTUtils.visitInOrder( node , new RegisterWithOffsetValidator( compContext ) );
						break;
				}
			}

		};
		
		ASTUtils.visitInOrder( unit.getAST() , visitor );
	}

	private static boolean checkValueInRange(final ICompilationContext compContext,OperatorNode node,final long minValue,final long maxValue) 
	{
		final ICompilationUnit unit = compContext.getCurrentCompilationUnit();
	
		final boolean[] valueInRange = {true};
		final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {

			@Override
			public boolean visit(ASTNode node) 
			{
				if ( node instanceof TermNode) 
				{
					final TermNode op = (TermNode) node;
					final Long value = op.calculate( compContext.getSymbolTable() );
					if ( value == null ) {
						unit.addMarker( 
								new CompilationError("Internal error, operand value has no value?",unit,node)
						);
						valueInRange[0]=false;
						return false;
					} 
					if ( value < minValue || value > maxValue ) 
					{
						unit.addMarker( 
							new CompilationError("Operand value "+value+" out-of-range( "+minValue+" - "+maxValue+")",unit,node)
						);
						valueInRange[0]=false;
						return false;
					}
				}
				return true;
			}

		};
		ASTUtils.visitInOrder( node , visitor );
		return valueInRange[0];
	}

	protected boolean isAbortOnErrors() {
		return true;
	}
}
