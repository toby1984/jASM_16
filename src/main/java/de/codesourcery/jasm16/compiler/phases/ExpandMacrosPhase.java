package de.codesourcery.jasm16.compiler.phases;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ASTVisitor;
import de.codesourcery.jasm16.ast.EndMacroNode;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.InvokeMacroNode;
import de.codesourcery.jasm16.ast.StartMacroNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.compiler.CompilationWarning;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.IMarker;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.MacroNameSymbol;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.StringResource;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.Lexer;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.parser.Identifier;
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
        	
        	public void visit(StartMacroNode startMacroNode, IIterationContext context) 
        	{
        		ASTNode current = startMacroNode;
        		while ( !(current instanceof StatementNode) ) {
        			current = current.getParent();
        		}
        		final StatementNode stmt = (StatementNode) current;
        		final ASTNode parent = stmt.getParent();
        		int index = parent.indexOf( stmt )+1;
        		
    			final boolean[] foundStart = {false};        		
    			final boolean[] foundEnd = {false};
    	        final ASTVisitor visitor2 = new ASTVisitor() 
    	        {
    	        	public void visit(StartMacroNode node, IIterationContext context) 
    	        	{
    	        		foundStart[0]=true;
    	        		context.stop();
    	        	}
    	        	
    	        	public void visit(EndMacroNode node, IIterationContext context) 
    	        	{
    	        		foundEnd[0]=true;
    	        		context.stop();
    	        	}
    	        }; 
    	        
        		for ( ; index < parent.getChildCount() ; index++ ) 
        		{
					ASTUtils.visitInOrder( parent.child(index) , visitor2 );
					if ( ! foundStart[0] && foundEnd[0] ) {
						break;
					}
        		}
        		
        		if ( ! foundStart[0] && foundEnd[0] ) {
        			// ok
        		} else {
        			compContext.addCompilationError("Unterminated macro definition", startMacroNode);
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
		final StringBuilder expandedBody = expandBody(macroDefinition, params , compContext );
		
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

	private StringBuilder expandBody(StartMacroNode macroDefinition, final Map<String, String> params,ICompilationContext context) 
	{
		final IScanner scanner = new Scanner( macroDefinition.getMacroBody() );
		final ILexer lexer = new Lexer(scanner);
		final StringBuilder expandedBody = new StringBuilder();

		boolean inQuote = false;
		boolean escaped = false;
		final Set<String> usedParamNames = new HashSet<>();
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
			String contents = tok.getContents();
			if ( ! inQuote && tok.hasType( TokenType.CHARACTERS ) && params.containsKey( contents ) ) 
			{
				usedParamNames.add( contents );
				expandedBody.append( params.get( contents ) );
			} else {
				expandedBody.append( contents );
			}
		}
		
		/*
		 * Add warnings for unused parameters
		 */
outer:		
		for ( String key : params.keySet() ) 
		{
			if ( ! usedParamNames.contains(key) ) 
			{
				final String warningMessage = "Macro parameter '"+key+"' is never used";
				
				final ICompilationUnit currentUnit = context.getCurrentCompilationUnit();
				final List<IMarker> markers = currentUnit.getMarkers(IMarker.TYPE_COMPILATION_WARNING);
				final int len = markers.size();
				for (int i = 0; i < len ; i++) 
				{
					final IMarker m = markers.get(i);
					if ( m instanceof CompilationWarning) {
						CompilationWarning warning = (CompilationWarning) m;
						if ( warning.getNode() == macroDefinition && warningMessage.equals( warning.getMessage() ) ) 
						{
							continue outer;
						}
					}
				}
				currentUnit.addMarker( new CompilationWarning( warningMessage , currentUnit , macroDefinition ) );
			}
		}
		return expandedBody;
	}

	private AST parseExpandedBody(final InvokeMacroNode invocation,StartMacroNode macroDefinition, final ICompilationContext compContext, String expandedBody) 
	{
		final String id =  "macro_expansion_"+macroDefinition.getMacroName().getRawValue()+"_"+invocation.getTextRegion().getStartingOffset();
		
		// define invocation as global scope so local labels inside macros works
		expandedBody = id+":\n"+expandedBody;
		
		final IResource expandedBodyResource = new StringResource( id , expandedBody , ResourceType.SOURCE_CODE );
		final ICompilationUnit unit = compContext.getCurrentCompilationUnit().withResource( expandedBodyResource );
		
		final Parser parser = new Parser( compContext );
		
		// TODO: Copy parser options ?
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