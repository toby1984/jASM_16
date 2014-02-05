package de.codesourcery.jasm16.compiler.phases;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ASTVisitor;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.InvokeMacroNode;
import de.codesourcery.jasm16.ast.StartMacroNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.MacroNameSymbol;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.Lexer;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.parser.ParseContext;
import de.codesourcery.jasm16.parser.Parser;
import de.codesourcery.jasm16.scanner.IScanner;
import de.codesourcery.jasm16.scanner.Scanner;
import de.codesourcery.jasm16.utils.FormattingVisitor;

/**
 * Responsible for expansion of macro invocations.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class ExpandMacrosPhase extends CompilerPhase 
{
	public ExpandMacrosPhase() {
		super( PHASE_EXPAND_MACROS );
	}

	@Override
	protected void run(ICompilationUnit unit, final ICompilationContext compContext) throws IOException 
	{
        final ASTVisitor visitor = new ASTVisitor() 
        {
        	@Override
        	public void visit(InvokeMacroNode node, IIterationContext context) 
        	{
				node.removeAllChildNodes();
				
        		final StartMacroNode definition = checkInvocationValid(node,compContext);
        		if ( definition != null ) 
        		{
        			final AST expanded = expand( node , definition , compContext );
        			if ( expanded != null ) 
        			{
        				for ( ASTNode statement : expanded.getChildren() ) 
        				{
        					final StatementNode stmt = (StatementNode) statement;
        					for ( ASTNode child2 : stmt.getChildren() ) 
        					{
        						node.internalAddChild( child2 );
        					}
        				}
        			}
        		}
        	}
        };
        ASTUtils.visitInOrder( unit.getAST() , visitor );
	}
	
	private StartMacroNode checkInvocationValid(InvokeMacroNode node,ICompilationContext context) 
	{
		if ( node.getMacroName() == null ) {
			return null;
		}
		
		final ISymbol symbol = context.getSymbolTable().getSymbol( node.getMacroName() , null );
		
		if ( symbol == null ) 
		{
			context.addCompilationError( "Unknown macro name" , node );
			return null;
		}
		
		if ( ! (symbol instanceof MacroNameSymbol) ) {
			context.addCompilationError( "Not a macro name" , node );
			return null;
		} 
		
		final MacroNameSymbol resolvedSymbol = (MacroNameSymbol ) symbol;
		
		final int actualArgumentCount = node.getArgumentCount();
		final int expectedArgumentCount = resolvedSymbol.getMacroDefinition().getArgumentCount();
		
		if ( actualArgumentCount < expectedArgumentCount ) {
			context.addCompilationError( "Too few arguments, macro requires "+expectedArgumentCount+" arguments, got only "+actualArgumentCount , node );			
			return null;
		} 
		if ( actualArgumentCount > expectedArgumentCount ) {
			context.addCompilationError( "Too many arguments, macro requires "+expectedArgumentCount+" arguments but got "+actualArgumentCount , node );				
			return null;
		}
		return resolvedSymbol.getMacroDefinition();
	}
	
	private AST expand(InvokeMacroNode invocation,StartMacroNode macroDefinition,ICompilationContext compContext) 
	{
		// map invocation parameters to macro arguments
		final Map<String, String> params = createArgumentMap(invocation,macroDefinition, compContext);

		// replace arguments in macro body with parameters from macro invocation
		final StringBuilder expandedBody = expandBody(macroDefinition, params);
		
		// parse expanded macro
		return parseExpandedBody(invocation, macroDefinition, compContext, expandedBody.toString() );
	}
	
	private Map<String, String> createArgumentMap(InvokeMacroNode invocation, StartMacroNode macroDefinition, ICompilationContext compContext) 
	{
		final Map<String,String> params = new HashMap<>();
		
		final int argCount = macroDefinition.getArgumentCount();

		final StringBuilder source = new StringBuilder();
		final FormattingVisitor astPrinter = new FormattingVisitor(compContext) 
		{
			@Override
			protected void output(String s) {
				source.append(s);
			}
		};
		
		final List<Identifier> argumentNames = macroDefinition.getArgumentNames();
		final List<ASTNode> arguments = invocation.getArguments();
		for ( int i = 0 ; i < argCount ; i++ ) 
		{
			final Identifier paramName = argumentNames.get(i);
			final ASTNode argument = arguments.get(i);
			
			source.setLength(0);
			ASTUtils.visitInOrder( argument , astPrinter );
			
			params.put( paramName.getRawValue() , source.toString() ); 
		}
		return params;
	}	

	private StringBuilder expandBody(StartMacroNode macroDefinition, final Map<String, String> params) 
	{
		final IScanner scanner = new Scanner( macroDefinition.getMacroBody() );
		final ILexer lexer = new Lexer(scanner);
		final StringBuilder expandedBody = new StringBuilder();

		boolean inQuote = false;
		boolean escaped = false;
		while ( ! lexer.eof() ) 
		{
			IToken tok = lexer.read();
			if ( tok.hasType( TokenType.STRING_DELIMITER ) ) {
				if ( ! escaped ) {
					inQuote = ! inQuote;
				}
				continue;
			}
			if ( ! escaped && tok.hasType(TokenType.STRING_ESCAPE ) ) {
				escaped = true;
				continue;
			}
			escaped = false;
			if ( ! inQuote && tok.hasType( TokenType.CHARACTERS ) && params.containsKey( tok.getContents() ) ) 
			{
				expandedBody.append( params.get( tok.getContents() ) );
			} else {
				expandedBody.append( tok.getContents() );
			}
		}
		return expandedBody;
	}

	private AST parseExpandedBody(final InvokeMacroNode invocation,StartMacroNode macroDefinition, final ICompilationContext compContext, final String expandedBody) 
	{
		final String id =  "macro_expansion_"+macroDefinition.getName().getRawValue()+"_"+invocation.getTextRegion().getStartingOffset();
		final Identifier identifier;
		try {
			identifier = new Identifier(id);
		} catch (ParseException e) {
			// should never happen
			throw new RuntimeException( e.getMessage() );
		}
		
		// define invocation as global scope so using local labels inside macros works
		final ICompilationUnit unit = CompilationUnit.createInstance( id , expandedBody );
		
		final Label scope = new Label(unit,invocation.getTextRegion() ,identifier, null );
		compContext.getSymbolTable().defineSymbol( scope );

		final Parser parser = new Parser( compContext ) {
			@Override
			protected void afterParseContextCreation(ParseContext context) 
			{
				super.afterParseContextCreation(context);
				context.storePreviousGlobalSymbol(  scope );
			}
		};
		parser.setParserOption( ParserOption.LOCAL_LABELS_SUPPORTED, true );
		
		final AST ast = parser.parse( unit , compContext.getSymbolTable()  , expandedBody , compContext, true);
		if ( unit.hasErrors() ) 
		{
			for ( ICompilationError i : unit.getErrors() ) 
			{
				compContext.addCompilationError( "(macro expansion): "+i.getMessage() , invocation );
			}
			return null;
		}
		return ast;
	}
}