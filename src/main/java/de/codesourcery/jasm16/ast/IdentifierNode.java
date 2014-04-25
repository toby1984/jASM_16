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
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

public class IdentifierNode extends ASTNode {

	private Identifier identifier;
	
	public IdentifierNode() {
	}
	
	private IdentifierNode(IdentifierNode n) {
		this.identifier = n.identifier;
	}
	
	@Override
	protected IdentifierNode copySingleNode() {
		return new IdentifierNode(this);
	}

	@Override
	public boolean supportsChildNodes() {
		return false;
	}

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
		ITextRegion r = new TextRegion( context.peek() );
		this.identifier = context.parseIdentifier( r , false );
		mergeWithAllTokensTextRegion( r );
		return this;
	}

	public Identifier getIdentifier() {
		return identifier;
	}
}
