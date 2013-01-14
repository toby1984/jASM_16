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

import org.apache.commons.lang.ObjectUtils;

import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Operator;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * AST node that represents an operator along with it's operands.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class OperatorNode extends TermNode
{
    private Operator operator;

    public OperatorNode() {
    }
    
    public OperatorNode(Operator op,ASTNode term1,IParseContext context) 
    {
        if ( op == null ) {
            throw new IllegalArgumentException("op must not be NULL.");
        }
        if ( term1 == null ) {
            throw new IllegalArgumentException("term1 must not be NULL.");
        }
        if ( op.isInfixOperator() ) {
            throw new IllegalArgumentException("Cannot use infix operator "+op+" with only one term");
        }
        this.operator = op;
        addChild( term1 , context );
    }  
    
    @Override
    public boolean equals(Object obj)
    {
        if ( obj == this ) {
            return true;
        }
        if ( obj instanceof OperatorNode) {
            return ObjectUtils.equals( operator , ((OperatorNode) obj).operator ) ;
        }
        return false; 
    }    
    
    public OperatorNode(Operator operator, ASTNode term1,ASTNode term2,ITextRegion foldedTextRegion) 
    {
        super( foldedTextRegion );
    	if ( operator == null ) {
			throw new IllegalArgumentException("operator must not be NULL");
		}
    	this.operator = operator;
    	addChild( term1 , null );
    	addChild( term2 , null );
    }
    
    public boolean hasAllRequiredArguments() {
        return getTermCount() == this.operator.getRequiredOperandCount();
    }
    
    @Override
    protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
        final IToken tok = context.read("expected an operator",TokenType.OPERATOR);
        mergeWithAllTokensTextRegion( tok  );
        
        if ( ! Operator.isValidOperator( tok.getContents() ) ) 
        {
            throw new ParseException("Invalid operator '"+tok.getContents()+"'", tok );
        }
        this.operator = Operator.fromString( tok.getContents() );
        return this;
    }
    
    public Operator getOperator() {
        return operator;
    }
    
    public boolean isNumberTerm(int termIndex) {
    	return getTerm( termIndex ) instanceof NumberNode;
    }
    
    public boolean isRegisterReference(int termIndex) 
    {
    	return getTerm( termIndex ) instanceof RegisterReferenceNode;
    }    
    
    public int getTermCount()
    {
    	int count = 0;
    	for ( ASTNode child : getChildren() ) {
    		if ( ExpressionNode.isTermNode( child ) ) {
    			count++;
    		}
    	}
    	return count;
    }
    
    public TermNode getTerm1()
    {
        return getTerm(0);
    }
    
    public TermNode getTerm2()
    {
        return getTerm(1);
    }
    
    public TermNode getTerm(int index) throws RuntimeException 
    {
    	int count = 0;
    	for ( ASTNode child : getChildren() ) {
    		if ( ExpressionNode.isTermNode( child ) ) {
    			if ( count == index ) {
    				return (TermNode) child;
    			}
    			count++;
    		}
    	}
    	return null;
    }
    
    @Override
    public String toString() 
    {
    	if ( getOperator() == null ) {
    		return "?? NULL operator ??";
    	}
    	return getOperator().getLiteral();
//    	if ( ! hasChildren() ) {
//    		return getOperator().getLiteral()+" ( no children )";
//    	}
//    	if ( getOperator().isPrefixOperator() ) 
//    	{
//    		if ( getOperator() == Operator.PARENS ) {
//    			return "( "+child(0)+" )";
//    		}
//    		return getOperator()+""+child(0);
//    	} else if ( getOperator().isPostfixOperator() ) {
//    		return child(0)+""+getOperator().getLiteral();
//    	}
//    	if ( getChildCount() < 2 ) {
//       		return child(0)+" "+getOperator().getLiteral()+" <missing operand>";
//    	}
//    	return child(0)+" "+getOperator().getLiteral()+" "+child(1);
    }
    
	@Override
	public TermNode reduce(ICompilationContext context) 
	{
		return operator.fold( context ,  this );
	}

    @Override
	protected OperatorNode copySingleNode()
    {
        final OperatorNode result = new OperatorNode();
        result.operator = operator;
        return result;
    }
    
    @Override
    public boolean supportsChildNodes() {
        return true; 
    }

	@Override
	public Long calculate(ISymbolTable symbolTable) {
		return operator.calculate( symbolTable,  this );
	}    
    
}