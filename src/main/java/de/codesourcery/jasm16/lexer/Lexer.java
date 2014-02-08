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

import java.util.*;

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
    
    private final Set<LexerOption> options = new HashSet<LexerOption>(); 
    private boolean caseSensitiveOpCodes = true;
    
    // internal state
    private final List<IToken> currentTokens=new ArrayList<IToken>();
    private final Stack<State> marks = new Stack<State>();
    
    private final ParseOffset parseOffset;
    
    public static final class ParseOffset 
    {
    	// offset relative to actual scanner offset, used
    	// when expanding macro invocations
    	private int baseOffset;
        private int currentLineNumber;
        private int currentLineStartOffset;
        
		public ParseOffset()
		{
			this(0,1,0);
		}
		
		public ParseOffset(int baseOffset, int currentLineNumber,int currentLineStartOffset) 
		{
			this.baseOffset = baseOffset;
			this.currentLineNumber = currentLineNumber;
			this.currentLineStartOffset = currentLineStartOffset;
		}    	

		public ParseOffset(ParseOffset offset) 
		{
			this.baseOffset = offset.baseOffset;
			this.currentLineNumber = offset.currentLineNumber;
			this.currentLineStartOffset = offset.currentLineStartOffset;
		}
		
		@Override
		public String toString() {
			return "ParseOffset[ base_offset="+baseOffset+", line_nr="+currentLineNumber+",lineStartingOffset="+currentLineStartOffset+"]";
		}
		
		public int baseOffset() { return baseOffset; }
        public int currentLineNumber() { return currentLineNumber;}
        public int currentLineStartOffset() { return currentLineStartOffset; }		
		
		public void apply(ParseOffset offset) {
			this.baseOffset = offset.baseOffset;
			this.currentLineNumber = offset.currentLineNumber;
			this.currentLineStartOffset = offset.currentLineStartOffset;			
		}
		
		public void newLine(int newLineStartOffset) {
            this.currentLineNumber++;
            this.currentLineStartOffset = newLineStartOffset;			
		}
    }

    protected final class State 
    {
        private final List<IToken> markedTokens = new ArrayList<IToken>();
        private final int scannerOffset;
        private final ParseOffset offset;
        private final Set<LexerOption> options;

        protected State() 
        {
            this.markedTokens.addAll( Lexer.this.currentTokens );
            this.scannerOffset = Lexer.this.scanner.currentParseIndex();
            this.offset = new ParseOffset( Lexer.this.parseOffset );
            this.options = new HashSet<>( Lexer.this.options );
        }
        
        public void apply() 
        {
            Lexer.this.scanner.setCurrentParseIndex( this.scannerOffset );
            
            Lexer.this.currentTokens.clear();
            Lexer.this.currentTokens.addAll( this.markedTokens );
            
            Lexer.this.parseOffset.apply( this.offset );
            
            Lexer.this.options.clear();
            Lexer.this.options.addAll( this.options );
        }
    }

    public Lexer(IScanner scanner) {
        this(scanner,new ParseOffset());
    }	
    
    public Lexer(IScanner scanner,ParseOffset offset) {
        this.scanner = scanner;
        this.parseOffset = offset;
    }	    
    
    @Override
    public void mark()
    {
        marks.push( new State() );
    }

    @Override
    public void clearMark() {
        if ( marks.isEmpty() ) {
            throw new IllegalStateException("Must call mark() first");
        }
        marks.pop();
    }	

    @Override
    public void reset() throws IllegalStateException
    {
        if ( marks.isEmpty() ) {
            throw new IllegalStateException("Must call mark() first");
        }
        // TODO: Maybe should be pop() here ???
        marks.peek().apply(); 
    }

    private void parseNextToken() 
    {
        if ( scanner.eof() ) {
            return;
        }

        // clear buffer
        buffer.setLength(0);

        // skip whitespace
        int startIndex = relativeParseIndex();		
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

        startIndex = relativeParseIndex();
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
                    startIndex = relativeParseIndex();
                    scanner.read();
                    currentTokens.add( new Token(TokenType.SINGLE_LINE_COMMENT, ";" , relativeParseIndex()-1 ) );
                    return; 
                case '\\':
                    handleString( buffer.toString() , startIndex );
                    startIndex = relativeParseIndex();
                    scanner.read();
                    currentTokens.add( new Token(TokenType.STRING_ESCAPE, "\\", relativeParseIndex()-1 ) );
                    return;                     
                case '\'':
                case '"': // string delimiter
                    handleString( buffer.toString() , startIndex );
                    startIndex = relativeParseIndex();
                    scanner.read();
                    currentTokens.add( new Token(TokenType.STRING_DELIMITER, Character.toString( currentChar ) , relativeParseIndex()-1 ) );
                    return;			    

                case '\n':          // parse unix-style newline
                    handleString( buffer.toString() , startIndex );
                    startIndex = relativeParseIndex();
                    scanner.read();
                    currentTokens.add( new Token(TokenType.EOL, "\n" , relativeParseIndex()-1 ) );
                    return;
                case '\r': // parse DOS-style newline
                    buffer.append( scanner.read() );				
                    if ( ! scanner.eof() && scanner.peek() == '\n' ) 
                    {
                        handleString( buffer.toString() , buffer.length()-1 , startIndex );
                        scanner.read();					
                        currentTokens.add(  new Token(TokenType.EOL, "\r\n" , relativeParseIndex()-2 ) );
                        return;
                    }
                    continue;
                case ':': 
                    handleString( buffer.toString() , startIndex );
                    scanner.read();     
                    currentTokens.add(  new Token(TokenType.COLON , ":" , relativeParseIndex()-1 ) );
                    return;
                case '(': 
                    handleString( buffer.toString() , startIndex );
                    scanner.read();     
                    currentTokens.add(  new Token(TokenType.PARENS_OPEN , "(" , relativeParseIndex()-1) );
                    return;
                case ')':
                    handleString( buffer.toString() , startIndex );             
                    scanner.read();     
                    currentTokens.add( new Token(TokenType.PARENS_CLOSE, ")" , relativeParseIndex()-1 ) );
                    return;
                case '[': 
                    handleString( buffer.toString() , startIndex );
                    scanner.read();     
                    currentTokens.add(  new Token(TokenType.ANGLE_BRACKET_OPEN , "[" , relativeParseIndex()-1) );
                    return;
                case ']':
                    handleString( buffer.toString() , startIndex );             
                    scanner.read();     
                    currentTokens.add( new Token(TokenType.ANGLE_BRACKET_CLOSE, "]" , relativeParseIndex()-1 ) );
                    return;
                case ',':
                    handleString( buffer.toString() , startIndex ); 
                    scanner.read();     
                    currentTokens.add(  new Token(TokenType.COMMA , "," , relativeParseIndex()-1 ) );
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
    
    /**
     * Returns the scanner's current parse offset plus the parsing base offset. 
     * @return
     */
    private int relativeParseIndex() {
    	return this.parseOffset.baseOffset+scanner.currentParseIndex();
    }
    
    private void parseOperator(int lastStartIndex) 
    {
        handleString( buffer.toString() , lastStartIndex );
        buffer.setLength( 0 );

        // consume first character
        final int startIndex = relativeParseIndex();
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
    	/* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    	 * MAKE SURE TO ADJUST isKeyword(String) when changing keywords here 
    	 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    	 */
    	
        /* 
         * Note that all comparisons here are ordered by
         * their probabilities (more likely checks come first).
         */
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
        
        if ( ".word".equalsIgnoreCase( buffer ) || "dat".equalsIgnoreCase( buffer ) || ".dat".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.INITIALIZED_MEMORY_WORD , buffer , startIndex ) );
            return ;
        }   
        
        if ( ".equ".equalsIgnoreCase( buffer ) || "#define".equalsIgnoreCase(buffer) ) {
            currentTokens.add( new Token(TokenType.EQUATION , buffer , startIndex ) );
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
        
        if ( "reserve".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.UNINITIALIZED_MEMORY_WORDS , buffer , startIndex ) );
            return ;
        }           

        if ( ".bss".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.UNINITIALIZED_MEMORY_BYTES , buffer , startIndex ) );
            return ;
        }		
        
        if ( "#include".equals( buffer ) || ".include".equals( buffer ) || "include".equalsIgnoreCase( buffer) || ".incsource".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.INCLUDE_SOURCE, buffer , startIndex ) );
            return ;        	
        }
        
        if ( ".incbin".equalsIgnoreCase( buffer ) || "incbin".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.INCLUDE_BINARY , buffer , startIndex ) );
            return ;
        }
        
        if ( "org".equalsIgnoreCase( buffer )  || ".org".equalsIgnoreCase( buffer )  || ".origin".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.ORIGIN , buffer , startIndex ) );
            return ;
        }    
        
        if ( ".macro".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.START_MACRO , buffer , startIndex ) );
            return ;        	
        }
        
        if ( ".endmacro".equalsIgnoreCase( buffer ) ) {
            currentTokens.add( new Token(TokenType.END_MACRO , buffer , startIndex ) );
            return ;        	
        }        
        
        if ( buffer.contains("." ) ) {
            
            int idx = startIndex;
            int lastIndex = startIndex;
            
            final StringBuilder tmp = new StringBuilder();
            final int len = buffer.length();
            for ( int i = 0 ; i <len ; i++ , idx++) 
            {
                final char c = buffer.charAt( i );
                if ( c == '.' ) {
                    if ( tmp.length() > 0 ) {
                        currentTokens.add( new Token(TokenType.CHARACTERS, tmp.toString() , lastIndex ) );
                        tmp.setLength(0);
                    }
                    currentTokens.add( new Token(TokenType.DOT, "." , idx ) );
                    lastIndex = idx+1;                    
                    continue;
                }
                tmp.append( c );
            }
            if ( tmp.length() > 0 ) {
                currentTokens.add( new Token(TokenType.CHARACTERS, tmp.toString() , lastIndex ) );                
            }
            return;
        }
        currentTokens.add(  new Token( TokenType.CHARACTERS , buffer , startIndex ) );
    }
    
    /**
     * Returns whether a given string matches a keyword (case-insensitive).
     * 
     * @param s
     * @return
     */
    public boolean isKeyword(String buffer) 
    {
    	if ( StringUtils.isBlank(buffer) ) {
    		return false;
    	}
    	
        if ( OpCode.fromIdentifier( buffer ) != null ) {
            return true;
        } 
        
        if ( "push".equalsIgnoreCase( buffer ) ) {
            return true;
        }

        if ( "pop".equalsIgnoreCase( buffer ) ) {
            return true;
        }	
        
        if ( ".word".equalsIgnoreCase( buffer ) || "dat".equalsIgnoreCase( buffer ) || ".dat".equalsIgnoreCase( buffer ) ) {
            return true;
        }   
        
        if ( ".equ".equalsIgnoreCase( buffer ) || "#define".equalsIgnoreCase(buffer) ) {
            return true;            
        }        
        
        if ( "pick".equalsIgnoreCase( buffer ) ) {
            return true;        	
        }

        if ( "peek".equalsIgnoreCase( buffer ) ) {
            return true;
        }  
        
        if ( ".byte".equalsIgnoreCase( buffer ) ) {
            return true;
        }
        
        if ( "pack".equalsIgnoreCase( buffer ) ) {
            return true;        	
        }
        
        if ( "reserve".equalsIgnoreCase( buffer ) ) {
            return true ;
        }           

        if ( ".bss".equalsIgnoreCase( buffer ) ) {
            return true;
        }		
        
        if ( "#include".equals( buffer ) || ".include".equals( buffer ) || "include".equalsIgnoreCase( buffer) || ".incsource".equalsIgnoreCase( buffer ) ) {
            return true;        	
        }
        
        if ( ".incbin".equalsIgnoreCase( buffer ) || "incbin".equalsIgnoreCase( buffer ) ) {
            return true;
        }
        
        if ( "org".equalsIgnoreCase( buffer )  || ".org".equalsIgnoreCase( buffer )  || ".origin".equalsIgnoreCase( buffer ) ) {
            return true;
        }    
        
        if ( ".macro".equalsIgnoreCase( buffer ) ) {
            return true;        	
        }
        
        if ( ".endmacro".equalsIgnoreCase( buffer ) ) {
            return true;        	
        }      	
    	return false;
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

    @Override
    public boolean eof() 
    {
        return currentToken() == null;
    }

    @Override
    public IToken peek() throws EOFException
    {
        if ( eof() ) {
            throw new EOFException("Premature end of file",currentParseIndex() );
        }
        return currentToken();
    }
    
    @Override
    public boolean peek(TokenType t) throws EOFException
    {
        if ( eof() ) {
            throw new EOFException("Premature end of file",currentParseIndex() );
        }
        return currentToken().hasType(t);
    }    

    @Override
    public IToken read() throws EOFException
    {
        if ( eof() ) {
            throw new EOFException("Premature end of file",currentParseIndex() );			
        }		
        final IToken result = currentToken();
        currentTokens.remove( 0 );

        if ( result.isEOL() ) {
        	this.parseOffset.newLine( result.getStartingOffset()+1);
        }
        return result;
    }

    @Override
    public int currentParseIndex()
    {
        final IToken tok = currentToken();
        return tok != null ? tok.getStartingOffset() : relativeParseIndex();
    }
    
    @Override
    public IToken read(TokenType expectedType) throws ParseException,EOFException
    {    
        return read((String) null,expectedType);
    }

    @Override
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

    @Override
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

    @Override
    public int getCurrentLineNumber() {
        return parseOffset.currentLineNumber();
    }

    @Override
    public int getCurrentLineStartOffset() {
        return parseOffset.currentLineStartOffset();
    }

    @Override
    public String toString()
    {
        return eof() ? "Lexer is at EOF" : peek().toString();
    }

    @Override
    public boolean hasLexerOption(LexerOption option) {
        if (option == null) {
            throw new IllegalArgumentException("option must not be NULL");
        }
        return this.options.contains( option );
    }

    @Override
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

	@Override
	public List<IToken> skipWhitespace(boolean skipEOL) 
	{
		List<IToken> result = new ArrayList<>();
		while ( ! eof() && ( peek().isWhitespace() || (skipEOL && peek().isEOL() ) ) ) 
		{
			result.add( read() );
		}
		return result;
	}    
}