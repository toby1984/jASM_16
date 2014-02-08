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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.codesourcery.jasm16.AddressingMode;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Operator;

/**
 * An operand in an assembly opcode.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class OperandNode extends ASTNode
{
	private AddressingMode addressingMode;

	public enum OperandPosition {
		SOURCE_OPERAND,
		TARGET_OPERAND,
		SOURCE_OR_TARGET,
		NOT_POSSIBLE;
	}

	public OperandNode() {
		this.addressingMode = null;
	}

	@Override
	protected OperandNode parseInternal(IParseContext context) throws ParseException
	{
		/*
		 * OPERAND := INDIRECT | MEMORY | IMMEDIATE | REGISTER_REF
		 * --------------------------------------------------------
		 *  
		 * INDIRECT := '[' REGISTER ']' | '[' REGISTER_EXPR ']' | '[SP++]' | '[--SP]'
		 *              
		 * REGISTER_EXPR := CONST_EXPR '+' REGISTER | REGISTER '+' CONST_EXPR | CONSTR_EXPR '+' REGISTER '+' CONST_EXPR 
		 *  
		 * REGISTER := 'A' | 'B' | 'C' | 'X' | 'Y' | 'Z' | 'I' | 'J' | 'SP'
		 *  
		 * CONST_EXPR = NUMBER | ADDITION | SUBTRACTION | MULTIPLICATION | DIVISION | '(' CONST_EXPR ')'
		 *
		 * ADDITION := CONST_EXPR '+' CONST_EXPR
		 * SUBTRACTION := CONST_EXPR '-' CONST_EXPR
		 * MULTIPLICATION := CONST_EXPR '*' CONST_EXPR
		 * DIVISION := CONST_EXPR '/' CONST_EXPR
		 * 
		 * ----------------------------------------------------
		 * 
		 * MEMORY := '[' NUMBER ']'
		 * 
		 * ----------------------- IMMEDIATE -------------------- 
		 * 
		 * IMMEDIATE := CONST_EXPR
		 * 
		 * ------------------------------------------------------
		 * 
		 * REGISTER_REF := REGISTER | 'SP' INCREMENT | DECREMENT 'SP'
		 * 
		 * INCREMENT := '++'
		 * DECREMENT := '--'
		 */
		final IToken tok = context.peek();
		if ( tok.hasType( TokenType.PICK ) ) 
		{
			this.addressingMode = AddressingMode.INTERNAL_EXPRESSION;
			addChild( new RegisterReferenceNode().parse( context ) , context );
			// parse operand
			final ASTNode expr = new ExpressionNode().parse( context );
			validateRegisterRefCount( context , expr , 0 );
			addChild( expr , context );
			return this;
			
		} else if ( tok.hasType( TokenType.PUSH ) || tok.hasType( TokenType.POP) || tok.hasType( TokenType.PEEK )) 
		{
			this.addressingMode = AddressingMode.INTERNAL_EXPRESSION;
			addChild( new RegisterReferenceNode().parse( context ) , context );
			return this;
		} 
		else if ( tok.hasType( TokenType.ANGLE_BRACKET_OPEN) ) { // INDIRECT ADDRESSING
			this.addressingMode = AddressingMode.INTERNAL_EXPRESSION;
			mergeWithAllTokensTextRegion( context.read( TokenType.ANGLE_BRACKET_OPEN ) );
			final ASTNode expr = wrapExpression( new ExpressionNode().parseInternal( context ) , context );
			validateRegisterRefCount( context, expr , 1 );
			addChild( expr , context );
			mergeWithAllTokensTextRegion( context.read( TokenType.ANGLE_BRACKET_CLOSE ) );
			return this;
		} 
		else if ( tok.hasType( TokenType.CHARACTERS ) ||
				  tok.hasType( TokenType.DOT ) || // local label
				  tok.hasType(TokenType.STRING_DELIMITER) ) 
		{ 
			// REGISTER or IMMEDIATE (label reference)
			if ( tok.hasType( TokenType.CHARACTERS ) && nextTokenIsRegisterIdentifier( context ) )
			{ 
				this.addressingMode = AddressingMode.REGISTER;
				final ASTNode node = new RegisterReferenceNode().parse( context );
				addChild( node , context );
			} 
			else 
			{
				// symbol reference or character literal
				this.addressingMode = AddressingMode.IMMEDIATE;            	
				ASTNode expr = new ExpressionNode().parse( context );
				validateRegisterRefCount( context , expr , 0 );
				addChild( expr , context );
			}
		} else if ( isMinusOperator( tok ) || tok.hasType( TokenType.NUMBER_LITERAL ) || tok.hasType( TokenType.PARENS_OPEN )) {
			this.addressingMode = AddressingMode.IMMEDIATE;
			final ASTNode expression = wrapExpression( new ExpressionNode().parse( context ) , context );
			validateRegisterRefCount( context, expression , 0 );
			addChild( expression, context  );            
		} else {
			throw new ParseException("Unexpected operand token: "+tok , tok );
		}
		return this;
	}
	
	private boolean isMinusOperator(IToken tok) {
	    return tok.hasType( TokenType.OPERATOR ) && Operator.fromString( tok.getContents() ) == Operator.MINUS;
	}
	
	private void validateRegisterRefCount(IParseContext context,ASTNode node,int maxNum) throws ParseException 
	{
		final int count = ASTUtils.getRegisterReferenceCount( node );
        if ( count > maxNum ) 
        {
        	final String cardinality;
        	if ( maxNum == 0 ) {
        		cardinality ="a";
        	} else if ( maxNum == 1 ) {
        		cardinality ="more than one";
        	} else {
         		cardinality = "more than "+Integer.toString( maxNum );
        	}
        	
        	final ICompilationUnit unit = context.getCompilationUnit();
        	final String error = "Expression must not contain "+cardinality+" register reference";
        	unit.addMarker( new CompilationError(error,unit,node ) );
        }		
	}

	private ASTNode wrapExpression(ASTNode input,IParseContext context) 
	{
		if ( input instanceof ExpressionNode || ! ( input instanceof OperatorNode) ) {
			return input;
		}

		final ExpressionNode result = new ExpressionNode();
		result.addChild( input , context );
		return result;
	}
	
	/**
	 * Returns whether this operand uses at least one reference 
	 * to a location in the object code.
	 * 
	 * <p>This method is used to determine whether a relocation table entry
	 * needs to be generated for this operand.</p>
	 * @return <code>NULL</code> if this operand has a symbol reference but the symbol
	 * could not be found in the symbol table (and thus we were unable to tell whether
	 * the referenced symbol is a label or something else) , <code>true</code>
	 * if this operand references a {@link Label}, <code>false</code> otherwise 
	 * 
	 * @see ISymbol
	 * @see Label 
	 */
	public Boolean referencesLabel(final ISymbolTable symbolTable) {
	    
	    final AtomicBoolean labelsFound = new AtomicBoolean(false);
	    final AtomicBoolean unresolvedSymbolsFound = new AtomicBoolean(false);
	    
	    final ISimpleASTNodeVisitor<ASTNode> visitor=new ISimpleASTNodeVisitor<ASTNode>() {
            
            @Override
            public boolean visit(ASTNode node)
            {
                if ( node instanceof SymbolReferenceNode) 
                {
                    final ISymbol symbol = ((SymbolReferenceNode) node).resolve(symbolTable); // symbolTable.getSymbol( identifier , scope );
                    if ( symbol == null ) {
                        unresolvedSymbolsFound.set(true);
                    } 
                    else 
                    {
                        if ( symbol instanceof Label ) {
                            labelsFound.set(true);
                        }
                    }
                }
                return true;
            }
        };
        ASTUtils.visitInOrder( this , visitor );
        if ( labelsFound.get() ) {
            return Boolean.TRUE; // important to return Boolean.FALSE here since calling code uses == comparison with Boolean.TRUE / Boolean.FALSE
        }
        if ( unresolvedSymbolsFound.get() ) {
            return null;
        }
        return Boolean.FALSE; // important to return Boolean.FALSE here since calling code uses == comparison with Boolean.TRUE / Boolean.FALSE
	}
	
	public RegisterReferenceNode getRegisterReferenceNode() {
	       final List<RegisterReferenceNode> result = 
	               ASTUtils.getNodesByType( this , RegisterReferenceNode.class , true );
	           if ( result.size() == 1 ) {
	               return result.get(0);
	           } else if ( result.size() > 1 ) {
	               throw new RuntimeException("Operand with more than one RegisterReferenceNode? "+this);
	           }
	           return null;
	}

	public Register getRegister() 
	{
	    final RegisterReferenceNode regNode = getRegisterReferenceNode();
	    return regNode != null ? regNode.getRegister() : null;
	}

	public Long getLiteralValue(ISymbolTable symbolTable) 
	{
		if ( ! supportsLiteralValue() ) {
			return null;
		}
		final List<TermNode> terms = new ArrayList<TermNode>();
		final List<ConstantValueNode> literalValues = new ArrayList<ConstantValueNode>();
		for ( ASTNode child : getChildren() ) {
			if ( child instanceof ConstantValueNode ) { // careful, ConstantValueNode extends TermNode so order matters !!!
				literalValues.add( (ConstantValueNode) child );
			} else if ( child instanceof RegisterReferenceNode ) {
				// ignore
			}			
			else if ( child instanceof TermNode ) // careful, ConstantValueNode extends TermNode so order matters !!!
			{
				terms.add( (TermNode) child);
			}

		}
		if ( terms.size() > 1 || literalValues.size() > 1 ) {
			return null;
		}
		if ( terms.isEmpty() && literalValues.isEmpty() ) {
			return null;
		}
		if ( terms.size() == 1 && literalValues.size() == 1 ) {
			return null;
		}
		if ( ! terms.isEmpty() ) {
			return terms.get(0).calculate( symbolTable );
		}
		return literalValues.get(0).calculate( symbolTable );
	}

	public boolean supportsLiteralValue() 
	{
		switch( getAddressingMode() ) 
		{
			case INDIRECT: 
			case INDIRECT_REGISTER_OFFSET: 
			case IMMEDIATE: 
				return true;
		}
		return false;
	}

	public ConstantValueNode getLiteralValueNode() 
	{
		if ( supportsLiteralValue() ) 
		{
			final List<ConstantValueNode> literals = ASTUtils.getNodesByType( this , ConstantValueNode.class , false );
			if ( literals.size() == 1 ) {
				return literals.get(0);
			}
		}
		return null;
	}

	public enum ExpressionType {
		CONSTANT,
		REGISTER,
		REGISTER_OFFSET,
		REGISTER_POSTINCREMENT,
		REGISTER_PREDECREMENT,
		UNKNOWN;
	}

	private enum NodeType {
		CONSTANT,
		REGISTER,
		REGISTER_INCREMENT,
		REGISTER_POSTINCREMENT,
		REGISTER_PREDECREMENT,
		OPERATOR;
	}

	private ExpressionType getExpressionType()
	{
		final Set<NodeType> nodeTypes = getNodeTypes();
		if ( equals( nodeTypes , NodeType.CONSTANT ) ) {
			return ExpressionType.CONSTANT;
		} else if ( equals( nodeTypes , NodeType.CONSTANT , NodeType.OPERATOR ) ) {
			return ExpressionType.CONSTANT;
		} else if ( equals( nodeTypes , NodeType.REGISTER ) ) {
			return ExpressionType.REGISTER;
		} else if ( equals( nodeTypes , NodeType.REGISTER_POSTINCREMENT ) ) {
			return ExpressionType.REGISTER_POSTINCREMENT;     
		} else if ( equals( nodeTypes , NodeType.REGISTER_PREDECREMENT) ) {
			return ExpressionType.REGISTER_PREDECREMENT;             
		} else if ( equals( nodeTypes , NodeType.CONSTANT , NodeType.OPERATOR , NodeType.REGISTER ) ||
				    equals( nodeTypes , NodeType.CONSTANT , NodeType.REGISTER ) ) // special case: PICK N
		{
			return ExpressionType.REGISTER_OFFSET;                
		}
		return ExpressionType.UNKNOWN; 
	}

	private Set<NodeType> getNodeTypes()
	{
		final Set<NodeType> nodeTypes = new HashSet<NodeType>();

		final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {

			@Override
			public boolean visit(ASTNode node)
			{
				final NodeType type = getNodeType(node);
				if ( type != null ) {
					nodeTypes.add( type );
				}
				return true;
			}

			private NodeType getNodeType(ASTNode node) 
			{
				if ( node instanceof NumberNode || node instanceof SymbolReferenceNode) {
					return NodeType.CONSTANT;
				} else if ( node instanceof RegisterReferenceNode) {
					final RegisterReferenceNode r = (RegisterReferenceNode) node;
					if ( r.hasPostIncrement() ) {
						return NodeType.REGISTER_POSTINCREMENT;
					}
					if ( r.hasPreDecrement() ) {
						return NodeType.REGISTER_PREDECREMENT;
					}
					return NodeType.REGISTER;
				} else if ( node instanceof OperatorNode) {
					return NodeType.OPERATOR;
				}
				return null;
			}
		};
		ASTUtils.visitInOrder( this , visitor );
		return nodeTypes;
	}	

	public AddressingMode getAddressingMode()
	{
		if ( addressingMode == AddressingMode.INTERNAL_EXPRESSION ) 
		{
			switch( getExpressionType() ) 
			{
			case CONSTANT:
				return AddressingMode.INDIRECT;
			case REGISTER:
				return AddressingMode.INDIRECT_REGISTER;
			case REGISTER_POSTINCREMENT:
				return AddressingMode.INDIRECT_REGISTER_POSTINCREMENT;
			case REGISTER_PREDECREMENT:
				return AddressingMode.INDIRECT_REGISTER_PREDECREMENT;                    
			case REGISTER_OFFSET:
				return AddressingMode.INDIRECT_REGISTER_OFFSET;
			default:
				throw new RuntimeException("Internal error, unreachable code reached: "+getExpressionType()+" , this="+this);
			}
		}
		return addressingMode;
	}

	private static boolean equals(Set<NodeType> set,NodeType... expected) {
		for ( NodeType t : expected ) {
			if ( ! set.contains( t ) ) {
				return false;
			}
		}
		return set.size() == expected.length;
	}

	private boolean nextTokenIsRegisterIdentifier(IParseContext context) 
	{
		if ( context.eof() ) {
			return false;
		}
		return context.peek().hasType( TokenType.CHARACTERS ) && Register.isRegisterIdentifier( context.peek().getContents() );
	}

	@Override
	protected OperandNode copySingleNode()
	{
		final OperandNode result = new OperandNode();
		result.addressingMode = getAddressingMode();
		return result;
	}

	@Override
	public boolean supportsChildNodes() {
		return true;
	}    
}
