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

/**
 * A NO-OP {@link IASTVisitor} implementation.
 *  
 * @author tobias.gierke@code-sourcery.de
 */
public class ASTVisitor implements IASTVisitor , IASTNodeVisitor<ASTNode> {

	
	public final void visit(ASTNode n, IIterationContext context) 
	{
		if ( n instanceof AST ) {
			visit( (AST) n , context );
	     } else if ( n instanceof IncludeSourceFileNode) {
	         visit( (IncludeSourceFileNode) n , context );				
	     } else if ( n instanceof EquationNode) {
	         visit( (EquationNode) n , context );			
	     } else if ( n instanceof OriginNode) {
	         visit( (OriginNode) n , context );
		} else if ( n instanceof IncludeBinaryFileNode) {
	        visit( (IncludeBinaryFileNode) n , context );
		} else if ( n instanceof CharacterLiteralNode) {
			visit( (CharacterLiteralNode) n , context );
		} else if ( n instanceof CommentNode) {
			visit( (CommentNode) n , context );
		} else if ( n instanceof ExpressionNode) {
			visit( (ExpressionNode) n , context );
		} else if ( n instanceof ExpressionNode) {
			visit( (ExpressionNode) n , context );
		} else if ( n instanceof InstructionNode) {
			visit( (InstructionNode) n , context );
		} else if ( n instanceof LabelNode) {
			visit( (LabelNode) n , context );
		} else if ( n instanceof SymbolReferenceNode) {
			visit( (SymbolReferenceNode) n , context );
		} else if ( n instanceof NumberNode) {
			visit( (NumberNode) n , context );
		} else if ( n instanceof OperandNode) {
			visit( (OperandNode) n , context );
		} else if ( n instanceof OperatorNode) {
			visit( (OperatorNode) n , context );
		} else if ( n instanceof RegisterReferenceNode) {
			visit( (RegisterReferenceNode) n , context );
		} else if ( n instanceof StatementNode) {
			visit( (StatementNode) n , context );
		} else if ( n instanceof InitializedMemoryNode) {
		    visit( (InitializedMemoryNode) n,context);
		} else if ( n instanceof UninitializedMemoryNode) {
			visit( (UninitializedMemoryNode) n , context );
		} else if ( n instanceof UnparsedContentNode) {
			visit( (UnparsedContentNode) n , context );
		} else {
			throw new RuntimeException("Unhandled node class: "+n.getClass());
		}
			
	}
	
    
    public void visit(IncludeSourceFileNode node,IIterationContext context) { }		
	
    
    public void visit(EquationNode node,IIterationContext context) { }	
	
    
    public void visit(OriginNode node,IIterationContext context) { }	
	
	
    public void visit(IncludeBinaryFileNode node,IIterationContext context) { }
    
	
	public void visit(AST node,IIterationContext context) { }
	
	
	public void visit(InitializedMemoryNode node,IIterationContext context) { }
	
	
	public void visit(CharacterLiteralNode node, IIterationContext context) { }

	
	public void visit(CommentNode node, IIterationContext context) { }

	
	public void visit(ExpressionNode node, IIterationContext context) {	}

	
	public void visit(InstructionNode node, IIterationContext context) { }

	
	public void visit(LabelNode node, IIterationContext context) { }

	
	public void visit(SymbolReferenceNode node, IIterationContext context) {}

	
	public void visit(NumberNode node, IIterationContext context) { }

	
	public void visit(OperandNode node, IIterationContext context) {}

	
	public void visit(OperatorNode node, IIterationContext context) {}

	
	public void visit(RegisterReferenceNode node, IIterationContext context) {}

	
	public void visit(StatementNode node, IIterationContext context) {}

	
	public void visit(UninitializedMemoryNode node, IIterationContext context) {}

	
	public void visit(UnparsedContentNode node, IIterationContext context) {}
}
