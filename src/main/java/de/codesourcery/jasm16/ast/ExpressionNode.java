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
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.compiler.GenericCompilationError;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Operator;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Represents an expression in the AST.
 * 
 * <p>Note that while this class implements an
 * shunting yard algorithm for getting
 * operator precedence (including parens) right ,
 * it currently only supports binary left-associative 
 * infix operators.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see Operator
 * @see OperatorNode
 */
public class ExpressionNode extends TermNode
{
    public ExpressionNode() {
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof ExpressionNode;
    }

    private static boolean isEmptyExpression(ASTNode node) 
    {
        return getTermCount( node ) == 0;
    }

    protected static int getTermCount(ASTNode node) 
    {
        int termCount=0;
        for ( ASTNode child : node.getChildren() ) 
        {
            if ( isTermNode( child ) ) {
                termCount++;
            }
        }
        return termCount;
    }

    @Override
    public Long calculate(ISymbolTable symbolTable) 
    {
        final List<TermNode> terms = new ArrayList<TermNode>();
        final List<ConstantValueNode> literalValues = new ArrayList<ConstantValueNode>();
        for ( ASTNode child : getChildren() ) {
            if ( child instanceof ConstantValueNode ) {
                literalValues.add( (ConstantValueNode) child );
            } 
            else if ( child instanceof OperatorNode || child instanceof ExpressionNode ) 
            {
                terms.add( (TermNode) child);
            } else if ( child instanceof RegisterReferenceNode ) {
                // ignore
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

    @Override
    protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
        final int offset = context.currentParseIndex();

        final ASTNode result = recursivelyParseTerm( context );
        if ( result == null ) {
            throw new ParseException("Incomplete or empty expression",offset,0);
        }

        if ( this.getTextRegion() != null ) { // merge leading whitespace etc.
            result.mergeWithAllTokensTextRegion( this.getTextRegion() );
        }

        if ( result.getChildCount() == 1 ) 
        {
            if ( ! hasAllRequiredArguments( result ) ) {
                throw new ParseException("Incomplete expression, add missing argument to complete it.", result.getTextRegion().getEndOffset() ,0 );
            }
        }        
        return result;
    }

    private static enum ExpectedType {
        NOTHING {
            @Override
            public boolean isTreatMinusAsOperator() { return true; }
        },
        LITERAL_OR_EXPRESSION {
            @Override
            public boolean isTreatMinusAsOperator() { return false; }
        },
        INFIX_OR_POSTFIX_OPERATOR {
            @Override
            public boolean isTreatMinusAsOperator() { return true; }
        },
        ANY_OPERATOR {
            @Override
            public boolean isTreatMinusAsOperator() { return true; }
        }, 
        INFIX_OP_OR_LITERAL_OR_EXPRESSION {
            @Override
            public boolean isTreatMinusAsOperator() { return true; }
        };
        
        public abstract boolean isTreatMinusAsOperator();
    }

    private ExpectedType getExpectedType(Stack<ASTNode> termStack,ASTNode previousNode) 
    {
        final ASTNode lastSeen;
        if ( termStack.isEmpty() ) 
        {
            if ( previousNode == null ) 
            {
                // may accept either an INFIX operator
                // or Operator.MINUS that indicates we're about
                // to parse a negative number
                return ExpectedType.INFIX_OP_OR_LITERAL_OR_EXPRESSION; 
            }
            lastSeen = previousNode;
        } 
        else {
            lastSeen = termStack.peek();
        }

        if ( isOperator( lastSeen ) ) 
        {
            if ( isParensOperator( lastSeen ) ) { // saw an parens expr. , now look for an operator
                return ExpectedType.INFIX_OR_POSTFIX_OPERATOR;
            }
            final Operator op = ((OperatorNode) lastSeen).getOperator();
            if ( op.isPrefixOperator() ) {
                return ExpectedType.LITERAL_OR_EXPRESSION; // => PREFIX operator , we're expecting a value (or an expression) now
            } 
            if ( op.isPostfixOperator() ) {
                return ExpectedType.NOTHING; // POSTFIX operator , we're expecting NOTHING now
            }     
            // INFIX operator
            return ExpectedType.LITERAL_OR_EXPRESSION;
        }
        // last seen was something else than an operator
        return ExpectedType.ANY_OPERATOR; 
    }

    private ASTNode recursivelyParseTerm(IParseContext context) throws EOFException, ParseException 
    {
        final Stack<ASTNode> termStack = new Stack<ASTNode>();
        ASTNode previousNode=null;
        do
        {
            if ( context.peek().isWhitespace() ) 
            {
                if ( termStack.isEmpty() ) 
                {
                    mergeWithAllTokensTextRegion( context.parseWhitespace() );
                } else {
                    termStack.peek().mergeWithAllTokensTextRegion( context.parseWhitespace() );
                }

                if ( ! isEmptyExpression(this) && context.eof() ) {
                    clearTermStack(termStack,previousNode,context);
                    return previousNode;
                }				
            }

            if ( context.peek().hasType( TokenType.STRING_DELIMITER) ) 
            {
                final int index = context.currentParseIndex();
                final ASTNode newNode = new CharacterLiteralNode(1).parse( context );
                previousNode = handleStack( termStack , newNode , index ,previousNode , context);            	
            }
            else  if ( context.peek().hasType(TokenType.OPERATOR ) ) 
            { 
                if ( context.peek().getContents().equals( Operator.DECREMENT.getLiteral() ) ) 
                {
                    final int index = context.currentParseIndex();
                    final ASTNode parsed = new RegisterReferenceNode().parse( context );
                    previousNode = handleStack(termStack , parsed , index ,previousNode , context );            		
                } 
                else 
                {
                    final int offset = context.currentParseIndex();
                    
                    final ASTNode node = new OperatorNode().parse( context );

                    if ( node instanceof OperatorNode) 
                    {
                        final OperatorNode operator = (OperatorNode) node;
//                        if ( ! operator.getOperator().isInfixOperator() ) // + - * /
//                        {
//                            throw new ParseException("Not implemented: Cannot handle operator "+operator.getOperator(), operator.getTextRegion() );
//                        }
                        
                        boolean treatAsNegativeNumber = false;
                        if ( Operator.MINUS == operator.getOperator() ) {
                            if ( termStack.isEmpty() && previousNode == null ) {
                                treatAsNegativeNumber = true;
                            } else {
                                treatAsNegativeNumber = ! getExpectedType(termStack, previousNode).isTreatMinusAsOperator();                                 
                            }
                        }
                        
                        if ( treatAsNegativeNumber ) 
                        {
                            // consume any whitespace
                            ITextRegion whitespace =null;
                            if ( ! context.eof() && context.peek().isWhitespace() ) {
                                whitespace = new TextRegion( context.read( TokenType.WHITESPACE ) );
                            }
                            
                            final ASTNode number = new NumberNode().parse( context );
                            if ( whitespace != null) {
                                number.mergeWithAllTokensTextRegion( whitespace );
                            }
                            number.mergeWithAllTokensTextRegion( node );
                            
                            if ( number instanceof NumberNode) {
                                ((NumberNode) number).convertToNegativeNumber();
                            }
                            previousNode = handleStack( termStack , number , offset ,previousNode , context);
                        } 
                        else {
                            previousNode = handleStack( termStack , node , offset ,previousNode , context);
                        }
                    } else {
                        previousNode = handleStack( termStack , node , offset ,previousNode , context);
                    }
                }
            } 
            else if ( context.peek().hasType( TokenType.NUMBER_LITERAL ) ) 
            {
                final int index = context.currentParseIndex();
                final ASTNode newNode = new NumberNode().parse( context );
                previousNode = handleStack( termStack , newNode , index ,previousNode , context);
            } 
            else if ( context.peek().hasType(TokenType.PARENS_OPEN ) ) 
            {
                final int index = context.currentParseIndex();

                final IToken tok = context.read(); // read '('

                final ASTNode node= new ExpressionNode().parse( context );
                if ( context.eof() || ! context.peek().hasType( TokenType.PARENS_CLOSE ) ) 
                {
                    throw new ParseException("Missing closing ')' ",context.currentParseIndex() ,0 );
                }
                final OperatorNode op = new OperatorNode(Operator.PARENS , node , context );
                op.mergeWithAllTokensTextRegion( tok );				
                op.mergeWithAllTokensTextRegion( context.read(TokenType.PARENS_CLOSE ) );
                previousNode = handleStack( termStack , op , index ,previousNode , context );
            } 
            else if ( context.peek().hasType( TokenType.DOT ) || context.peek().hasType( TokenType.CHARACTERS ) ) 
            {
                // (local) identifier
                final int index = context.currentParseIndex();
                final ASTNode parsed;
                if ( Register.isRegisterIdentifier( context.peek().getContents() ) ) {
                    parsed = new RegisterReferenceNode().parse( context );
                } else {
                    parsed = new SymbolReferenceNode().parse( context );
                }
                previousNode = handleStack(termStack , parsed , index ,previousNode , context );
            } else {
                return clearTermStack(termStack,previousNode,context);				
            }

            if ( ! context.eof() && context.peek().isWhitespace() ) 
            {
                if ( termStack.isEmpty() ) {
                    mergeWithAllTokensTextRegion( context.parseWhitespace() );
                } else {
                    termStack.peek().mergeWithAllTokensTextRegion( context.parseWhitespace() );
                }

                if ( ! isEmptyExpression(this) && context.eof() ) {
                    return clearTermStack(termStack,previousNode,context);					
                }				
            }
        } while ( ! context.eof() );
        return clearTermStack(termStack,previousNode,context);
    }

    private static boolean isOperator(ASTNode node) 
    {
        return node instanceof OperatorNode;
    }	

    private static boolean isParensOperator(ASTNode node) 
    {
        return (node instanceof OperatorNode) && ((OperatorNode) node).getOperator() == Operator.PARENS;
    }    

    private ASTNode handleStack(Stack<ASTNode> termStack,ASTNode node,int parseIndex,ASTNode previousNode,IParseContext context) throws ParseException 
    {
        if ( termStack.isEmpty() || ! isOperator( node ) ) 
        {
            termStack.push( node );
            return previousNode;
        }

        final OperatorNode currentOperator = (OperatorNode) node;
        final OperatorNode previousOperator = findLastOperatorOnStack( termStack , parseIndex );

        if ( previousOperator == null ) {
            termStack.push( currentOperator );
            return previousNode;
        }

        /*
            while there is an operator token, previousOperator, at the top of the stack, and

            - nextOperator's precedence is less than or equal to that of previousOperator,         
         */
        ASTNode newPrevious = previousNode; 
        if ( currentOperator.getOperator().getPrecedence() <= previousOperator.getOperator().getPrecedence() ) {
            newPrevious = clearTermStack( termStack , previousNode ,context);
        } 
        termStack.push( currentOperator );
        return newPrevious;
    }

    private OperatorNode findLastOperatorOnStack(Stack<ASTNode> stack, int parseIndex) throws ParseException 
    {
        final int len = stack.size()-1;
        for ( int i = len ; i >= 0 ; i-- ) {
            if ( isOperator( stack.get(i) ) ) {
                return (OperatorNode) stack.get(i);
            }
        }
        if ( stack.size() > 1 ) {
            throw new ParseException("Missing operator",parseIndex,0);
        }
        return null;
    }

    private boolean hasAllRequiredArguments(ASTNode node) 
    {
        if ( isOperator( node ) ) {
            return ((OperatorNode) node).hasAllRequiredArguments();
        }
        return false;
    }

    private ASTNode clearTermStack(final Stack<ASTNode> stack,ASTNode previousNode,IParseContext context) throws ParseException 
    {
        if ( stack.isEmpty() ) {
            return previousNode;
        }

        final Stack<ASTNode> operatorStack = new Stack<ASTNode>();
        final Stack<ASTNode> argumentStack = new Stack<ASTNode>();
        while ( ! stack.isEmpty() ) 
        {
            final ASTNode n = stack.pop();
            if ( isOperator( n ) && ! isParensOperator( n ) ) 
            {
                operatorStack.push( n );
            } else {
                argumentStack.push( unwrapParens( n ) ); // Operator.PARENS has only one argument that is in fact an already evaluated expression
            }
        }

        Collections.reverse( operatorStack );
        Collections.reverse( argumentStack );

        ASTNode rootNode = null;
        ASTNode lastNode = null;
        while( ! operatorStack.isEmpty() ) 
        {
            /*
             * TODO: This code currently only handles left-associative INFIX operators
             * with two arguments.
             */
            OperatorNode operator = (OperatorNode) operatorStack.pop();
            if ( argumentStack.isEmpty() ) {
                throw new ParseException("Operator '"+operator.getOperator().getLiteral()+"' requires an operand",operator.getTextRegion().getEndOffset() , 0 );
            }
            ASTNode argument1 = argumentStack.pop();
            ASTNode argument2 = operator.getOperator().getRequiredOperandCount() == 2 ? argumentStack.isEmpty() ? null : argumentStack.pop() : null;
            operator.insertChild( 0 , argument1 , context );
            if ( argument2 != null ) {
                operator.insertChild( 0 , argument2 , context );
            }
            if ( rootNode == null ) {
                rootNode = operator;
            }
            lastNode = operator;
            argumentStack.push( operator );
        }

        if ( argumentStack.size() == 1 ) 
        {
            if ( lastNode == null ) {
                rootNode = lastNode = argumentStack.pop();
            } else {
                ASTNode arg = argumentStack.pop();
                lastNode = arg;
            }
        } else if ( argumentStack.size() > 1 ) {
            throw new ParseException("Expression has no operators?",argumentStack.pop().getTextRegion() );
        }

        if ( ! operatorStack.isEmpty() || ! argumentStack.isEmpty() ) {
            throw new RuntimeException("Post-condition failure");
        }

        if ( previousNode == null ) {
            return lastNode;
        }

        if ( ! isOperator( previousNode ) ) {
            throw new RuntimeException("Internal error");
        }

        if ( hasAllRequiredArguments( previousNode ) ) {
            lastNode.insertChild(  0 , previousNode , context );
        }  else {
            previousNode.addChild( rootNode , context );
        }
        return lastNode;
    }

    private ASTNode unwrapParens(ASTNode node) 
    {
        if ( !(node instanceof OperatorNode) ) {
            return node;
        }
        if ( ((OperatorNode) node).getOperator() == Operator.PARENS ) 
        {
            ASTNode result = node.child(0);
            result.setTextRegionIncludingAllTokens( node.getTextRegion() ); // include '(' and ')'
            return result;
        }
        return node;
    }

    public static boolean isTermNode(ASTNode n) 
    {
        return n instanceof TermNode;
    }	

    protected boolean isLiteralExpression(ICompilationContext context) 
    {
        return getLiteralValueNode( context ) != null;
    }

    private ConstantValueNode getLiteralValueNode(ICompilationContext context) 
    {
        final List<ConstantValueNode> nodes = new ArrayList<ConstantValueNode>();
        for ( ASTNode child : getChildren() ) {
            if ( child instanceof ConstantValueNode ) {
                nodes.add( (ConstantValueNode) child );
            }
        }
        if ( nodes.size() == 1 ) 
        {
            return nodes.get(0);
        }
        return null;
    }	

    private static boolean containsRegisterReference(ASTNode node) 
    {
        return ASTUtils.containsNodeWithType( node , RegisterReferenceNode.class );
    }	

    private static boolean containsOnlyOperators(ASTNode root,final Operator operator) 
    {
        final boolean[] result = { false };
        final ISimpleASTNodeVisitor<OperatorNode> visitor = new ISimpleASTNodeVisitor<OperatorNode>() {

            @Override
            public boolean visit(OperatorNode node) 
            {
                if ( node.getOperator() ==operator ) {
                    result[0] = true;
                }
                return true;
            }
        };
        ASTUtils.visitNodesByType( root , visitor , OperatorNode.class );
        return result[0];
    }

    public TermNode reduce(ICompilationContext context) 
    {
        ExpressionNode result = new ExpressionNode();
        result.setTextRegionIncludingAllTokens( getTextRegion() );

        for ( ASTNode child : getChildren() ) 
        {
            if ( child instanceof TermNode) 
            {
                final ASTNode reduced = ((TermNode) child).reduce( context );
                result.addChild( reduced , null );
            } else {
                result.mergeTextRegion( child.getTextRegion() );
            }
        }

        // trivial case: resulting expression is a constant/number
        if ( result.isLiteralExpression(context) ) 
        {
            try {
                return result.getLiteralValueNode( context ).toNumberNode( context , result.getTextRegion() );
            } catch (ParseException e) {
                context.getCurrentCompilationUnit().addMarker(  new GenericCompilationError( e.getMessage() , context.getCurrentCompilationUnit() , e ) );
            }
        }

        if ( containsRegisterReference( result ) && containsOnlyOperators( result , Operator.PLUS ) )
        {
            // maybe NUMBER1 + a + NUMBER2 or ( a + NUMBER1 ) + NUMBER2 )
            // since '+' is commutative , re-write the expression so that it becomes
            // A + ( NUMBER1 + NUMBER 2) and thus can be simplified

            // the code is based on the assumption that we can have at most one register reference
            // per expression (which is something the parse() methods assures)
            boolean tryAgain = false;
            while ( rewriteRegisterExpression( result ) ) {
                tryAgain = true;
            }
            if ( tryAgain ) 
            {
                ExpressionNode result2 = new ExpressionNode();
                result.setTextRegionIncludingAllTokens( result.getTextRegion() );
                for ( ASTNode child : result.getChildren() ) 
                {
                    if ( child instanceof TermNode) 
                    {
                        result2.addChild( ((TermNode) child).reduce( context ) , null );
                    } else {
                        result2.mergeTextRegion( child.getTextRegion() );
                    }
                }                
                result=result2;
            }
        }

        if ( result.getChildCount() == 1 && result.child(0) instanceof TermNode) 
        {
            final TermNode realResult = (TermNode) result.child(0);
            realResult.mergeWithAllTokensTextRegion( result );
            return realResult;
        }
        return result;
    }

    protected static boolean rewriteRegisterExpression(ASTNode input) {

        final List<RegisterReferenceNode> matches = ASTUtils.getNodesByType( input , RegisterReferenceNode.class , false );
        if ( ! matches.isEmpty() ) {
            ASTNode parent = matches.get(0).getParent();
            if ( parent instanceof OperatorNode ) 
            {
                final OperatorNode op = (OperatorNode) parent;
                return moveUp( op , matches.get(0) );
            } else if ( parent instanceof ExpressionNode && parent.getParent() instanceof OperatorNode) {
                final OperatorNode op = (OperatorNode) parent.getParent();
                return moveUp( op , matches.get(0) );
            }
        }
        return false;
    }

    private static boolean moveUp(OperatorNode node, RegisterReferenceNode nodeToSwap) 
    {
        /* (a+3) + 4
         * 
         *        +                 +     
         *      /  \              /  \    
         *     +    4    ===>    +   (a)   
         *    / \               / \       
         *  (a)  3             4   3      
         *   
         * 4+a+3
         * 
         *        +                 +         
         *      /  \              /  \        
         *     4    +    ===>   (a)   +       
         *         / \               / \           
         *       (a)   3             4   3          
         */
        final ASTNode parent = node.getParent();
        if ( ! ( parent instanceof OperatorNode) ) { 
            return false;
        }

        final OperatorNode op = (OperatorNode) parent;

        final TermNode child0 = op.getTerm(0);
        final TermNode child1 = op.getTerm(1);
        if ( child0.isNumberLiteral() && child1 == node ) {
            op.swapChild( child0 , nodeToSwap );
            return true;
        } else if ( child1.isNumberLiteral() && child0 == node ) {
            op.swapChild( child1 , nodeToSwap );
            return true;
        }
        return false;
    }

    @Override
	protected ExpressionNode copySingleNode()
    {
        return new ExpressionNode();
    }

    @Override
    public boolean supportsChildNodes() {
        return true;
    }	
}