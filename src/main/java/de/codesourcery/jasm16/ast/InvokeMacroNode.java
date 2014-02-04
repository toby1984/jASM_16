package de.codesourcery.jasm16.ast;

import java.io.IOException;
import java.util.List;

import de.codesourcery.jasm16.compiler.*;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.parser.Parser;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

public class InvokeMacroNode extends ObjectCodeOutputNode {

	private Identifier macroName;
	
	private AST ast;
	
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
		return getChildren();
	}

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException 
	{
		ITextRegion region = new TextRegion( context.currentParseIndex() , 0 );
		this.macroName = context.parseIdentifier( region , false );
		region.merge(  context.skipWhitespace(false ) );
		if ( context.peek( TokenType.PARENS_OPEN ) ) 
		{
			// parse arguments
			region.merge( context.read(TokenType.PARENS_OPEN ) );
			
			region.merge( context.skipWhitespace( false ) );
			
			while ( ! context.eof() && ! context.peek(TokenType.PARENS_CLOSE ) ) 
			{
				addChild( new ExpressionNode().parse( context ) , context );
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
	
	@Override
	public void symbolsResolved(ICompilationContext context) 
	{
	}
	
	public AST getExpandedMacro(ICompilationContext currentContext , IParentSymbolTable table) throws IOException 
	{
		// setup symbol table where argument names resolve
		// to the arguments used in the macro invocation
		
		if ( ! table.containsSymbol( macroName , null ) || 
			 ! (table.getSymbol( macroName , null ) instanceof MacroNameSymbol ) ) 
		{
			// TODO: Reference to unknown macro
			return null;
		}
		
		final MacroNameSymbol definitionSymbol = (MacroNameSymbol) table.getSymbol( macroName , null );
		final StartMacroNode definition = definitionSymbol.getMacroDefinition();
		
		if ( definition.getArgumentCount() != getArgumentCount() ) {
			// TODO: Argument count mismatch
			return null;
		}
		
		// create symbol table
		final ICompilationUnit currentUnit= CompilationUnit.createInstance( "_macro_expansion_"+definition.getName() , definition.getMacroBody() );
		
		SymbolTable parent = new SymbolTable("_macro_expansion_"+definition.getName() );
		parent.setParent( table );
		final int argCount = getArgumentCount();
		for ( int index = 0 ; index < argCount ; index++ ) 
		{
			final Identifier argumentName = definition.getArgumentNames().get(index);
			final TermNode argument = (TermNode) getArguments().get(index);
			final Equation eq = new Equation( currentUnit , argument.getTextRegion() , argumentName , argument );  
			parent.defineSymbol( eq );
		}		

		// parse macro body
		final ICompilationUnitResolver unitResolver = new ICompilationUnitResolver() {

			@Override
			public ICompilationUnit getOrCreateCompilationUnit(IResource resource) throws IOException 
			{
				if ( resource.equals( currentUnit.getResource() ) ) {
					return currentUnit;
				}
				throw new IOException("Unknown resource: "+resource);
			}

			@Override
			public ICompilationUnit getCompilationUnit(IResource resource) throws IOException 
			{
				if ( resource.equals( currentUnit.getResource() ) ) {
					return currentUnit;
				}				
				throw new IOException("(not supported) Won't create resource "+resource);
			}
		};
		return new Parser( unitResolver ).parse( currentUnit, parent, definition.getMacroBody() ,IResourceResolver.NOP_RESOLVER , true );
	}

	@Override
	public int getSizeInBytes(long thisNodesObjectCodeOffsetInBytes) 
	{
		if ( ast == null ) {
			return UNKNOWN_SIZE;
		}
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeObjectCode(IObjectCodeWriter writer,ICompilationContext compContext) throws IOException, ParseException 
	{
		
	}
}