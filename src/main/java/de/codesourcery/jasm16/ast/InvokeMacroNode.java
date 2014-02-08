package de.codesourcery.jasm16.ast;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

public class InvokeMacroNode extends ASTNode {

	private Identifier macroName;
	private final List<ASTNode> arguments=new ArrayList<>(); 
	
	public InvokeMacroNode() {
	}
	
	public InvokeMacroNode(InvokeMacroNode macroInvocationNode) {
		this.macroName = macroInvocationNode.macroName;
	}

	@Override
	protected ASTNode copySingleNode() {
		return new InvokeMacroNode(this);
	}

	@Override
	public boolean supportsChildNodes() {
		return true;
	}
	
	public int getArgumentCount() {
		return getArguments().size();
	}
	
	public List<ASTNode> getArguments() {
		return arguments;
	}

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
		ITextRegion region = new TextRegion( context.currentParseIndex() , 0 );
		this.macroName = context.parseIdentifier( region , false );
		
		if ( context.getCurrentlyExpandingMacro() != null ) {
			context.addCompilationError("Sorry, invoking macros from another macro is currently not implemented",this);
		}
		// actual existance of invoked macro is checked by ExpandMacrosPhase
		
		region.merge(  context.skipWhitespace(false ) );
		if ( ! context.eof() && context.peek( TokenType.PARENS_OPEN ) ) 
		{
			// parse arguments
			region.merge( context.read(TokenType.PARENS_OPEN ) );
			
			region.merge( context.skipWhitespace( false ) );
			
			while ( ! context.eof() && ! context.peek(TokenType.PARENS_CLOSE ) ) 
			{
				arguments.add( new MacroArgumentNode().parse( context ) );
				region.merge( context.skipWhitespace( false ) );
				if ( context.eof() || context.peek(TokenType.PARENS_CLOSE ) ) {
					break;
				}
				region.merge( context.read(TokenType.COMMA ) );
			}
			region.merge( context.read(TokenType.PARENS_CLOSE ) );
		}
		mergeWithAllTokensTextRegion( region );
		return this;
	}

	public Identifier getMacroName() {
		return this.macroName;
	}	
}