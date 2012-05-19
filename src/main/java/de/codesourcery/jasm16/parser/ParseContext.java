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
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnitResolver;
import de.codesourcery.jasm16.compiler.IMarker;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
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
	
	// values are IResource#getIdentifier() values
	private final LinkedHashSet<String> includedSourceFiles;
	
	private boolean recoveringFromParseError;
	
	public ParseContext(ICompilationUnit unit , 
			ISymbolTable symbolTable,
			ILexer lexer, 
			IResourceResolver resourceResolver,
			ICompilationUnitResolver compilationUnitResolver,
			Set<ParserOption> options) 
	{
		this( unit , symbolTable , lexer ,resourceResolver , compilationUnitResolver , options ,
				new LinkedHashSet<String>() );
	}
	
	protected ParseContext(ICompilationUnit unit , 
			ISymbolTable symbolTable,
			ILexer lexer, 
			IResourceResolver resourceResolver,
			ICompilationUnitResolver compilationUnitResolver,			
			Set<ParserOption> options,
			LinkedHashSet<String> includedSourceFiles) 
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
	}
	
	
	public List<IToken> advanceTo(TokenType[] expectedTypes,
			boolean advancePastMatchedToken) {
		return lexer.advanceTo(expectedTypes, advancePastMatchedToken);
	}

	
	public void clearMark() {
		lexer.clearMark();
	}

	
	public int currentParseIndex() {
		return lexer.currentParseIndex();
	}

	
	public boolean eof() {
		return lexer.eof();
	}

	
	public void mark() {
		lexer.mark();
	}

	
	public IToken peek() throws EOFException {
		return lexer.peek();
	}

	
	public IToken read() throws EOFException {
		return lexer.read();
	}

	
	public IToken read(TokenType expectedType) throws ParseException,
			EOFException {
		return lexer.read(expectedType);
	}

	
	public void reset() {
		lexer.reset();
	}

	
	public ICompilationUnit getCompilationUnit() {
		return unit;
	}

	
	public ISymbolTable getSymbolTable() {
		return symbolTable;
	}

	
	public int getCurrentLineNumber() {
		return lexer.getCurrentLineNumber();
	}

	
	public int getCurrentLineStartOffset() {
		return lexer.getCurrentLineStartOffset();
	}
	
    
    public String toString()
    {
        return eof() ? "Parser is at EOF" : peek().toString()+" ( offset "+currentParseIndex()+" )";
    }	

    public Identifier parseIdentifier(ITextRegion range) throws EOFException, ParseException  
    {
    	if ( eof() || ! peek().hasType( TokenType.CHARACTERS ) ) 
    	{
    	    if ( ! eof() && peek().hasType( TokenType.INSTRUCTION ) ) {
    	           throw new ParseException("Not a valid identifier (instructions cannot be used as identifiers)" , peek() );
    	    }
    		throw new ParseException("Expected an identifier" , currentParseIndex() ,0 );
    	}
    	
        int startOffset = currentParseIndex();  
        final IToken token = read( TokenType.CHARACTERS  );
        if ( range != null ) {
        	range.merge( token );
        }
        
        final String chars = token.getContents();
        int i = 0;
        for ( char c : chars.toCharArray() ) {
            if ( ! Identifier.isValidIdentifierChar( c ) ) {
                throw new ParseException("Character '"+c+"' is not allowed within an identifier", 
                		startOffset+i , 1 );
            }
            i++;
        }
        Identifier.assertValidIdentifier( chars , token );
        return new Identifier( chars );
    }

	
	public ITextRegion parseWhitespace() throws EOFException, ParseException {
		return read( TokenType.WHITESPACE );
	}

	
    public boolean isRecoveringFromParseError()
    {
        return recoveringFromParseError;
    }

    
    public void setRecoveringFromParseError(boolean recoveringFromParseError)
    {
        this.recoveringFromParseError = recoveringFromParseError;
    }

    
    public IResource resolve(String identifier, ResourceType resourceType) throws ResourceNotFoundException
    {
        return this.resourceResolver.resolve( identifier, resourceType );
    }

    
    public IResource resolveRelative(String identifier, IResource parent, ResourceType resourceType) throws ResourceNotFoundException
    {
        return this.resourceResolver.resolveRelative( identifier , parent, resourceType );        
    }

    
    public void addMarker(IMarker marker)
    {
        getCompilationUnit().addMarker( marker );
    }

    
    public void addCompilationError(String message, ASTNode node)
    {
        final CompilationError error = new CompilationError( message , getCompilationUnit() , node );
        error.setLineNumber( getCurrentLineNumber() );
        error.setLineStartOffset( getCurrentLineStartOffset() );
        addMarker( error );
    }

    
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

    
    public boolean hasParserOption(ParserOption option)
    {
        if (option == null) {
            throw new IllegalArgumentException("option must not be NULL.");
        }
        return options.contains( option );
    }

    
    public void setLexerOption(LexerOption option, boolean onOff)
    {
        lexer.setLexerOption( option, onOff);
    }

    
    public boolean hasLexerOption(LexerOption option)
    {
        return lexer.hasLexerOption( option );
    }

	
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
		return new ParseContext(unit, symbolTable, lexer, resourceResolver,compilationUnitResolver ,  options , this.includedSourceFiles );
	}

	
	public String parseString(ITextRegion region) throws EOFException, ParseException 
	{
		IToken tok = read( TokenType.STRING_DELIMITER );
		if ( region != null ) {
			region.merge( tok );
		}
		StringBuilder contents = new StringBuilder();
		do 
		{
			if ( eof() ) {
				throw new ParseException("Unexpected EOF while looking for closing string delimiter",currentParseIndex(),0);
			}
			
			tok = read();
			if ( region != null ) {
				region.merge( tok );
			}
			
			if ( tok.isEOL() ) {
				throw new ParseException("Unexpected EOL while looking for closing string delimiter",currentParseIndex(),0);				
			}			

			if ( tok.hasType( TokenType.STRING_DELIMITER ) ) {
				break; // ok
			}
			contents.append( tok.getContents() );
		} while ( true );
		return contents.toString();
	}
	
}