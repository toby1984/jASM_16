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
import de.codesourcery.jasm16.AddressingMode;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ASTVisitor;
import de.codesourcery.jasm16.ast.ConstantValueNode;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.OperandNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.ISymbolTable;
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
        		if ( node.getAddressingMode() == AddressingMode.IMMEDIATE ||
        		     node.getAddressingMode() == AddressingMode.INDIRECT ) 
        		{
        			if ( node.getChildCount() != 1 ) {
						unit.addMarker( 
								new CompilationError("Addressing operand with != one child ?",unit,node) 
							);        				
        			}
        			checkValueInValidOperandRange(  compContext , node.child(0) );
        		} 
        		else  if ( node.getAddressingMode() == AddressingMode.INDIRECT_REGISTER_OFFSET ) 
        		{
        			final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {

						@Override
						public boolean visit(ASTNode node) 
						{
							if ( node instanceof OperatorNode) 
							{
								final OperatorNode op = (OperatorNode) node;
								if ( op.getOperator() != Operator.PLUS ) {
									unit.addMarker( 
										new CompilationError("Register-indirect with offset did not evaluate to an addition",unit,node) 
									);
								} 
								else 
								{
									ASTNode child1 = op.child( 0 );
									ASTNode child2 = op.child( 1 );
									if ( child1 instanceof RegisterReferenceNode) {
										checkValueInValidAddressRange( compContext , child2 );
									} else if ( child2 instanceof RegisterReferenceNode ) {
										checkValueInValidAddressRange( compContext , child1 );
									}
								}
							}
							return true;
						}

					};
        			ASTUtils.visitInOrder( node , visitor );
        		}
        	}
        	
        };
        ASTUtils.visitInOrder( unit.getAST() , visitor );
    }
    
    private void checkValueInValidOperandRange(ICompilationContext compContext , ASTNode child) 
	{
    	final ICompilationUnit unit = compContext.getCurrentCompilationUnit();
		if ( ! ( child instanceof ConstantValueNode ) ) {
			unit.addMarker( 
					new CompilationError("Unexpected operand node "+child,unit,child) 
				);								
			return;
		}
		final ISymbolTable symbolTable = compContext.getSymbolTable();
		final Long value = ((ConstantValueNode) child).getNumericValue( symbolTable );
		if ( value == null ) {
			unit.addMarker( 
					new CompilationError("Failed to resolve operand ?"+child,unit,child) 
				);								
			return;
		}							
		if ( ! InstructionNode.isValidOperandValue( value ) ) {
			unit.addMarker( 
					new CompilationError("Out-of-range operand "+
							value,unit,child) 
				);								
			return;								
		}
	}     
    
    private void checkValueInValidAddressRange(ICompilationContext compContext , ASTNode child) 
	{
    	final ICompilationUnit unit = compContext.getCurrentCompilationUnit();
		if ( ! ( child instanceof ConstantValueNode ) ) {
			unit.addMarker( 
					new CompilationError("Invalid address "+child,unit,child) 
				);								
			return;
		}
		final ISymbolTable symbolTable = compContext.getSymbolTable();
		final Long value = ((ConstantValueNode) child).getNumericValue( symbolTable );
		if ( value == null ) {
			unit.addMarker( 
					new CompilationError("Failed to resolve address ?"+child,unit,child) 
				);								
			return;
		}							
		if ( value < 0 || value > Address.MAX_ADDRESS) {
			unit.addMarker( 
					new CompilationError("Out-of-range offset/address "+
							value,unit,child) 
				);								
			return;								
		}
	}    
    
    protected boolean isAbortOnErrors() {
        return true;
    }
}
