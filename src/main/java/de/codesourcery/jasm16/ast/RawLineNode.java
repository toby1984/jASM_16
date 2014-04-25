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
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.parser.IParseContext;

public class RawLineNode extends ASTNode {

	private String contents;
	
	public RawLineNode() {
	}
	
	private RawLineNode(RawLineNode other) {
		this.contents = other.contents;
	}
	
	@Override
	protected RawLineNode copySingleNode() {
		return new RawLineNode( this );
	}

	@Override
	public boolean supportsChildNodes() {
		return false;
	}

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
		StringBuilder builder = new StringBuilder();
		while ( ! context.eof() && ! context.peek().isEOL() ) 
		{
			final IToken token = context.read();
			mergeWithAllTokensTextRegion( token );
			builder.append( token.getContents() );
		}
		this.contents = builder.toString();
		return this;
	}

	public String getContents() {
		return contents;
	}
}