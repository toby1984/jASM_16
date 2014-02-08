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
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.ast.StartMacroNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.CompilationWarning;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.IMarker;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.MacroNameSymbol;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.StringResource;
import de.codesourcery.jasm16.exceptions.DuplicateSymbolException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.Lexer;
import de.codesourcery.jasm16.lexer.Lexer.ParseOffset;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.parser.Parser;
import de.codesourcery.jasm16.scanner.IScanner;
import de.codesourcery.jasm16.scanner.Scanner;
import de.codesourcery.jasm16.utils.FormattingVisitor;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

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
	protected void run(final ICompilationUnit unit, final ICompilationContext compContext) throws IOException 
	{
        final ASTVisitor visitor = new ASTVisitor() 
        {
        	@Override
        	public void visit(InvokeMacroNode node, IIterationContext context) 
        	{
				node.removeAllChildNodes();
				
        		final StartMacroNode definition = checkInvocationValid(node,compContext.getSymbolTable() , compContext);
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
        			} else {
        				unit.addMarker( new CompilationError("Macro expansion failed", unit , node ) );
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
	
	/**
	 * 
	 * @param node
	 * @param symbolTable
	 * @param context context or <code>null</code>. If no context is given, errors will not be registered with the context
	 * @return
	 */
	private static StartMacroNode checkInvocationValid(InvokeMacroNode node,ISymbolTable symbolTable,ICompilationContext context) 
	{
		if ( node.getMacroName() == null ) {
			return null;
		}
		
		final ISymbol symbol = symbolTable.getSymbol( node.getMacroName() , null );
		
		if ( symbol == null ) 
		{
			if ( context != null ) {
				context.addCompilationError( "Unknown macro name" , node );
			}
			return null;
		}
		
		if ( ! (symbol instanceof MacroNameSymbol) ) {
			if ( context != null ) {
				context.addCompilationError( "Not a macro name" , node );
			}
			return null;
		} 
		
		final MacroNameSymbol resolvedSymbol = (MacroNameSymbol ) symbol;
		
		final int actualArgumentCount = node.getArgumentCount();
		final int expectedArgumentCount = resolvedSymbol.getMacroDefinition().getArgumentCount();
		
		if ( actualArgumentCount < expectedArgumentCount ) {
			if ( context != null ) {
				context.addCompilationError( "Too few arguments, macro requires "+expectedArgumentCount+" arguments, got only "+actualArgumentCount , node );
			}
			return null;
		} 
		if ( actualArgumentCount > expectedArgumentCount ) {
			if ( context != null ) {
				context.addCompilationError( "Too many arguments, macro requires "+expectedArgumentCount+" arguments but got "+actualArgumentCount , node );
			}
			return null;
		}
		return resolvedSymbol.getMacroDefinition();
	}
	
	private static AST expand(InvokeMacroNode invocation,StartMacroNode macroDefinition,ICompilationContext compContext) 
	{
		// map invocation parameters to macro arguments
		final Map<String, String> params = createArgumentMap(invocation,macroDefinition, compContext, compContext.getCurrentCompilationUnit() );

		// replace arguments in macro body with parameters from macro invocation
		final StringBuilder expandedBody = expandBody(macroDefinition, params , compContext.getCurrentCompilationUnit() );
		
		// parse expanded macro
		return parseExpandedBody(invocation, macroDefinition, compContext, expandedBody.toString() );
	}
	
	/**
	 * Expand macro invocation.
	 * 
	 * @param invocation
	 * @param currentCompilationUnit the compilation unit the macro invocation is in
	 * @return expanded macro invocation or <code>null</code> if expansion failed
	 */
	public static String expandInvocation(InvokeMacroNode invocation,ICompilationUnit currentCompilationUnit) {
		
		ISymbolTable table = currentCompilationUnit.getSymbolTable();
		if ( table.getParent() != null ) {
			table = table.getParent();
		}
		
		// locate macro definition
		final StartMacroNode macroDefinition = checkInvocationValid(invocation, table ,null);
		if ( macroDefinition == null ) {
			return null;
		}
		
		// map invocation parameters to macro arguments
		final Map<String, String> params = createArgumentMap(invocation,macroDefinition, null, currentCompilationUnit );

		// replace arguments in macro body with parameters from macro invocation
		final StringBuilder expandedBody = expandBody(macroDefinition, params , currentCompilationUnit );		
		return expandedBody.toString();
	}
	
	/**
	 * 
	 * @param invocation
	 * @param macroDefinition
	 * @param compContext CompContext or <code>null</code>
	 * @param currentUnit
	 * @return
	 */
	private static Map<String, String> createArgumentMap(InvokeMacroNode invocation, StartMacroNode macroDefinition, ICompilationContext compContext,ICompilationUnit currentUnit) 
	{
		final Map<String,String> params = new HashMap<>();
		
		final int argCount = macroDefinition.getArgumentCount();

		final StringBuilder source = new StringBuilder();
		
		final FormattingVisitor astPrinter = new FormattingVisitor(compContext,currentUnit,false,false) 
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

	private static StringBuilder expandBody(StartMacroNode macroDefinition, final Map<String, String> params,ICompilationUnit currentUnit) 
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

	private static AST parseExpandedBody(final InvokeMacroNode invocation,StartMacroNode macroDefinition, final ICompilationContext compContext, String expandedBody) 
	{
		final String id =  "macro_expansion_"+macroDefinition.getMacroName().getRawValue()+"_"+invocation.getTextRegion().getStartingOffset();
		
		final IResource expandedBodyResource = new StringResource( id , expandedBody , ResourceType.SOURCE_CODE );
		final ICompilationUnit unit = compContext.getCurrentCompilationUnit().withResource( expandedBodyResource );
		
		// generate fake global label so local labels within expanded macros work properly
		final ITextRegion region = new TextRegion(0,id.length());	
		Identifier globalIdentifier;
		try {
			globalIdentifier = new Identifier(id);
		} catch (ParseException e) { // should never happen unless someone changed the valid identifier syntax definition without adjusting the code here as well
			throw new RuntimeException("Internal error, failed to generate valid global label from macro name?",e);
		}
        final Label newLabel = new Label( compContext.getCurrentCompilationUnit() , region , globalIdentifier , null );	
        
		final ParseOffset bodyParseOffset = macroDefinition.getBodyParseOffset();
		final Parser parser = new Parser( compContext , bodyParseOffset ) 
		{
			@Override
			protected void parseContextCreated(IParseContext context) 
			{
		        try 
		        {
					// register global label		        	
		            context.getSymbolTable().defineSymbol( newLabel );
	            	context.storePreviousGlobalSymbol( newLabel );
		        } 
		        catch(DuplicateSymbolException e) 
		        {
		        	// should never happen since the label's ID contains the parse offset of the macro invocation
		            final String message = "Duplicate symbol '"+id+"' found at "+region+" in "+context.getCompilationUnit()+" , " +
		            		"previous definition found in "+e.getExistingDefinition().getCompilationUnit();
		            context.addCompilationError( message , null );
		        }
			}
		};
		
		// TODO: Copy parser options ?
		parser.setParserOption( ParserOption.LOCAL_LABELS_SUPPORTED, true );
		
		final AST ast = parser.parse( unit , compContext.getSymbolTable()  , expandedBody , compContext, macroDefinition );
		if ( unit.hasErrors() ) 
		{
			for ( ICompilationError i : unit.getErrors() ) 
			{
				compContext.addCompilationError( "(macro expansion): "+i.getMessage() , invocation );
			}
			return null;
		}
		
		// insert fake LabelNode into AST so SymbolReferenceNode#getPreviousGlobalLabel()
		// is able to find our artificial global label when resolving local symbols
		if ( ast.hasChildren() ) 
		{
			if ( ast.child(0) instanceof StatementNode) 
			{
				final LabelNode fakeGlobalLabel = new LabelNode();
				fakeGlobalLabel.setLabel( newLabel );
				ast.child(0).insertChild(0, fakeGlobalLabel ,  null , false );
			} else {
				throw new RuntimeException("Internal error, non-empty AST does not begin with a statement node ?");
			}
		}
		return ast;
	}
}