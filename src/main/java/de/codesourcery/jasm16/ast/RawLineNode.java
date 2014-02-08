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