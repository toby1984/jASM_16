package de.codesourcery.jasm16.ast;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.jasm16.compiler.MacroNameSymbol;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.Lexer.ParseOffset;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;
import de.codesourcery.jasm16.utils.TextRegion;

public class StartMacroNode extends ASTNode {

	private Identifier name;
	private String macroBody;
	
	private ParseOffset bodyParseOffset;
	
	public StartMacroNode() {
	}
	
	private StartMacroNode(StartMacroNode macroNode) 
	{
		this.name = macroNode.name;
		this.macroBody = macroNode.macroBody;
		this.bodyParseOffset = macroNode.bodyParseOffset != null ? new ParseOffset( macroNode.bodyParseOffset ) : null;
	}

	public Identifier getMacroName() {
		return name;
	}
	
	@Override
	protected ASTNode copySingleNode() {
		return new StartMacroNode(this );
	}
	
	public List<Identifier> getArgumentNames() 
	{
		if ( hasArgumentList() ) {
			return getArgumentsNode().getArgumentNames();
		}
		return new ArrayList<>(); 
	}
	
	public boolean hasArgumentList() {
		return hasChildren() && child(0) instanceof MacroParametersListNode;
	}

	private MacroParametersListNode getArgumentsNode() {
		return (MacroParametersListNode) child(0);
	}
	
	public int getArgumentCount() {
		if ( hasArgumentList() ) {
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
				context.addCompilationError( "Already within macro definition of '"+context.getCurrentMacroDefinition().getMacroName()+"() , nested definitions are not allowed", this );				
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
			
			this.macroBody = "";
			if ( ! context.eof() && context.peek().isEOL() && ! isNextLineEndOfMacro( context ) ) 
			{
				// consume newline
				region.merge( context.read() );
			}

			// lexer is now at start of next line after .macro
			this.bodyParseOffset = new ParseOffset( context.currentParseIndex() , context.getCurrentLineNumber() , context.getCurrentLineStartOffset() );
			
			final StringBuilder buffer = new StringBuilder();
			while ( ! context.eof() && ! isNextLineEndOfMacro( context ) ) 
			{
	            final int lineNumber = context.getCurrentLineNumber();
	            final int lineOffset = context.getCurrentLineStartOffset();
				RawLineNode line = (RawLineNode) new RawLineNode().parse( context );
				if ( line.getContents().length() > 0 ) {
					context.getCompilationUnit().setLine( new Line(lineNumber,lineOffset ) );
					addChild( line , context );
					buffer.append( line.getContents() );
				}
				if ( ! context.eof() && context.peek().isEOL() && ! isNextLineEndOfMacro( context ) ) 
				{
					buffer.append( context.read().getContents() ); // consume newline
				}
			}
			this.macroBody = buffer.toString();
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
	
	private boolean isNextLineEndOfMacro(IParseContext context) 
	{
		if ( context.eof() ) {
			return true;
		}
		if ( ! context.eof() && context.peek().isEOL() ) 
		{
			context.mark();
			context.read(); // advance so we can peek at next token 
			if ( context.eof() || context.peek(TokenType.END_MACRO ) ) {
				context.reset();
				return true;
			}
			context.reset();
			context.clearMark();
		}
		return false;
	}
	
	public String getMacroBody() {
		return macroBody;
	}
	
	public ParseOffset getBodyParseOffset() {
		return this.bodyParseOffset;
	}
}