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

import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;

public class MacroParametersListNode extends ASTNode {

	public MacroParametersListNode() {
	}
	
	private MacroParametersListNode(MacroParametersListNode macroArgumentListNode) 
	{
	}

	@Override
	protected ASTNode copySingleNode() {
		return new MacroParametersListNode(this);
	}

	public List<Identifier> getArgumentNames() 
	{
		final List<Identifier> result = new ArrayList<Identifier>();
		for ( ASTNode child: getChildren() ) {
			result.add( ((IdentifierNode) child).getIdentifier() );
		}
		return result;
	}
	
	@Override
	public boolean supportsChildNodes() {
		return true;
	}

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
		mergeWithAllTokensTextRegion( context.read(TokenType.PARENS_OPEN ) );
		
		mergeWithAllTokensTextRegion( context.skipWhitespace(false) );
		
		final Set<Identifier> args = new HashSet<>();
		while ( ! context.eof() && ! context.peek(TokenType.PARENS_CLOSE ) ) 
		{
			mergeWithAllTokensTextRegion( context.skipWhitespace(false) );
			IdentifierNode idNode = (IdentifierNode) new IdentifierNode().parse( context );
			if ( args.contains( idNode.getIdentifier() ) ) {
				context.addCompilationError("Duplicate macro argument name" , idNode);
			} else {
				args.add( idNode.getIdentifier() );
			}
			addChild( idNode , context );
			
			mergeWithAllTokensTextRegion( context.skipWhitespace(false) );
			
			if ( ! context.eof() && ! context.peek(TokenType.PARENS_CLOSE ) ) 
			{
				mergeWithAllTokensTextRegion( context.read(TokenType.COMMA ) );
			}
		}
		if ( ! context.eof() ) 
		{
			mergeWithAllTokensTextRegion( context.skipWhitespace(false) );			
			mergeWithAllTokensTextRegion( context.read(TokenType.PARENS_CLOSE ) );
		} else {
			context.addCompilationError( "Unterminated argument list" , this );
		}
		return this;
	}

	public int getArgumentCount() {
		return getArgumentNames().size();
	}
}