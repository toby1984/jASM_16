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
import java.util.List;

import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * AST node that covers a source code region that failed to parse correctly.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class UnparsedContentNode extends ASTNode
{
    private final String error;
    private final int errorOffset;
    
    public UnparsedContentNode(String error, int errorOffset ) 
    {
    	this(error,errorOffset,new ArrayList<IToken>() );
    }
    
    public UnparsedContentNode(String error, int errorOffset , List<IToken> tokens) 
    {
        this.error = error;
        this.errorOffset = errorOffset;
        if ( ! tokens.isEmpty() ) {
            setTextRegionIncludingAllTokens( new TextRegion( tokens ) );
        } else {
            setTextRegionIncludingAllTokens( new TextRegion( errorOffset , 0 ) );
        }
    }
    
    public String getError()
    {
        return error;
    }
    
    public int getErrorOffset()
    {
        return errorOffset;
    }
    
    @Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
        throw new UnsupportedOperationException();
    }

    @Override
	protected UnparsedContentNode copySingleNode()
    {
        return new UnparsedContentNode(this.error, this.errorOffset );
    }

    @Override
    public boolean supportsChildNodes() {
        return false;
    }    
    
    @Override
    public String toString()
    {
        return "UnparsedContentNode[ errorOffset = "+errorOffset+" , region = "+getTextRegion()+"]";
    }
}
