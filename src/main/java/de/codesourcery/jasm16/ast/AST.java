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

import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.utils.Line;

/**
 * Top of the abstract syntax tree.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class AST extends ASTNode 
{
	@Override
	protected ASTNode copySingleNode() {
		return new AST();
	}

	/**
	 * Returns the first statement that covers a specific source code offset.
	 * 
	 * @param offset
	 * @return statement or <code>null</code> if no statement could be found
	 */
	public StatementNode getFirstStatementForOffset(int offset) {
	    
	    for ( ASTNode n : getChildren() ) 
	    {
	    	if ( n instanceof StatementNode) 
	    	{
		        StatementNode stmt = (StatementNode) n;
		        if ( stmt.getTextRegion().contains( offset ) ) {
		            return stmt;
		        }
	    	}
	    }
	    return null;
	}
	
	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
        while ( ! context.eof() ) 
        {
            final int lineNumber = context.getCurrentLineNumber();
            final int lineOffset = context.getCurrentLineStartOffset();
            final ASTNode node = new StatementNode().parse( context );
            if ( node instanceof StatementNode) 
            {
                context.getCompilationUnit().setLine( new Line( lineNumber , lineOffset ) );
            } 
            addChild( node , context );
        }
        
        // make sure no .org $$$$ directive has a value
        // equal to or less than it's predecessors
        final List<OriginNode> origins = ASTUtils.getNodesByType( this , OriginNode.class , true );
        Address lastValue=null;
        for ( OriginNode n : origins ) 
        {
        	final Address value = n.getAddress();
        	if ( value == null ) {
        		continue; // parse error....
        	}
        	if ( lastValue == null ) {
        		lastValue = n.getAddress();
        	} else if ( n.getAddress().getValue() <= lastValue.getValue() ) {
        		context.getCompilationUnit().addMarker( 
       				new CompilationError("Invalid offset "+value+" , equal to or lower than the "+
       						" preceeding .org ("+lastValue+")" , context.getCompilationUnit() , n )
   				);
        	} else {
        		lastValue = n.getAddress();
        	}
        }
        return this;	    
	}
	
	@Override
	public boolean supportsChildNodes() {
	    return true;
	}

}
