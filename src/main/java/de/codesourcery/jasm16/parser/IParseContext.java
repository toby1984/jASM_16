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

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.IMarker;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.utils.ITextRange;

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
     * @see ASTNode#getTextRange()
     * @see LabelNode
     * @see Identifier
     */
    public Identifier parseIdentifier(ITextRange range) throws EOFException, ParseException;

    /**
     * Parse whitespace at the current parse position.
     * 
     * @return
     * @throws EOFException
     * @throws ParseException If no whitespace could be read at the current parse position.
     * @see TokenType#WHITESPACE
     */
    public ITextRange parseWhitespace() throws EOFException,ParseException;	

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

    public void addCompilationError(String message, ASTNode node); 
}
