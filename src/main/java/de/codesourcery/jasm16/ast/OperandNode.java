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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.codesourcery.jasm16.AddressingMode;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

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
        if ( tok.hasType( TokenType.PUSH ) || tok.hasType( TokenType.POP) || tok.hasType( TokenType.PEEK )) 
        {
            this.addressingMode = AddressingMode.INTERNAL_EXPRESSION;
            addChild( new RegisterReferenceNode().parse( context ) , context );
            return this;
        } 
        else if ( tok.hasType( TokenType.ANGLE_BRACKET_OPEN) ) { // INDIRECT ADDRESSING
            this.addressingMode = AddressingMode.INTERNAL_EXPRESSION;
            mergeWithAllTokensTextRange( context.read( TokenType.ANGLE_BRACKET_OPEN ) );
            final ASTNode expr = wrapExpression( new ExpressionNode().parseInternal( context ) , context );
            addChild( expr , context );
			mergeWithAllTokensTextRange( context.read( TokenType.ANGLE_BRACKET_CLOSE ) );
            return this;
        } else if ( tok.hasType( TokenType.CHARACTERS ) ) { // REGISTER or IMMEDIATE (label reference)

            if ( nextTokenIsRegisterIdentifier( context ) )
            { 
                this.addressingMode = AddressingMode.REGISTER;
                final ASTNode node = new RegisterReferenceNode().parse( context );
                addChild( node , context );
            } 
            else 
            {
            	// probably a label reference
            	this.addressingMode = AddressingMode.IMMEDIATE;            	
                addChild( new ExpressionNode().parse( context ) , context );
            }
            
        } else if ( tok.hasType( TokenType.NUMBER_LITERAL ) || tok.hasType( TokenType.PARENS_OPEN )) {
            this.addressingMode = AddressingMode.IMMEDIATE;
            addChild( wrapExpression( new ExpressionNode().parse( context ) , context ), context  );            
        } else {
            throw new ParseException("Unexpected operand token: "+tok.getType() , tok );
        }
        return this;
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
    
    public Register getRegister() 
    {
        final List<RegisterReferenceNode> result = 
        	ASTUtils.getNodesByType( this , RegisterReferenceNode.class , true );
        if ( result.size() == 1 ) {
            return result.get(0).getRegister();
        } else if ( result.size() > 1 ) {
            throw new RuntimeException("Operand with more than one RegisterReferenceNode? "+this);
        }
        return null;
    }

    public ConstantValueNode getLiteralValueNode() 
    {
        final AddressingMode mode = getAddressingMode();
        if ( mode == AddressingMode.INDIRECT || 
        	 mode == AddressingMode.INDIRECT_REGISTER_OFFSET || 
        	 mode == AddressingMode.IMMEDIATE ) 
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
        } else if ( equals( nodeTypes , NodeType.CONSTANT , NodeType.OPERATOR , NodeType.REGISTER ) ) {
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
                if ( node instanceof NumberNode || node instanceof LabelReferenceNode) {
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
    public OperandNode copySingleNode()
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
