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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.OpCode;
import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.Operator;
import de.codesourcery.jasm16.scanner.IScanner;
import de.codesourcery.jasm16.utils.NumberLiteralHelper;

/**
 * Default {@link ILexer} implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class Lexer implements ILexer {

    private final IScanner scanner;

    private final StringBuilder buffer = new StringBuilder();	
    private final List<IToken> currentTokens=new ArrayList<IToken>();
    private final Stack<State> marks = new Stack<State>();
    private final Set<LexerOption> options = new HashSet<LexerOption>(); 

    private boolean caseSensitiveOpCodes = true;
    
    private int currentLineNumber=1;
    private int currentLineStartOffset;

    protected static final class State 
    {
        private final List<IToken> markedTokens = new ArrayList<IToken>();
        private final int scannerOffset;
        private final int lineNumber;
        private final int lineStartOffset;

        protected State(List<IToken> tokens,int scannerOffset,int lineNumber,int lineStartOffset) 
        {
            this.markedTokens.addAll( tokens );
            this.scannerOffset = scannerOffset;
            this.lineNumber = lineNumber;
            this.lineStartOffset = lineStartOffset;
        }
    }

    public Lexer(IScanner scanner) {
        this.scanner = scanner;
    }	

    
    public void mark()
    {
        marks.push( new State( this.currentTokens , 
                scanner.currentParseIndex() , 
                currentLineNumber ,
                currentLineStartOffset) );
    }

    
    public void clearMark() {
        if ( marks.isEmpty() ) {
            throw new IllegalStateException("Must call mark() first");
        }
        marks.pop();
    }	

    
    public void reset() throws IllegalStateException
    {
        if ( marks.isEmpty() ) {
            throw new IllegalStateException("Must call mark() first");
        }
        final State state = marks.peek();
        scanner.setCurrentParseIndex( state.scannerOffset );
        currentTokens.clear();
        currentTokens.addAll( state.markedTokens );
        currentLineNumber = state.lineNumber;
        currentLineStartOffset = state.lineStartOffset;
    }

    private void parseNextToken() 
    {
        if ( scanner.eof() ) {
            return;
        }

        // clear buffer
        buffer.setLength(0);

        // skip whitespace
        int startIndex = scanner.currentParseIndex();		
        while ( ! scanner.eof() && isWhitespace( scanner.peek() ) ) 
        {
            buffer.append( scanner.read() );
        }

        if ( buffer.length() > 0 ) {
            currentTokens.add( new Token( TokenType.WHITESPACE , buffer.toString(), startIndex ) );
        }

        if ( scanner.eof() ) {
            return;
        }

        startIndex = scanner.currentParseIndex();
        char currentChar = scanner.peek();
        buffer.setLength( 0 );

        while ( ! scanner.eof() ) 
        {
            currentChar = scanner.peek();

            switch( currentChar ) 
            {
                case ' ': // whitespace
                case '\t': // whitespace
                    handleString( buffer.toString() , startIndex );
                    return;
                case ';': // single-line comment
                    handleString( buffer.toString() , startIndex );
                    startIndex = scanner.currentParseIndex();
                    scanner.read();
                    currentTokens.add( new Token(TokenType.SINGLE_LINE_COMMENT, ";" , scanner.currentParseIndex()-1 ) );
                    return; 
                case '"': // string delimiter
                    handleString( buffer.toString() , startIndex );
                    startIndex = scanner.currentParseIndex();
                    scanner.read();
                    currentTokens.add( new Token(TokenType.STRING_DELIMITER, "\"" , scanner.currentParseIndex()-1 ) );
                    return;			    

                case '\n':          // parse unix-style newline
                    handleString( buffer.toString() , startIndex );
                    startIndex = scanner.currentParseIndex();
                    scanner.read();
                    currentTokens.add( new Token(TokenType.EOL, "\n" , scanner.currentParseIndex()-1 ) );
                    return;
                case '\r': // parse DOS-style newline
                    buffer.append( scanner.read() );				
                    if ( ! scanner.eof() && scanner.peek() == '\n' ) 
                    {
                        handleString( buffer.toString() , buffer.length()-1 , startIndex );
                        scanner.read();					
                        currentTokens.add(  new Token(TokenType.EOL, "\r\n" , scanner.currentParseIndex()-2 ) );
                        return;
                    }
                    continue;
                case ':': 
                    handleString( buffer.toString() , startIndex );
                    scanner.read();     
                    currentTokens.add(  new Token(TokenType.COLON , ":" , scanner.currentParseIndex()-1 ) );
                    return;
                case '(': 
                    handleString( buffer.toString() , startIndex );
                    scanner.read();     
                    currentTokens.add(  new Token(TokenType.PARENS_OPEN , "(" , scanner.currentParseIndex()-1) );
                    return;
                case ')':
                    handleString( buffer.toString() , startIndex );             
                    scanner.read();     
                    currentTokens.add( new Token(TokenType.PARENS_CLOSE, ")" , scanner.currentParseIndex()-1 ) );
                    return;
                case '[': 
                    handleString( buffer.toString() , startIndex );
                    scanner.read();     
                    currentTokens.add(  new Token(TokenType.ANGLE_BRACKET_OPEN , "[" , scanner.currentParseIndex()-1) );
                    return;
                case ']':
                    handleString( buffer.toString() , startIndex );             
                    scanner.read();     
                    currentTokens.add( new Token(TokenType.ANGLE_BRACKET_CLOSE, "]" , scanner.currentParseIndex()-1 ) );
                    return;
                case ',':
                    handleString( buffer.toString() , startIndex ); 
                    scanner.read();     
                    currentTokens.add(  new Token(TokenType.COMMA , "," , scanner.currentParseIndex()-1 ) );
                    return;
            }			

            if ( Operator.isOperatorPrefix( currentChar ) ) 
            {
                parseOperator( startIndex );
                return;
            }

            // ...keep the rest...some unrecognized character sequence
            buffer.append( scanner.read() );
        }

        handleString( buffer.toString() , startIndex );
    }

    private void parseOperator(int lastStartIndex) 
    {
        handleString( buffer.toString() , lastStartIndex );
        buffer.setLength( 0 );

        // consume first character
        final int startIndex = scanner.currentParseIndex();
        buffer.append( scanner.read() );

        List<Operator> possibleOperators = Operator.getPossibleOperatorsByPrefix( buffer.toString() );
        while ( ! scanner.eof() && ( possibleOperators.size() > 1 || ( possibleOperators.size() == 1 && ! Operator.isValidOperator( buffer.toString() ) ) ) ) 
        {
            char peek = scanner.peek();

            if ( Operator.isOperatorPrefix( buffer.toString()+peek ) ) 
            {
                buffer.append( scanner.read() );
                possibleOperators = Operator.getPossibleOperatorsByPrefix( buffer.toString() );        		
            } else {
                break;
            }
        }

        final String operator;
        if ( possibleOperators.size() > 1 ) {
            operator = Operator.pickOperatorWithLongestMatch( buffer.toString() ).getLiteral();
        } else {
            operator = buffer.toString();
        }
        currentTokens.add(  new Token( TokenType.OPERATOR , operator , startIndex ) );
    }

    private void handleString(String buffer, int startIndex) 
    {
        handleString(buffer,buffer.length() , startIndex );
    }

    private void handleString(String s, int length , int startIndex) 
    {
        if ( s.length() == 0 || length <= 0 ) {
            return;
        }

        final String buffer = s.substring(0,length);

        OpCode opCode = caseSensitiveOpCodes ? OpCode.fromIdentifier( buffer ) : OpCode.fromIdentifier( buffer.toUpperCase() );
        if ( opCode != null ) {
            currentTokens.add(  new Token( TokenType.INSTRUCTION , buffer , startIndex ) );
            return;
        } 
        
        if ( NumberLiteralHelper.isNumberLiteral( buffer ) ) {
            currentTokens.add( new Token(TokenType.NUMBER_LITERAL , buffer , startIndex ) );
            return;
        }        

        if ( "push".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.PUSH , buffer , startIndex ) );
            return ;
        }

        if ( "pop".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.POP , buffer , startIndex ) );
            return ;
        }		
        
        if ( "pick".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.PICK , buffer , startIndex ) );
            return ;        	
        }

        if ( "peek".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.PEEK , buffer , startIndex ) );
            return ;
        }  
        
        if ( ".byte".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.INITIALIZED_MEMORY_BYTE , buffer , startIndex ) );
            return ;
        }
        
        if ( "pack".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.INITIALIZED_MEMORY_PACK , buffer , startIndex ) );
            return ;        	
        }
        
        if ( ".word".equalsIgnoreCase( buffer ) || "dat".equalsIgnoreCase( buffer ) || ".dat".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.INITIALIZED_MEMORY_WORD , buffer , startIndex ) );
            return ;
        }      
        
        if ( "reserve".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.UNINITIALIZED_MEMORY_WORDS , buffer , startIndex ) );
            return ;
        }           

        if ( ".bss".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.UNINITIALIZED_MEMORY_BYTES , buffer , startIndex ) );
            return ;
        }		
        
        if ( "org".equalsIgnoreCase( buffer )  || ".org".equalsIgnoreCase( buffer )  || ".origin".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.ORIGIN , buffer , startIndex ) );
            return ;
        }
        
        if ( ".include".equals( buffer ) || "include".equalsIgnoreCase( buffer) || ".incsource".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.INCLUDE_SOURCE, buffer , startIndex ) );
            return ;        	
        }
        
        if ( ".incbin".equalsIgnoreCase( buffer ) || "incbin".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.INCLUDE_BINARY , buffer , startIndex ) );
            return ;
        }
        
        if ( ".equ".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.EQUATION , buffer , startIndex ) );
            return ;        	
        }

        if ( ".".equals( buffer ) ) {
            currentTokens.add( new Token(TokenType.DOT, "." , startIndex ) );
            return;
        } else if ( buffer.startsWith("." ) ) {
            currentTokens.add( new Token(TokenType.DOT, "." , startIndex ) );
            currentTokens.add( new Token( TokenType.CHARACTERS , buffer.substring( 1 , buffer.length() ) , startIndex+1 ) );            
            return;
        }
        currentTokens.add(  new Token( TokenType.CHARACTERS , buffer , startIndex ) );
    }

    private static boolean isWhitespace(char c ) {
        return c == ' ' || c == '\t';
    }

    private IToken currentToken() 
    {
        if ( currentTokens.isEmpty() ) 
        {
            parseNextToken();
            if ( currentTokens.isEmpty() ) {
                return null;
            }
            return currentTokens.get(0);
        } 
        return currentTokens.get(0);
    }

    
    public boolean eof() 
    {
        return currentToken() == null;
    }

    
    public IToken peek() throws EOFException
    {
        if ( eof() ) {
            throw new EOFException("Premature end of file",currentParseIndex() );
        }
        return currentToken();
    }

    
    public IToken read() throws EOFException
    {
        if ( eof() ) {
            throw new EOFException("Premature end of file",currentParseIndex() );			
        }		
        final IToken result = currentToken();
        currentTokens.remove( 0 );

        if ( result.isEOL() ) {
            currentLineNumber++;
            currentLineStartOffset = result.getStartingOffset()+1;
        }
        return result;
    }

    
    public int currentParseIndex()
    {
        final IToken tok = currentToken();
        return tok != null ? tok.getStartingOffset() : scanner.currentParseIndex();
    }
    
    
    public IToken read(TokenType expectedType) throws ParseException,EOFException
    {    
        return read((String) null,expectedType);
    }

    
    public IToken read(String errorMessage, TokenType expectedType) throws ParseException,EOFException
    {
        final IToken tok = peek();
        if ( tok.getType() != expectedType ) 
        {
            if ( StringUtils.isBlank( errorMessage )  ) 
            {
                if ( expectedType != TokenType.EOL && expectedType != TokenType.WHITESPACE ) {
                    throw new ParseException( "Expected token of type "+expectedType+" but got '"+tok.getContents()+"'", tok );
                } 
                throw new ParseException( "Expected token of type "+expectedType+" but got "+tok.getType(), tok );                
            }
            throw new ParseException( errorMessage, tok );
        }
        return read();
    }

    
    public List<IToken> advanceTo(TokenType[] expectedTypes,boolean advancePastMatchedToken) 
    {    
        if ( expectedTypes == null ) {
            throw new IllegalArgumentException("expectedTokenTypes must not be NULL.");
        }
        
        boolean expectingEOL = false;
        for ( TokenType t : expectedTypes ) 
        {
            if ( TokenType.EOL == t ) {
                expectingEOL = true;
                break;
            }
        }
        
        final List<IToken> result = new ArrayList<IToken>();
        while( ! eof() ) 
        {
            if ( peek().isEOL() ) 
            {
                if ( expectingEOL ) {
                    if ( advancePastMatchedToken ) {
                        result.add( read() );
                    }        			
                }
                return result; // RETURN
            }
            for ( TokenType expectedType : expectedTypes ) 
            {
                if ( peek().hasType( expectedType ) ) 
                {
                    if ( advancePastMatchedToken ) {
                        result.add( read() );
                    }
                    return result; // RETURN !
                }
            }
            result.add( read() );
        }
        return result;
    }

    
    public int getCurrentLineNumber() {
        return currentLineNumber;
    }

    
    public int getCurrentLineStartOffset() {
        return currentLineStartOffset;
    }

    
    public String toString()
    {
        return eof() ? "Lexer is at EOF" : peek().toString();
    }

    
    public boolean hasLexerOption(LexerOption option) {
        if (option == null) {
            throw new IllegalArgumentException("option must not be NULL");
        }
        return this.options.contains( option );
    }

    
    public void setLexerOption(LexerOption option, boolean enabled) 
    {
        if ( option == null ) {
            throw new IllegalArgumentException("option must not be NULL");
        }
        
        if ( enabled ) {
            options.add( option );
        } else {
            
            options.remove( option );
        }
        
        if ( option == LexerOption.CASE_INSENSITIVE_OPCODES ) {
            caseSensitiveOpCodes = ! enabled;
        }        
    }    
}
