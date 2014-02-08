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

import de.codesourcery.jasm16.ast.*;
import de.codesourcery.jasm16.compiler.*;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.CircularSourceIncludeException;
import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * Provides context information used during the parsing process.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IParseContext extends ILexer , IResourceResolver {

    /**
     * Check whether a specific parser option is enabled.
     * 
     * @param option
     * @return
     */
    public boolean hasParserOption(ParserOption option);

    /**
     * Returns the compilation unit ('file') currently being parsed.
     * 
     * @return
     */
    public ICompilationUnit getCompilationUnit();

    /**
     * Returns the symbol table.
     * 
     * <p>Since we're parsing assembler, there is only one (global)
     * symbol table that's shared across all compilation units.</p>
     * 
     * @return
     */
    public ISymbolTable getSymbolTable();
    
    /**
     * Returns whether a given string matches a keyword (case-insensitive).
     * 
     * @param s
     * @return
     */
    public boolean isKeyword(String s);

    /**
     * Parse an identifier starting at the current parse position.
     * 
     * <p>This method will consume all parsed characters from the input.</p>
     * 
     * @param range range to merge parsed character range with, may be <code>null</code>
     * @return
     * @throws EOFException
     * @throws ParseException if no (valid) identifier could be found at the current
     * parse position.
     * 
     * @see ASTNode#getTextRegion()
     * @see LabelNode
     * @see Identifier
     */
    public Identifier parseIdentifier(ITextRegion range,boolean localLabelsAllowed) throws EOFException, ParseException;

    /**
     * Parse whitespace at the current parse position.
     * 
     * @return
     * @throws EOFException
     * @throws ParseException If no whitespace could be read at the current parse position.
     * @see TokenType#WHITESPACE
     */
    public ITextRegion parseWhitespace() throws EOFException,ParseException;	
    
    /**
     * Parses a string enclosed by string delimiters.
     * 
     * @param region region to merge text regions from consumed tokens with, may be <code>null</code> 
     * @return
     * @throws EOFException
     * @throws ParseException
     */
    public String parseString(ITextRegion region) throws EOFException,ParseException;

    /**
     * Set whether the parser is currently trying to recover from a parse error.
     * 
     * <p>See {@link ASTNode#parse(IParseContext)} for a detailed description of the parser's error
     * recovery mechanism.</p>
     * 
     * @param yesNo
     */
    public void setRecoveringFromParseError(boolean yesNo);

    /**
     * Check whether the parser is currently trying to recover from a parse error.
     * 
     * <p>See {@link ASTNode#parse(IParseContext)} for a detailed description of the parser's error
     * recovery mechanism.</p>
     *      
     * @return
     */
    public boolean isRecoveringFromParseError();	

    /**
     * Add a marker to the current compilation unit.
     * 
     * @see ICompilationUnit#addMarker(de.codesourcery.jasm16.compiler.IMarker)
     */
    public void addMarker(IMarker marker);

    /**
     * Add a compilation error.
     * 
     * <p>Convenience method that uses {@link ICompilationUnit#addMarker(IMarker)} 
     * to add an error that encompasses a specific AST node to the current compilation unit.</p>
     * @param message
     * @param node
     * @see ICompilationUnit#addMarker(IMarker)
     */
    public void addCompilationError(String message, ASTNode node); 
    
    public ICompilationUnit getCompilationUnitFor(IResource resource) throws IOException;
    
    /**
     * Invoked to create a new <code>ParseContext</code> whenever a
     * source include needs to be processed.
     * 
     * @param resource
     * @return new parse context 
     * @throws IOException 
     */
    public IParseContext createParseContextForInclude(IResource resource) throws IOException,CircularSourceIncludeException;
    
    /**
     * Used to keep track of the last global symbol that was parsed.
     * 
     * @param globalSymbol global symbol or <code>null</code>
     * 
     * @see LabelNode
     * @see ISymbol#isLocalSymbol()
     * @see #getPreviousGlobalSymbol()
     */
    public void storePreviousGlobalSymbol(ISymbol globalSymbol);
    
    /**
     * Returns the last global symbol seen in this compilation unit.
     * 
     * @return previous global symbol or <code>null</code>
     * @see #storePreviousGlobalSymbol(ISymbol)
     */
    public ISymbol getPreviousGlobalSymbol();    
    
    /**
     * Sets the AST node of the macro definition that is currently being parsed.
     *  
     * @param node AST node or <code>null</code>
     */
    public void setCurrentMacroDefinition(StartMacroNode node);
    
    /**
     * Returns the AST node of the macro definition that is currently being parsed.
     *  
     * @param node AST node or <code>null</code>
     */    
    public StartMacroNode getCurrentMacroDefinition();
    
    /**
     * Returns whether we're currently parsing a macro definition.
     * 
     * @return
     * @see #getCurrentMacroDefinition()
     * @see #setCurrentMacroDefinition(StartMacroNode)
     */
    public boolean isParsingMacroDefinition();
    
    /**
     * Returns the macro definition that is currently being expanded.
     * 
     * @return Macro definition or <code>null</code> if the parser is not parsing an expanded macro body.
     */
    public StartMacroNode getCurrentlyExpandingMacro();
}