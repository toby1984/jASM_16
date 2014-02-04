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
package de.codesourcery.jasm16.lexer;

import java.io.InputStream;
import java.util.List;

import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.scanner.IScanner;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * Lexer that tokenizes an input stream returned by {@link IScanner}.
 * 
 * <p>Each token returned by this lexer provides access
 * to the string contents that make up the token and
 * the {@link ITextRegion} in the input stream where the 
 * token was parsed from.</p>
 * 
 * <p>Note that this lexer provides a way of creating
 * checkpoints in the parsed token stream (analog to the
 * {@link InputStream#mark(int)} / {@link InputStream#reset()} method)
 * to help the parser in recovering from parse errors.
 * </p>
 * @author tobias.gierke@code-sourcery.de
 */
public interface ILexer {
    
    public enum LexerOption {
        CASE_INSENSITIVE_OPCODES;
    }
    
    /**
     * Enable/disable a compiler flag.
     * 
     * @param option
     * @param onOff
     */
    public void setLexerOption(LexerOption option,boolean onOff);
    
    /**
     * Check whether a specific compiler option is enabled.
     * 
     * @param option
     * @return
     */
    public boolean hasLexerOption(LexerOption option);    

	/**
	 * Check whether the lexer has reached the end of input.
	 * @return
	 */
	public boolean eof();
	
	/**
	 * Peek at the next token in the input stream.
	 * @return
	 * @throws EOFException
	 */
	public IToken peek() throws EOFException;
	
	/**
	 * Check whether the the next token in the input stream has a specific type.
	 * @return <code>false</code> if the next token is not of the given type
	 */
	public boolean peek(TokenType t) throws EOFException;	
	
	/**
	 * Read the next token from the input stream.
	 * 
	 * @return
	 * @throws EOFException
	 */
	public IToken read() throws EOFException;
	
	/**
	 * Returns the current parse index in the input stream.
	 * 
	 * @return
	 */
	public int currentParseIndex();
	
	/**
	 * Returns the starting offset of the last line seen by the lexer.
	 * @return
	 * @see TokenType#EOL
	 */
	public int getCurrentLineStartOffset();
	
	/**
	 * Returns the number of the line the lexer is currently parsing.
	 * 
	 * @return
	 * @see TokenType#EOL
	 */
	public int getCurrentLineNumber();
	
	/**
	 * Reads a token with a specific type from the input stream.
	 * 
	 * @param errorMessage 
	 * @param expectedType
	 * @return
	 * @throws ParseException if the input stream yielded a token different
	 * than the expected type
	 * @throws EOFException
	 */
	public IToken read(String errorMessage,TokenType expectedType) throws ParseException,EOFException;
	
    /**
     * Reads a token with a specific type from the input stream.
     * 
     * @param expectedType
     * @return
     * @throws ParseException if the input stream yielded a token different
     * than the expected type
     * @throws EOFException
     */	
    public IToken read(TokenType expectedType) throws ParseException,EOFException;
    
	/**
	 * Advance until either a token with a specific type is found or EOL
	 * is encountered.
	 * 
	 * @param expectedType
	 * @param advancePastMatchedToken whether to include the matched token into the result
	 * or stop lexing right in front of it
	 * @return skipped tokens
	 * @see TokenType#EOL
	 */
	public List<IToken> advanceTo(TokenType[] expectedTypes,boolean advancePastMatchedToken);
	
	/**
	 * Remember the lexer's current internal state.
	 *
	 * <p>The lexer's internal state consists of the current line number, line starting offset and
	 * current parse index.</p>
	 * 
	 * <p>The <code>mark()</code> / <code>reset()</code> mechanism
	 * uses a stack , so each call to <code>mark()</code> pushes the lexer's current state
	 * onto the stack and each call to <code>reset()</code> pop's the last parse position off this stack.
	 * </p>
	 * @see #reset()
	 * @see #clearMark()
	 */
	public void mark();
	
	/**
	 * Removes the last remembered lexer state from the internal stack.
	 * 
	 * <p>
	 * This method does nothing if {@link #mark()} was never called.
	 * </p>
	 * @see #mark()
	 * @see #reset()
	 */
	public void clearMark();
	
	/**
	 * Consumes whitespace/EOL tokens until either EOF or a non-whitespace/non-EOL token is encountered.
	 * 
	 * @param skipEOL whether to consume EOL tokens as well
	 * @return the consumed tokens
	 * 
	 * @see IToken#isWhitespace()
	 * @see IToken#isEOL()
	 */
	public List<IToken> skipWhitespace(boolean skipEOL);
	
	/**
	 * Reset's the lexer's internal state to how it was the last
	 * time {@link #mark()} got called. 
	 * 
	 * @throws IllegalStateException if {@link #mark()} has never been called
	 */
	public void reset() throws IllegalStateException;
	
    /**
     * Returns whether a given string matches a keyword (case-insensitive).
     * 
     * @param s
     * @return
     */
    public boolean isKeyword(String s);	
}
