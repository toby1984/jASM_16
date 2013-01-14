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

import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

/**
 * An AST node that represents a source code comment.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class CommentNode extends ASTNode
{
	private String value;
	
    @Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
    	mergeWithAllTokensTextRegion( context.read( TokenType.SINGLE_LINE_COMMENT) );

        while( ! context.eof() && ! context.peek().isEOL() ) 
        {
        	mergeWithAllTokensTextRegion( context.read() );
        }
        if ( ! context.eof() ) {
        	mergeWithAllTokensTextRegion( context.read( TokenType.EOL ) );
        }
        return this;
    }
    
    @Override
	protected CommentNode copySingleNode()
    {
    	final CommentNode result= new CommentNode();
    	result.value = value;
    	return result;
    }    
    
    @Override
    public boolean supportsChildNodes() {
        return false;
    }
}
