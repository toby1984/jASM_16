package de.codesourcery.jasm16.ast;

import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

public class EndMacroNode extends ASTNode {

	@Override
	protected ASTNode copySingleNode() {
		return new EndMacroNode();
	}

	@Override
	public boolean supportsChildNodes() {
		return false;
	}

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
		mergeWithAllTokensTextRegion(  context.read(TokenType.END_MACRO) );
		if ( ! context.isParsingMacroDefinition() ) {
			context.addCompilationError( "End of macro without ever starting one?",this);
		} else {
			context.setCurrentMacroDefinition( null );
		}
		return this;
	}
}