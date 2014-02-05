package de.codesourcery.jasm16.ast;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.jasm16.compiler.MacroNameSymbol;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

public class StartMacroNode extends ASTNode {

	private Identifier name;
	private String macroBody;
	
	public StartMacroNode() {
	}
	
	private StartMacroNode(StartMacroNode macroNode) 
	{
		this.name = macroNode.name;
		this.macroBody = macroNode.macroBody;
	}

	public Identifier getName() {
		return name;
	}
	
	@Override
	protected ASTNode copySingleNode() {
		return new StartMacroNode(this );
	}
	
	public List<Identifier> getArgumentNames() 
	{
		if ( hasChildren() ) {
			return getArgumentsNode().getArgumentNames();
		}
		return new ArrayList<>(); 
	}

	private MacroParametersListNode getArgumentsNode() {
		return (MacroParametersListNode) child(0);
	}
	
	public int getArgumentCount() {
		if ( hasChildren() ) {
			return getArgumentsNode().getArgumentCount();
		}
		return 0;
	}

	@Override
	public boolean supportsChildNodes() {
		return true;
	}

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
		final ITextRegion region = new TextRegion( context.read(TokenType.START_MACRO ) );
		try 
		{
			context.mark();
			
			region.merge( context.skipWhitespace( false ) );

			ITextRegion idRegion = new TextRegion( context.currentParseIndex() , 0 );
			this.name = context.parseIdentifier( idRegion , false);
			region.merge( idRegion );
			
			if ( context.isParsingMacroDefinition() ) {
				context.addCompilationError( "Already within macro definition of '"+context.getCurrentMacroDefinition().getName()+"() , nested definitions are not allowed", this );				
			}
			
			// update IParseContext AFTER we assigned the macro name (just in case somebody queries for the macro name )
			context.setCurrentMacroDefinition( this );
			
			if ( context.getSymbolTable().containsSymbol( this.name , null ) ) {
				context.addCompilationError( "Macro name clashes with already defined symbol", this );
			} 
			else 
			{
				// create symbol in global scope
				if ( ! context.isKeyword( this.name.getRawValue() ) ) {
					context.getSymbolTable().defineSymbol( new MacroNameSymbol( this , context.getCompilationUnit() , idRegion , this.name ) );
				} else {
					context.addCompilationError( "Invalid macro name, clashes with keyword", this );
				}
			}
			
			region.merge( context.skipWhitespace( false) );		
			
			if ( ! context.eof() && context.peek( TokenType.PARENS_OPEN ) ) {
				addChild( new MacroParametersListNode().parse(context) , context );
			}
			
			region.merge( context.skipWhitespace(false) );
			boolean markedEOLAfterStart = false;
			if ( ! context.eof() && context.peek(TokenType.EOL ) ) 
			{
				context.mark();
				region.merge( context.read() );
				markedEOLAfterStart =true;
			}			
			
			final StringBuilder buffer = new StringBuilder();
			while ( ! context.eof() && ! context.peek().hasType(TokenType.END_MACRO ) ) 
			{
				// need some funky logic here since StatementNode()#parse() wants
				// to parse everything (INCLUDING EOL) right after the actual instruction/whatever (implemented that way so that all comments can be handled in one place)
				// so we must take care NOT to consume the EOL before .end_macro
				
				if ( context.peek().isEOL() ) {
					if ( markedEOLAfterStart ) 
					{
						context.clearMark();
						markedEOLAfterStart =false;
					}
					context.mark();
				} 
				
				final IToken token = context.read();
				if ( token.isEOL() ) 
				{
					if ( ! context.eof() && context.peek(TokenType.END_MACRO) ) {
						context.reset();
						context.clearMark();
						break;
					} 
					context.clearMark();
				}
				region.merge( token );
				buffer.append( token.getContents() );
			}
			this.macroBody = buffer.toString();
			if ( buffer.length() == 0 && markedEOLAfterStart ) 
			{
				context.reset();
			}
        } 
		catch(Exception e) 
        {
            addCompilationErrorAndAdvanceParser( e , context );
            return this;
        } 
		finally 
        {
            context.clearMark();
        }
		
		mergeWithAllTokensTextRegion( region );
		return this;
	}
	
	public String getMacroBody() {
		return macroBody;
	}
}