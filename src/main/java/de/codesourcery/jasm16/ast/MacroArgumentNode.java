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
 * Uninterpreted string, used to hold macro arguments that need
 * to be passed literally to the macro invocation.
 *  
 * @author tobias.gierke@code-sourcery.de
 */
public class MacroArgumentNode extends ASTNode {

	private String value;
	
	public MacroArgumentNode() {
	}
	
	private MacroArgumentNode(MacroArgumentNode node) {
		this.value = node.value;
	}
	
	@Override
	protected MacroArgumentNode copySingleNode() {
		return new MacroArgumentNode(this);
	}

	@Override
	public boolean supportsChildNodes() {
		return false;
	}

	public String getValue() {
		return value;
	}
	
	@Override
	protected MacroArgumentNode parseInternal(IParseContext context) throws ParseException 
	{
		final StringBuilder buffer = new StringBuilder();
		
		context.skipWhitespace(false);
		int openParensCount = 0;
		while ( ! context.eof() && ! context.peek().isEOL() && ! context.peek(TokenType.COMMA ) ) 
		{
			context.skipWhitespace(false);
			if ( context.eof() || context.peek().isEOL() ||  context.peek(TokenType.COMMA ) )
			{
				break;
			}
			if ( context.peek(TokenType.PARENS_OPEN ) ) 
			{
				buffer.append( context.read().getContents() );				
				openParensCount++;
			} 
			else if ( context.peek(TokenType.PARENS_CLOSE) ) 
			{
				openParensCount--;
				if ( openParensCount == 0 ) 
				{
					buffer.append( context.read().getContents() );
					break;
				} 
				else if ( openParensCount <= 0 ) 
				{
					openParensCount=0;
					break;
				}
			} else {
				buffer.append( context.read().getContents() );
			}
		}
		if ( openParensCount != 0 ) {
			context.addCompilationError( "Mismatched parens" , this );
		}
		this.value = buffer.toString();
		return this;
	}
	
	@Override
	public String toString() {
		return "RawStringNode[ "+value+" ]";
	}
}