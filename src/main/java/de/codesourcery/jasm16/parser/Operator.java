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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.codesourcery.jasm16.ast.ConstantValueNode;
import de.codesourcery.jasm16.ast.NumberNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.exceptions.ParseException;

/**
 * Enumeration of all supported operators.
 * 
 * <p>Note that the current expression parser only supports
 * left-associative infix operators ; the {@link #INCREMENT} 
 * and {@link #DECREMENT} operators are internally handled 
 * by {@link RegisterReferenceNode} so the normally expression parser
 * never sees those.
 * </p>
 * @author tobias.gierke@code-sourcery.de
 */
public enum Operator 
{
	INCREMENT("++",9,OperatorPosition.POSTFIX) {
		@Override
		public long calculate(Long n1, Long n2) {
			throw new UnsupportedOperationException("not possible");
		}
		
		@Override
		public int getRequiredOperandCount()
		{
		    return 1;
		}
	},
	DECREMENT("--",9,OperatorPosition.PREFIX) {
		@Override
		public long calculate(Long n1, Long n2) {
			throw new UnsupportedOperationException("not possible");
		}
        @Override
        public int getRequiredOperandCount()
        {
            return 1;
        }		
	},	
    BITWISE_OR("|",1,OperatorPosition.INFIX ) {
        @Override
        public long calculate(Long n1,Long n2) {
            return n1 | n2 ;
        }       
    },
    BITWISE_XOR("^",2,OperatorPosition.INFIX ) {
        @Override
        public long calculate(Long n1,Long n2) {
            return n1 ^ n2 ;
        }       
    },      
    BITWISE_AND("&",3,OperatorPosition.INFIX ) {
        @Override
        public long calculate(Long n1,Long n2) {
            return n1 & n2 ;
        }       
    },    
	// == !=
	EQUAL("==",4,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1, Long n2)
		{
			return n1.longValue() == n2.longValue() ? 1 : 0;
		}
		@Override
		public boolean isComparisonOperator() {
			return true;
		}		
	},	
	NOT_EQUAL("!=",4,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1, Long n2)
		{
			return n1.longValue() != n2.longValue() ? 1 : 0;			
		}
		@Override
		public boolean isComparisonOperator() {
			return true;
		}			
	},	
	// < > <= >=
	GREATER_OR_EQUAL(">=",5,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1, Long n2)
		{
			return n1 >= n2 ? 1 : 0;
		}
		@Override
		public boolean isComparisonOperator() {
			return true;
		}			
	},	
	LESS_OR_EQUAL("<=",5,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1, Long n2)
		{
			return n1 <= n2 ? 1 : 0;
		}
		@Override
		public boolean isComparisonOperator() {
			return true;
		}			
	},		
	GREATER_THAN(">",5,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1, Long n2)
		{
			return n1 > n2 ? 1 : 0;
		}
		@Override
		public boolean isComparisonOperator() {
			return true;
		}			
	},	
	LESS_THAN("<",5,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1, Long n2)
		{
			return n1 < n2 ? 1 : 0;
		}
		@Override
		public boolean isComparisonOperator() {
			return true;
		}	
	},
	LEFT_SHIFT("<<",6,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1, Long n2)
		{
			return n1 << n2;
		}
	},	
	RIGHT_SHIFT(">>",6,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1,Long n2)
		{
			return n1 >> n2;
		}
	},		
	PLUS("+",7,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1,Long n2) {
			return n1+n2;
		}
	},
	MINUS("-",7,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1,Long n2) {
			return n1-n2;
		}
	},
	MODULO("%",8,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1,Long n2) {
			return n1 % n2;
		}
	},	
	TIMES("*",8,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1,Long n2) {
			return n1*n2;
		}
	},
	DIVIDE("/",8,OperatorPosition.INFIX) {
		@Override
		public long calculate(Long n1,Long n2) {
			return n1 / n2;
		}
	},
	BITWISE_NOT("~",9,OperatorPosition.PREFIX ) {
        @Override
        public long calculate(Long n1,Long n2) {
            return ~n1;
        }	    
	},
	PARENS("(",100,OperatorPosition.PREFIX) {

        @Override
        protected long calculate(Long n1,Long n2)
        {
            throw new UnsupportedOperationException("Invoked on parens?");
        }
        
        @Override
        public int getRequiredOperandCount()
        {
            return 1;
        }
    };
	
	/**
	 * Operator precedence. 
	 * Operators with higher precedence get evaluated earlier.
	 */
	private final int precedence;
	private final OperatorPosition[] positions;
	private final String literal;
	
	/**
	 * Enumeration of possible operator positions (prefix,infix,postfix).
	 * @author tobias.gierke@code-sourcery.de
	 */
	public enum OperatorPosition {
		PREFIX,
		INFIX,
		POSTFIX;
	}
	
	protected static final class Cache 
	{
	    protected static final Map<String,Operator> OPERATORS_BY_LITERAL = new HashMap<String,Operator>();
	    
	    static {
	        for ( Operator op : Operator.values() ) {
	            OPERATORS_BY_LITERAL.put( op.literal , op );
	        }
	    }
	    
	    public static Operator fromString(String s) 
	    {
	        final Operator result = OPERATORS_BY_LITERAL.get( s );
	        if ( result == null ) {
	            throw new IllegalArgumentException("Unknown operator '"+s+"'");
	        }
	        return result;
	    }
	    
	    public static boolean isValidOperator(String s) {
	        return OPERATORS_BY_LITERAL.containsKey( s );
	    }	    
	}
	
	/**
	 * Applies this operator to constant values.
	 * 
	 * <p>This method is used by {@link TermNode#reduce(ICompilationContext)}
	 * to calculate the literal value of an expression.</p>
	 * 
	 * @param context
	 * @param n1
	 * @param n2
	 * @return
	 * @throws ParseException
	 * @throws UnsupportedOperationException if this operator cannot be used for calculating 
	 * literal values
	 */
	protected long calculate(ISymbolTable table,ConstantValueNode n1, ConstantValueNode n2) throws ParseException,UnsupportedOperationException {
		return calculate( n1.getNumericValue( table ) , n2.getNumericValue( table ) );
	}
	
	protected abstract long calculate(Long n1, Long n2) throws UnsupportedOperationException;
	
	public Long calculate(ISymbolTable table, OperatorNode node) 
	{
		final Long value1 = node.getTerm( 0 ).calculate( table );
		final TermNode term2 = node.getTerm(1);
		final Long value2 = term2 != null ? term2.calculate( table ) : null;
		
		// prefix operators receive their argument
		// as first value only
		if ( node.getOperator().isPrefixOperator() ) {
		    if ( value1 != null ) {
		        return calculate( value1 , null );
		    }
		} 
		if ( value1 != null && value2 != null )
		{
			return calculate( value1 , value2 );
		}
		return null;
	}
	/**
	 * Try to fold an {@link OperatorNode} into the corresponding
	 * literal value.
	 * 
	 * @param context
	 * @param node
	 * @return value of this expression after folding, may still resemble the
	 * input value if reducing the expression to a literal value was not successful.
	 * @see TermNode#reduce(ICompilationContext)
	 */
	public TermNode fold(ICompilationContext context , OperatorNode node) 
	{
		final TermNode term1 = node.getTerm( 0 ).reduce( context );
		final TermNode term2 = node.getTerm( 1 ).reduce( context );
		if ( term1 instanceof ConstantValueNode && term2 instanceof ConstantValueNode) 
		{
			long calculated;
			try {
				calculated = calculate( context.getSymbolTable() , (ConstantValueNode) term1, (ConstantValueNode) term2 ); 
			} catch (ParseException e) {
				throw new RuntimeException("Should not happen...",e);
			}
			return new NumberNode( calculated , node.getTextRegion() );
		}
		return new OperatorNode( node.getOperator() , term1 , term2 , node.getTextRegion() );		
	}
	
	private Operator(String literal,int precedence, OperatorPosition... positions) {
		this.literal = literal;
		this.positions = positions;
		this.precedence = precedence;
	}
	
	public boolean isComparisonOperator() {
		return false;
	}
	
	/**
	 * Returns the number of operands supported by this operator.
	 * 
	 * @return
	 */
	public int getRequiredOperandCount() 
	{
	    if ( isInfixOperator() ) {
	        return 2;
	    }
	    return 1;
	}
	
	/**
	 * Returns whether this is an prefix operator.
	 * @return
	 */
	public boolean isPrefixOperator() {
		return supportsPosition( OperatorPosition.PREFIX );
	}	
	
	/**
	 * Returns whether this is an infix operator.
	 * @return
	 */
	public boolean isInfixOperator() {
		return supportsPosition( OperatorPosition.INFIX );
	}
	
	/**
	 * Returns whether this is a postfix opperator.
	 * 
	 * @return
	 */
	public boolean isPostfixOperator() {
		return supportsPosition( OperatorPosition.POSTFIX );
	}	
	
	private boolean supportsPosition(OperatorPosition pos) 
	{
		if (pos == null) {
			throw new IllegalArgumentException("position must not be NULL");
		}
		for ( OperatorPosition actual : getSupportedPositions() ) {
			if ( actual == pos ) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Returns the positions this operator may occur at.
	 * 
	 * @return
	 */
	public OperatorPosition[] getSupportedPositions() {
		return positions;
	}
	
	/**
	 * Returns the string literal for this operator.
	 * @return
	 */
	public String getLiteral() {
		return literal;
	}

	/**
	 * Returns the operator instance for a given string literal.
	 * 
	 * @param s
	 * @return
	 * @throws IllegalArgumentException if the input does not resemble a valid operator literal
	 * @see #isValidOperator(String)
	 */
    public static Operator fromString(String s) throws IllegalArgumentException 
    {
        final Operator result = Cache.OPERATORS_BY_LITERAL.get( s );
        if ( result == null ) {
            throw new IllegalArgumentException("Unknown operator '"+s+"'");
        }
        return result;
    }
    
    /**
     * Check whether a string resembles a known operator.
     * 
     * @param s
     * @return
     */
    public static boolean isValidOperator(String s) {
        return Cache.OPERATORS_BY_LITERAL.containsKey( s );
    }	
	
    /**
     * Check whether a given string is the prefix of 
     * at least one operator.
     * 
     * <p>Note that some operators are ambiguous , the one
     * with the longest prefix needs to be matched.</p>
     *  
     * @param prefix
     * @return
     */
	public static boolean isOperatorPrefix(String prefix) 
	{
		for ( Operator op : values() ) {
			if ( op.literal.startsWith( prefix ) ) {
				return true;
			}
		}
		return false;		
	}	
	
	/**
     * Check whether a given character is the prefix of 
     * at least one operator.
     * 
     * <p>Note that some operators are ambiguous , the one
     * with the longest prefix needs to be matched.</p>
     *  	 
	 * @param prefix
	 * @return
	 */
	public static boolean isOperatorPrefix(char prefix) 
	{
		for ( Operator op : values() ) {
			if ( op.literal.charAt(0) == prefix ) {
				return true;
			}
		}
		return false;		
	}	
	
	/**
	 * Returns all operators that match a given prefix.
	 * 
	 * @param prefix
	 * @return
	 */
	public static List<Operator> getPossibleOperatorsByPrefix(char prefix) 
	{
		final List<Operator> result = new ArrayList<Operator>();
		for ( Operator op : values() ) {
			if ( op.literal.charAt(0) == prefix ) {
				result.add( op );
			}
		}
		return result;		
	}
	
	/**
	 * Picks the operator with the longest prefix match for a given input string.
	 * 
	 * @param prefix
	 * @return
	 * @throws IllegalArgumentException If no operator could be found for the input prefix
	 */
	public static Operator pickOperatorWithLongestMatch(String prefix) {
		
		List<Operator> candidates = getPossibleOperatorsByPrefix( prefix );
		if ( candidates.size() == 1 ) {
			return candidates.get(0);
		} else if ( candidates.isEmpty() ) {
			throw new IllegalArgumentException("Found no operators with prefix '"+prefix+"'");
		}
		
		// look for perfect match first
		for ( Operator op : candidates ) {
			if ( op.literal.equals( prefix ) ) {
				return op;
			}
		}
		Operator result = null;
		int matchLength=0;
		
		for ( Operator op : candidates ) 
		{
			int len = 0 ;
			for ( int i = 0 ; i < prefix.length() && i < op.literal.length()  ; i++ ) 
			{
				if ( op.literal.charAt( i ) == prefix.charAt( i ) ) {
					len++;
				}
			}
			if ( result == null || len > matchLength ) {
				matchLength = len;
				result = op;
			}
		}
		return result;
	}
	
	/**
	 * Returns all operators for which a given input string is a prefix.
	 * @param prefix
	 * @return
	 */
	public static List<Operator> getPossibleOperatorsByPrefix(String prefix) 
	{
		final List<Operator> result = new ArrayList<Operator>();
		for ( Operator op : values() ) {
			if ( op.literal.startsWith( prefix ) ) {
				result.add( op );
			}
		}
		return result;
	}
	
	/**
	 * Returns the precedence of this operator.
	 * 
	 * @return higher values mean higher precedence
	 */
	public int getPrecedence() {
		return precedence;
	}
	
}
