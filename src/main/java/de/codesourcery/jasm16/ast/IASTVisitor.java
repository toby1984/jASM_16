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
 * AST visitor supported by {@link ASTUtils#visitInOrder(ASTNode,IASTNodeVisitor<ASTNode>)}.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IASTVisitor {

    public void visit(MacroArgumentNode node,IIterationContext context);	
	
    public void visit(IncludeSourceFileNode node,IIterationContext context);	
    
    public void visit(EquationNode node,IIterationContext context);	
    
    public void visit(RawLineNode node,IIterationContext context);
    
	public void visit(AST node,IIterationContext context);
			
    public void visit(OriginNode node,IIterationContext context);
    
	public void visit(IncludeBinaryFileNode node,IIterationContext context);
	   
	public void visit(CharacterLiteralNode node,IIterationContext context);
	
	public void visit(CommentNode node,IIterationContext context);	
	
	public void visit(ExpressionNode node,IIterationContext context);
	
	public void visit(InstructionNode node,IIterationContext context);
	
	public void visit(LabelNode node,IIterationContext context);
	
	public void visit(SymbolReferenceNode node,IIterationContext context);
	
	public void visit(NumberNode node,IIterationContext context);
	
	public void visit(OperandNode node,IIterationContext context);	
	
	public void visit(OperatorNode node,IIterationContext context);	
	
	public void visit(RegisterReferenceNode node,IIterationContext context);
	
	public void visit(StatementNode node,IIterationContext context);	
	
    public void visit(InitializedMemoryNode node,IIterationContext context);	
	
	public void visit(UninitializedMemoryNode node,IIterationContext context);
	
	public void visit(UnparsedContentNode node,IIterationContext context);	
	
    public void visit(IdentifierNode node,IIterationContext context);

    public void visit(StartMacroNode node,IIterationContext context);
    
    public void visit(InvokeMacroNode node,IIterationContext context);
    
    public void visit(EndMacroNode node,IIterationContext context);
    
    public void visit(MacroParametersListNode node,IIterationContext context);
}
