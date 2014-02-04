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
