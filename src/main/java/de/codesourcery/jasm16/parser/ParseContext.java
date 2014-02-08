/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.jasm16.parser;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.StartMacroNode;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnitResolver;
import de.codesourcery.jasm16.compiler.IMarker;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.CircularSourceIncludeException;
import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.Lexer;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.scanner.Scanner;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Default {@link IParseContext} implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ParseContext implements IParseContext 
{
	private static final Logger LOG = Logger.getLogger( ParseContext.class );
	
	private final ICompilationUnit unit;
	private final ISymbolTable symbolTable;
	private final ILexer lexer;
	private final IResourceResolver resourceResolver;
	private final ICompilationUnitResolver compilationUnitResolver;
	private final Set<ParserOption> options = new HashSet<ParserOption>();
	private final StartMacroNode currentlyExpandingMacro;
	
	// values are IResource#getIdentifier() values
	private final LinkedHashSet<String> includedSourceFiles;
	
	private ISymbol lastGlobalSymbol;
	
	private boolean recoveringFromParseError;
	
	private StartMacroNode macroBeingParsed;
	
	public ParseContext(ICompilationUnit unit , 
			ISymbolTable symbolTable,
			ILexer lexer, 
			IResourceResolver resourceResolver,
			ICompilationUnitResolver compilationUnitResolver,
			Set<ParserOption> options,
			StartMacroNode currentlyExpandingMacro) 
	{
		this( unit , symbolTable , lexer ,resourceResolver , compilationUnitResolver , options ,
				new LinkedHashSet<String>() , currentlyExpandingMacro );
	}
	
	protected ParseContext(ICompilationUnit unit , 
			ISymbolTable symbolTable,
			ILexer lexer, 
			IResourceResolver resourceResolver,
			ICompilationUnitResolver compilationUnitResolver,			
			Set<ParserOption> options,
			LinkedHashSet<String> includedSourceFiles,
			StartMacroNode currentlyExpandingMacro) 
	{
		if (lexer == null) {
			throw new IllegalArgumentException("lexer must not be NULL");
		}
		if ( unit == null ) {
			throw new IllegalArgumentException("unit must not be NULL");
		}
		if ( symbolTable == null ) {
            throw new IllegalArgumentException("symbolTable must not be NULL.");
        }
		if ( compilationUnitResolver == null ) {
			throw new IllegalArgumentException("compilationUnitResolver must not be NULL");
		}
		if ( resourceResolver == null ) {
            throw new IllegalArgumentException("resourceResolver must not be NULL.");
        }
		if ( options == null ) {
            throw new IllegalArgumentException("options must not be NULL.");
        }
		this.includedSourceFiles = includedSourceFiles;
		this.options.addAll( options );
		this.resourceResolver = resourceResolver;
		this.symbolTable = symbolTable;
		this.unit = unit;
		this.lexer = lexer;
		this.compilationUnitResolver = compilationUnitResolver;
		this.currentlyExpandingMacro = currentlyExpandingMacro;
	}
	
	@Override
	public List<IToken> advanceTo(TokenType[] expectedTypes,
			boolean advancePastMatchedToken) {
		return lexer.advanceTo(expectedTypes, advancePastMatchedToken);
	}

	@Override
	public void clearMark() {
		lexer.clearMark();
	}

	@Override
	public int currentParseIndex() {
		return lexer.currentParseIndex();
	}

	@Override
	public boolean eof() {
		return lexer.eof();
	}

	@Override
	public void mark() {
		lexer.mark();
	}

	@Override
	public IToken peek() throws EOFException {
		return lexer.peek();
	}
	
    @Override
    public boolean peek(TokenType t) throws EOFException {
    	return lexer.peek(t);
    }

	@Override
	public IToken read() throws EOFException {
		return lexer.read();
	}

	@Override
	public IToken read(TokenType expectedType) throws ParseException,
			EOFException {
		return lexer.read(expectedType);
	}

	@Override
	public void reset() {
		lexer.reset();
	}

	@Override
	public ICompilationUnit getCompilationUnit() {
		return unit;
	}

	@Override
	public ISymbolTable getSymbolTable() {
		return symbolTable;
	}

	@Override
	public int getCurrentLineNumber() {
		return lexer.getCurrentLineNumber();
	}

	@Override
	public int getCurrentLineStartOffset() {
		return lexer.getCurrentLineStartOffset();
	}
	
    @Override
    public String toString()
    {
        return eof() ? "Parser is at EOF" : peek().toString()+" ( offset "+currentParseIndex()+" )";
    }	

    public Identifier parseIdentifier(ITextRegion range,boolean localLabelsAllowed) throws EOFException, ParseException  
    {
    	if ( eof() || ! peek().hasType( TokenType.CHARACTERS ) ) 
    	{
    	    if ( ! eof() && isKeyword( peek().getContents() ) ) 
    	    {
    	    	throw new ParseException("Not a valid identifier" , peek() );
    	    }
    	    
    	    if ( ! eof() && peek().hasType(TokenType.DOT ) ) 
    	    {
    	    	if ( ! localLabelsAllowed ) {
        	    	throw new ParseException("Support for local labels disabled by configuration" , currentParseIndex() ,0 );    	    		
    	    	}
    	    	// fall-through
    	    } else {
    	    	throw new ParseException("Expected an identifier" , currentParseIndex() ,0 );
    	    }
    	}
    	
        int startOffset = currentParseIndex();  
        String chars = "";
        IToken token = peek();
        if ( token.hasType( TokenType.DOT ) ) 
        {
        	chars += read().getContents();
        	if ( range != null ) {
        		range.merge( token );
        	}
        }
        
        token = read( TokenType.CHARACTERS  );
        if ( range != null ) {
        	range.merge( token );
        }
        
        chars += token.getContents();
        int i = 0;
        for ( char c : chars.toCharArray() ) 
        {
        	if ( i == 0 && c == '.' ) { // special case: local label
        		continue;
        	}
        	
            if ( ! Identifier.isValidIdentifierChar( c ) ) {
                throw new ParseException("Character '"+c+"' is not allowed within an identifier", 
                		startOffset+i , 1 );
            }
            i++;
        }
    	if ( isKeyword( chars ) ) 
	    {
	    	throw new ParseException("Keywords are not allowed as identifier" , startOffset , chars.length() );
	    }        
        Identifier.assertValidIdentifier( chars , token );
        return new Identifier( chars );
    }

	@Override
	public ITextRegion parseWhitespace() throws EOFException, ParseException {
		return read( TokenType.WHITESPACE );
	}

	@Override
    public boolean isRecoveringFromParseError()
    {
        return recoveringFromParseError;
    }

    @Override
    public void setRecoveringFromParseError(boolean recoveringFromParseError)
    {
        this.recoveringFromParseError = recoveringFromParseError;
    }

    @Override
    public IResource resolve(String identifier) throws ResourceNotFoundException
    {
        return this.resourceResolver.resolve( identifier );
    }
    
    @Override
    public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException
    {
        return this.resourceResolver.resolveRelative( identifier , parent );        
    }

    @Override
    public void addMarker(IMarker marker)
    {
        getCompilationUnit().addMarker( marker );
    }

    @Override
    public void addCompilationError(String message, ASTNode node)
    {
        final CompilationError error = new CompilationError( message , getCompilationUnit() , node );
        error.setLineNumber( getCurrentLineNumber() );
        error.setLineStartOffset( getCurrentLineStartOffset() );
        addMarker( error );
    }

    @Override
    public IToken read(String errorMessage, TokenType expectedType) throws ParseException, EOFException
    {
        return lexer.read(errorMessage, expectedType);
    }

    public void setParserOption(ParserOption option, boolean onOff)
    {
        if ( option == null ) {
            throw new IllegalArgumentException("option must not be NULL.");
        }
        
        if ( onOff ) {
            options.add( option );
        } else {
            options.remove(option);
        }
    }

    @Override
    public boolean hasParserOption(ParserOption option)
    {
        if (option == null) {
            throw new IllegalArgumentException("option must not be NULL.");
        }
        return options.contains( option );
    }

    @Override
    public void setLexerOption(LexerOption option, boolean onOff)
    {
        lexer.setLexerOption( option, onOff);
    }

    @Override
    public boolean hasLexerOption(LexerOption option)
    {
        return lexer.hasLexerOption( option );
    }
    
    @Override
    public ICompilationUnit getCompilationUnitFor(IResource resource) throws IOException {
    	return compilationUnitResolver.getCompilationUnit( resource );
    }

	@Override
	public IParseContext createParseContextForInclude(IResource resource) throws IOException 
	{
		final String source = Misc.readSource( resource );
		final ICompilationUnit unit = compilationUnitResolver.getOrCreateCompilationUnit( resource );
		
		if ( includedSourceFiles.contains( resource.getIdentifier() ) ) 
		{
			final StringBuilder cycle = new StringBuilder();
			for (Iterator<String> it = includedSourceFiles.iterator(); it.hasNext();) {
				String id = it.next();
				cycle.append( id );
				if ( it.hasNext() ) {
					cycle.append(" <-> ");
				}
			}
			final String errorMsg ="Circular includes detected while parsing: "+getCompilationUnit().getResource()+" <-> "+
			cycle;
			LOG.error("createParseContextForInclude(): "+errorMsg);
			throw new CircularSourceIncludeException( errorMsg , getCompilationUnit() );
		}
		includedSourceFiles.add( resource.getIdentifier() );
		getCompilationUnit().addDependency( unit );
		
		final ILexer lexer = new Lexer( new Scanner( source ) );
		IParseContext result = new ParseContext(unit, symbolTable, lexer, resourceResolver,compilationUnitResolver ,  options , this.includedSourceFiles , this.currentlyExpandingMacro);
		return result;
	}

	@Override
	public String parseString(ITextRegion region) throws EOFException, ParseException 
	{
	    final IToken delimiter= read( TokenType.STRING_DELIMITER );
		if ( region != null ) {
			region.merge( delimiter );
		}
		
		final StringBuilder contents = new StringBuilder();
		final String delimiterString = delimiter.getContents();
		boolean characterQuoted = false;
		do 
		{
			if ( eof() ) {
				throw new ParseException("Unexpected EOF while looking for closing string delimiter",currentParseIndex(),0);
			}
			
			IToken tok = peek();
			
			if ( tok.isEOL() ) {
				throw new ParseException("Unexpected EOL while looking for closing string delimiter",currentParseIndex(),0);				
			}			
			
			if ( tok.hasType( TokenType.STRING_ESCAPE ) && ! characterQuoted ) 
			{
			    read();
			    
			    characterQuoted = true;
			    if ( region != null ) {
			        region.merge( tok );
			    }
                continue;
			}
			
			if ( ! characterQuoted && tok.hasType( TokenType.STRING_DELIMITER ) && tok.getContents().equals( delimiterString ) ) 
			{
		        break;
			} 
			
			read();
			
            if ( region != null ) {
                region.merge( tok );
            }			
			contents.append( tok.getContents() );
            characterQuoted = false;
		} while ( true );
		
		if ( region != null ) {
		    region.merge( read(TokenType.STRING_DELIMITER) );
		}
		return contents.toString();
	}

	@Override
	public void storePreviousGlobalSymbol(ISymbol globalSymbol) {
		if ( globalSymbol != null && globalSymbol.isLocalSymbol() ) {
			throw new IllegalArgumentException("You need to pass a GLOBAL symbol, not "+globalSymbol);
		}
		this.lastGlobalSymbol = globalSymbol;
	}

	@Override
	public ISymbol getPreviousGlobalSymbol() {
		return lastGlobalSymbol;
	}
	
	@Override
	public List<IToken> skipWhitespace(boolean skipEOL) {
		return lexer.skipWhitespace(skipEOL);
	}
	
	@Override
	public void setCurrentMacroDefinition(StartMacroNode node) {
		macroBeingParsed=node;		
	}
	
	@Override
	public StartMacroNode getCurrentMacroDefinition() {
		return macroBeingParsed;
	}
	
	@Override
    public StartMacroNode getCurrentlyExpandingMacro() {
		return this.currentlyExpandingMacro;
	}
	
	@Override
	public boolean isParsingMacroDefinition() {
		return macroBeingParsed != null;
	}

	@Override
	public boolean isKeyword(String s) 
	{
		return lexer.isKeyword(s);
	}
}