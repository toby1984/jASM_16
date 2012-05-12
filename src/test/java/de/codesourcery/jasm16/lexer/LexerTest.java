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
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.OpCode;
import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.scanner.Scanner;
import de.codesourcery.jasm16.utils.TextRegion;

public class LexerTest extends TestHelper {

    public void testParseSetOrigin() 
    {
        final String source = ".org 0x2000";

        final Lexer lexer = new Lexer( new Scanner( source ) );
        
        assertToken( lexer , TokenType.ORIGIN, ".org" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "0x2000" );
        assertTrue( lexer.eof() );
    }
    
    public void testParseSetOrigin2() 
    {
        final String source = ".origin 1024";

        final Lexer lexer = new Lexer( new Scanner( source ) );
        
        assertToken( lexer , TokenType.ORIGIN, ".origin" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1024" );
        assertTrue( lexer.eof() );
    }    
    
    public void testParseIncludeBinary() 
    {
        final String source = ".incbin \"somefile.txt\"";

        final Lexer lexer = new Lexer( new Scanner( source ) );
        
        assertToken( lexer , TokenType.INCLUDE_BINARY, ".incbin" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.STRING_DELIMITER, "\"" );
        assertToken( lexer , TokenType.CHARACTERS, "somefile.txt" );
        assertToken( lexer , TokenType.STRING_DELIMITER, "\"" );
        assertTrue( lexer.eof() );
    }
    
    public void testLexing() 
    {
        final String source = "        :loop         SET [0x2000+I], [A]      ; 2161 2000\n";

        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.WHITESPACE, "        " );
        assertToken( lexer , TokenType.COLON, ":" );		
        assertToken( lexer , TokenType.CHARACTERS, "loop" );
        assertToken( lexer , TokenType.WHITESPACE, "         " );
        assertToken( lexer , TokenType.INSTRUCTION, "SET" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.ANGLE_BRACKET_OPEN, "[" );	

        assertToken( lexer , TokenType.NUMBER_LITERAL, "0x2000" );	
        assertToken( lexer , TokenType.OPERATOR, "+" );	
        assertToken( lexer , TokenType.CHARACTERS, "I" );
        assertToken( lexer , TokenType.ANGLE_BRACKET_CLOSE, "]" );	
        assertToken( lexer , TokenType.COMMA , "," );			

        assertToken( lexer , TokenType.WHITESPACE, " " );

        assertToken( lexer , TokenType.ANGLE_BRACKET_OPEN, "[" );	
        assertToken( lexer , TokenType.CHARACTERS, "A" );
        assertToken( lexer , TokenType.ANGLE_BRACKET_CLOSE, "]" );	

        assertToken( lexer , TokenType.WHITESPACE, "      " );
        assertToken( lexer , TokenType.SINGLE_LINE_COMMENT, ";");
        assertToken( lexer , TokenType.WHITESPACE, " ");
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2161");
        assertToken( lexer , TokenType.WHITESPACE, " ");    
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2000");        
        assertToken( lexer , TokenType.EOL, "\n" );
        assertTrue( lexer.eof() );
    }

    public void testWhitespacesArePreserved() 
    {
        final String source = "SET [A+10] , 10 ";

        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.INSTRUCTION, "SET" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.ANGLE_BRACKET_OPEN, "[" );
        assertToken( lexer , TokenType.CHARACTERS, "A" );
        assertToken( lexer , TokenType.OPERATOR, "+" );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "10" );
        assertToken( lexer , TokenType.ANGLE_BRACKET_CLOSE, "]" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.COMMA, "," );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "10" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertTrue( lexer.eof() );
    }

    public void testParseOperators2() 
    {
        String source ="1+2*";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1" );
        assertToken( lexer , TokenType.OPERATOR, "+" );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2" );
        IToken tok = lexer.read();
        assertEquals( TokenType.OPERATOR , tok.getType() );
        assertEquals("*" , tok.getContents());
        assertTrue( new TextRegion( 3,1 ).isSame( tok ) );
        assertTrue( lexer.eof() );
    }
    
    public void testConditional1() 
    {
        String source ="1<=2";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1" );
        assertToken( lexer , TokenType.OPERATOR, "<=" );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2" );
        assertTrue( lexer.eof() );
    }    
    
    public void testConditional2() 
    {
        String source ="1>=2";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1" );
        assertToken( lexer , TokenType.OPERATOR, ">=" );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2" );
        assertTrue( lexer.eof() );
    }     
    
    public void testConditional3() 
    {
        String source ="1<2";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1" );
        assertToken( lexer , TokenType.OPERATOR, "<" );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2" );
        assertTrue( lexer.eof() );
    } 
    
    public void testConditional4() 
    {
        String source ="1>2";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1" );
        assertToken( lexer , TokenType.OPERATOR, ">" );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2" );
        assertTrue( lexer.eof() );
    }      
    
    public void testConditional5() 
    {
        String source ="1==2";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1" );
        assertToken( lexer , TokenType.OPERATOR, "==" );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2" );
        assertTrue( lexer.eof() );
    }      
    
    public void testConditional6() 
    {
        String source ="1!=2";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1" );
        assertToken( lexer , TokenType.OPERATOR, "!=" );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2" );
        assertTrue( lexer.eof() );
    }        
    public void testParseExpressionWithNegativeNumber() throws ParseException 
    {
        String source ="1+-2";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.NUMBER_LITERAL, "1" );
        assertToken( lexer , TokenType.OPERATOR, "+" );
        assertToken( lexer , TokenType.OPERATOR, "-" );        
        assertToken( lexer , TokenType.NUMBER_LITERAL, "2" );
        assertTrue( lexer.eof() );
    }    
    
    public void testParseShiftOperators () 
    {
        String source =" << >> ";
        final Lexer lexer = new Lexer( new Scanner( source ) );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.OPERATOR, "<<" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertToken( lexer , TokenType.OPERATOR, ">>" );
        assertToken( lexer , TokenType.WHITESPACE, " " );
        assertTrue( lexer.eof() );
    }    

    public void testGetCurrentLineNumber() {

        final String input="line1\nline2";

        final Lexer lexer = new Lexer( new Scanner( input ) );

        assertEquals( 1 , lexer.getCurrentLineNumber() );
        assertToken( lexer , TokenType.CHARACTERS , "line1" );
        assertEquals( 1 , lexer.getCurrentLineNumber() );

        assertToken( lexer , TokenType.EOL, "\n" );
        assertEquals( 2 , lexer.getCurrentLineNumber() );	

        assertToken( lexer , TokenType.CHARACTERS , "line2" );
        assertEquals( 2 , lexer.getCurrentLineNumber() );	
        assertTrue( lexer.eof() );
    }	

    public void testGetCurrentLineNumberWithMark() {

        final String input="line1\nline2\nline3";

        final Lexer lexer = new Lexer( new Scanner( input ) );

        assertEquals( 1 , lexer.getCurrentLineNumber() );
        assertToken( lexer , TokenType.CHARACTERS , "line1" );
        assertToken( lexer , TokenType.EOL, "\n" );		

        assertEquals( 2 , lexer.getCurrentLineNumber() );
        lexer.mark();

        assertToken( lexer , TokenType.CHARACTERS , "line2" );
        assertEquals( 2 , lexer.getCurrentLineNumber() );			
        assertToken( lexer , TokenType.EOL, "\n" );

        assertToken( lexer , TokenType.CHARACTERS , "line3" );
        assertEquals( 3 , lexer.getCurrentLineNumber() );	
        assertTrue( lexer.eof() );

        lexer.reset();

        assertEquals( 2 , lexer.getCurrentLineNumber() );
        assertToken( lexer , TokenType.CHARACTERS , "line2" );
    }	

    public void testParseUninitializedMemory() {

        final String input = ".bss 100";
        final Lexer lexer = new Lexer( new Scanner( input ) );
        assertFalse( lexer.eof() );
        assertToken( lexer , TokenType.UNINITIALIZED_MEMORY , ".bss");
        assertToken( lexer , TokenType.WHITESPACE, " ");        
        assertToken( lexer , TokenType.NUMBER_LITERAL, "100");
        assertTrue( lexer.eof() );
    }	

    public void testParseComment() {

        final String input = "        ; stuff\n";
        final Lexer lexer = new Lexer( new Scanner( input ) );
        assertFalse( lexer.eof() );
        assertToken( lexer , TokenType.WHITESPACE , "        " );
        assertToken( lexer , TokenType.SINGLE_LINE_COMMENT , ";");
        assertToken( lexer , TokenType.WHITESPACE, " ");		
        assertToken( lexer , TokenType.CHARACTERS, "stuff");
        assertToken( lexer , TokenType.EOL , "\n" );
        assertTrue( lexer.eof() );
    }

    public void testParseCharacterLiteralWithDoubleQuote() {

        final Lexer lexer = new Lexer( new Scanner("\"blubb\"" ) );

        assertFalse( lexer.eof() );
        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.STRING_DELIMITER, tok.getType() );
        assertEquals("\"" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS, tok.getType() );
        assertEquals("blubb" , tok.getContents() );
        assertEquals( 1 , tok.getStartingOffset() );		

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.STRING_DELIMITER, tok.getType() );
        assertEquals("\"" , tok.getContents() );
        assertEquals( 6 , tok.getStartingOffset() );		
        assertTrue( lexer.eof() );
    }	

    public void testParseParens() {

        final Lexer lexer = new Lexer( new Scanner("()" ) );

        assertFalse( lexer.eof() );
        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.PARENS_OPEN , tok.getType() );
        assertEquals("(" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.PARENS_CLOSE , tok.getType() );
        assertEquals(")" , tok.getContents() );
        assertEquals( 1 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );        
    }	

    public void testParseOneString() {

        final Lexer lexer = new Lexer( new Scanner("blubb" ) );

        assertFalse( lexer.eof() );
        IToken tok = lexer.peek();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blubb" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blubb" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );
    }

    public void testResetWithoutMarkFails() {

        final Lexer lexer = new Lexer( new Scanner("blubb blah" ) );

        try {
            lexer.reset();
            fail("Should've failed");
        } catch(IllegalStateException e) {
            // ok
        }
    }

    public void testMarkReset() throws EOFException, ParseException {

        final Lexer lexer = new Lexer( new Scanner("blubb blah" ) );

        assertFalse( lexer.eof() );

        IToken tok =  lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blubb" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );

        lexer.read( TokenType.WHITESPACE );

        lexer.mark();

        tok =  lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blah" , tok.getContents() );
        assertEquals( 6 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );        

        lexer.reset();

        tok =  lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blah" , tok.getContents() );
        assertEquals( 6 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );   

        lexer.reset();

        tok =  lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blah" , tok.getContents() );
        assertEquals( 6 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );          
    }	

    public void testParseOneComment() {

        final Lexer lexer = new Lexer( new Scanner(";simplecomment" ) );

        assertFalse( lexer.eof() );
        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.SINGLE_LINE_COMMENT , tok.getType() );
        assertEquals(";" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("simplecomment" , tok.getContents() );
        assertEquals( 1 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );		
    }	

    public void testParseTwoCommentsUNIX() {

        final Lexer lexer = new Lexer( new Scanner(";comment1\n;comment2" ) );

        assertFalse( lexer.eof() );
        assertToken( lexer , TokenType.SINGLE_LINE_COMMENT , ";" );
        assertToken( lexer , TokenType.CHARACTERS, "comment1" );		
        assertFalse( lexer.eof() );

        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.EOL , tok.getType() );
        assertEquals("\n" , tok.getContents() );
        assertEquals( 9 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );

        assertToken( lexer , TokenType.SINGLE_LINE_COMMENT , ";" );
        assertToken( lexer , TokenType.CHARACTERS, "comment2" );        
        assertTrue( lexer.eof() );		
    }	

    public void testParseTwoCommentsDOS() {

        final Lexer lexer = new Lexer( new Scanner(";comment1\r\n;comment2" ) );

        assertFalse( lexer.eof() );
        assertToken( lexer , TokenType.SINGLE_LINE_COMMENT , ";" );
        assertToken( lexer , TokenType.CHARACTERS, "comment1" );        
        assertFalse( lexer.eof() );

        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.EOL , tok.getType() );
        assertEquals("\r\n" , tok.getContents() );
        assertEquals( 9 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );

        assertToken( lexer , TokenType.SINGLE_LINE_COMMENT , ";" );
        assertToken( lexer , TokenType.CHARACTERS, "comment2" );        
        assertTrue( lexer.eof() );     
    }	


    public void testParseOneStringIncludingWhitespace() {

        final Lexer lexer = new Lexer( new Scanner("   blubb  " ) );

        assertFalse( lexer.eof() );

        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.WHITESPACE , tok.getType() );
        assertEquals("   " , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );		

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blubb" , tok.getContents() );
        assertEquals( 3 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.WHITESPACE , tok.getType() );
        assertEquals("  " , tok.getContents() );
        assertEquals( 8 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );		

    }	

    public void testParseTwoStrings() throws EOFException, ParseException {

        final Lexer lexer = new Lexer( new Scanner("   blubb  blah " ) );

        assertFalse( lexer.eof() );

        lexer.read( TokenType.WHITESPACE );

        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blubb" , tok.getContents() );
        assertEquals( 3 , tok.getStartingOffset() );

        lexer.read( TokenType.WHITESPACE );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("blah" , tok.getContents() );
        assertEquals( 10 , tok.getStartingOffset() );

        lexer.read( TokenType.WHITESPACE );

        assertTrue( lexer.eof() );
    }		

    public void testParseDecNumber() {

        final Lexer lexer = new Lexer( new Scanner("12345" ) );

        assertFalse( lexer.eof() );
        IToken tok = lexer.peek();
        assertNotNull( tok );
        assertEquals( TokenType.NUMBER_LITERAL , tok.getType() );
        assertEquals("12345" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.NUMBER_LITERAL , tok.getType() );
        assertEquals("12345" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );
    }

    public void testParseByType() throws EOFException, ParseException {

        final Lexer lexer = new Lexer( new Scanner("a b" ) );

        lexer.read();
        lexer.read();
        try {
            lexer.read( TokenType.WHITESPACE );
            fail("Should've failed");
        } catch(ParseException e) {
            assertEquals( 2 ,e.getErrorOffset() );
        }
        IToken tok = lexer.read( TokenType.CHARACTERS );
        assertNotNull( tok );
        assertEquals( TokenType.CHARACTERS , tok.getType() );
        assertEquals("b" , tok.getContents() );
        assertEquals( 2 , tok.getStartingOffset() );

        assertTrue( lexer.eof() );
    }

    public void testParseBinNumber() {

        final Lexer lexer = new Lexer( new Scanner("b101010" ) );

        assertFalse( lexer.eof() );
        IToken tok = lexer.peek();
        assertNotNull( tok );
        assertEquals( TokenType.NUMBER_LITERAL , tok.getType() );
        assertEquals("b101010" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.NUMBER_LITERAL , tok.getType() );
        assertEquals("b101010" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );
    }	

    public void testParseOperators() {

        final Lexer lexer = new Lexer( new Scanner("+-++*/--" ) );

        assertFalse( lexer.eof() );
        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.OPERATOR , tok.getType() );
        assertEquals("+" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.OPERATOR , tok.getType() );
        assertEquals("-" , tok.getContents() );
        assertEquals( 1 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );   

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.OPERATOR , tok.getType() );
        assertEquals("++" , tok.getContents() );
        assertEquals( 2 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );         

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.OPERATOR , tok.getType() );
        assertEquals("*" , tok.getContents() );
        assertEquals( 4 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );        

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.OPERATOR , tok.getType() );
        assertEquals("/" , tok.getContents() );
        assertEquals( 5 , tok.getStartingOffset() );
        assertFalse( lexer.eof() );  

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.OPERATOR , tok.getType() );
        assertEquals("--" , tok.getContents() );
        assertEquals( 6 , tok.getStartingOffset() );

        assertTrue( lexer.eof() );           
    }   	

    public void testParseHexNumber() {

        final Lexer lexer = new Lexer( new Scanner("0xdeadbeef" ) );

        assertFalse( lexer.eof() );
        IToken tok = lexer.peek();
        assertNotNull( tok );
        assertEquals( TokenType.NUMBER_LITERAL , tok.getType() );
        assertEquals("0xdeadbeef" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );

        tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.NUMBER_LITERAL , tok.getType() );
        assertEquals("0xdeadbeef" , tok.getContents() );
        assertEquals( 0 , tok.getStartingOffset() );
        assertTrue( lexer.eof() );
    }	

    public void testParseAngleBracketOpen() {
        final Lexer lexer = new Lexer( new Scanner("[" ) );
        assertToken(lexer,TokenType.ANGLE_BRACKET_OPEN,"[" , 0 );
        assertTrue( lexer.eof() );		
    }	

    public void testParseAngleBracketClose() {
        final Lexer lexer = new Lexer( new Scanner("]" ) );
        assertToken(lexer,TokenType.ANGLE_BRACKET_CLOSE,"]" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseComma() {
        final Lexer lexer = new Lexer( new Scanner("," ) );
        assertToken(lexer,TokenType.COMMA,"," , 0 );
        assertTrue( lexer.eof() );
    }
    
    public void testParseIncludeSource() {
        final Lexer lexer = new Lexer( new Scanner(".include" ) );
        assertToken(lexer,TokenType.INCLUDE_SOURCE,".include" , 0 );
        assertTrue( lexer.eof() );
    }   
    
    public void testParseOrigin1() {
        final Lexer lexer = new Lexer( new Scanner(".org" ) );
        assertToken(lexer,TokenType.ORIGIN,".org" , 0 );
        assertTrue( lexer.eof() );
    }   
    
    public void testParseOrigin2() {
        final Lexer lexer = new Lexer( new Scanner(".origin" ) );
        assertToken(lexer,TokenType.ORIGIN,".origin" , 0 );
        assertTrue( lexer.eof() );
    }         
    
    public void testParseDot() {
        final Lexer lexer = new Lexer( new Scanner("." ) );
        assertToken(lexer,TokenType.DOT,"." , 0 );
        assertTrue( lexer.eof() );
    }
    
    public void testParseDotInLocalLabel() {
        final Lexer lexer = new Lexer( new Scanner(".label" ) );
        assertToken(lexer,TokenType.DOT,"." , 0 );
        assertToken(lexer,TokenType.CHARACTERS,"label" , 1 );        
        assertTrue( lexer.eof() );
    }    

    public void testParseLabel() {
        final Lexer lexer = new Lexer( new Scanner(":label" ) );
        assertToken(lexer,TokenType.COLON,":" , 0 );
        assertToken(lexer,TokenType.CHARACTERS,"label" , 1 );
        assertTrue( lexer.eof() );
    }

    public void testParseEOL() {
        final Lexer lexer = new Lexer( new Scanner("\n\r\n" ) );
        assertToken(lexer,TokenType.EOL,"\n" , 0 );
        assertToken(lexer,TokenType.EOL,"\r\n" , 1 );		
        assertTrue( lexer.eof() );
    }	

    public void testParseIncrement() {
        final Lexer lexer = new Lexer( new Scanner("++" ) );
        assertToken(lexer,TokenType.OPERATOR,"++" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpSet() {
        final Lexer lexer = new Lexer( new Scanner("SET" ) );
        assertToken(lexer,OpCode.SET,"SET" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParsePeek() throws EOFException, ParseException {

        final String line="SET a,PEEK\n";

        final Lexer lexer = new Lexer( new Scanner( line) );

        assertToken(lexer,OpCode.SET,"SET" );

        lexer.read( TokenType.WHITESPACE );

        assertToken(lexer,TokenType.CHARACTERS,"a" );

        assertToken(lexer,TokenType.COMMA,"," );        
        assertToken(lexer,TokenType.PEEK,"PEEK" );
        assertToken(lexer,TokenType.EOL,"\n" );
        assertTrue( lexer.eof() );
    }   	

    public void testParsePop() throws EOFException, ParseException {

        final String line="SET a,POP\n";

        final Lexer lexer = new Lexer( new Scanner( line) );

        assertToken(lexer,OpCode.SET,"SET" );

        lexer.read( TokenType.WHITESPACE );

        assertToken(lexer,TokenType.CHARACTERS,"a" );

        assertToken(lexer,TokenType.COMMA,"," );        
        assertToken(lexer,TokenType.POP,"POP" );
        assertToken(lexer,TokenType.EOL,"\n" );
        assertTrue( lexer.eof() );
    }	

    public void testParsePush() throws EOFException, ParseException {

        final String line="SET PUSH,1\n";

        final Lexer lexer = new Lexer( new Scanner( line) );

        assertToken(lexer,OpCode.SET,"SET" );

        lexer.read( TokenType.WHITESPACE );

        assertToken(lexer,TokenType.PUSH,"PUSH" );

        assertToken(lexer,TokenType.COMMA,"," );   

        assertToken(lexer,TokenType.NUMBER_LITERAL,"1" );        

        assertToken(lexer,TokenType.EOL,"\n" );
        assertTrue( lexer.eof() );
    }	

    public void testParseLabelAndInstruction() throws EOFException, ParseException {

        final String line=":label SET a,10\n";

        final Lexer lexer = new Lexer( new Scanner( line) );

        assertToken(lexer,TokenType.COLON,":" );
        assertToken(lexer,TokenType.CHARACTERS,"label" );

        lexer.read( TokenType.WHITESPACE );

        assertToken(lexer,OpCode.SET,"SET" );

        lexer.read( TokenType.WHITESPACE );

        assertToken(lexer,TokenType.CHARACTERS,"a" );

        assertToken(lexer,TokenType.COMMA,"," );		
        assertToken(lexer,TokenType.NUMBER_LITERAL,"10" );
        assertToken(lexer,TokenType.EOL,"\n" );
        assertTrue( lexer.eof() );
    }

    public void testParseOpAnd() {
        final Lexer lexer = new Lexer( new Scanner("and" ) );
        assertToken(lexer,OpCode.AND,"and" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpOr() {
        final Lexer lexer = new Lexer( new Scanner("bor" ) );
        assertToken(lexer,OpCode.OR,"bor" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpXor() {
        final Lexer lexer = new Lexer( new Scanner("xor" ) );
        assertToken(lexer,OpCode.XOR,"xor" , 0 );
        assertTrue( lexer.eof() );
    }		

    public void testParseOpShl() {
        final Lexer lexer = new Lexer( new Scanner("shl" ) );
        assertToken(lexer,OpCode.SHL,"shl" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpShr() {
        final Lexer lexer = new Lexer( new Scanner("shr" ) );
        assertToken(lexer,OpCode.SHR,"shr" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpAdd() {
        final Lexer lexer = new Lexer( new Scanner("add" ) );
        assertToken(lexer,OpCode.ADD,"add" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpSub() {
        final Lexer lexer = new Lexer( new Scanner("sub" ) );
        assertToken(lexer,OpCode.SUB,"sub" , 0 );
        assertTrue( lexer.eof() );
    }		

    public void testParseOpMul() {
        final Lexer lexer = new Lexer( new Scanner("mul" ) );
        assertToken(lexer,OpCode.MUL,"mul" , 0 );
        assertTrue( lexer.eof() );
    }

    public void testParseOpDiv() {
        final Lexer lexer = new Lexer( new Scanner("div" ) );
        assertToken(lexer,OpCode.DIV,"div" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpMod() {
        final Lexer lexer = new Lexer( new Scanner("mod" ) );
        assertToken(lexer,OpCode.MOD,"mod" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpIfe() {
        final Lexer lexer = new Lexer( new Scanner("ife" ) );
        assertToken(lexer,OpCode.IFE,"ife" , 0 );
        assertTrue( lexer.eof() );
    }

    public void testParseOpIfn() {
        final Lexer lexer = new Lexer( new Scanner("ifn" ) );
        assertToken(lexer,OpCode.IFN,"ifn" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpIfg() {
        final Lexer lexer = new Lexer( new Scanner("ifg" ) );
        assertToken(lexer,OpCode.IFG,"ifg" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpIfb() {
        final Lexer lexer = new Lexer( new Scanner("ifb" ) );
        assertToken(lexer,OpCode.IFB,"ifb" , 0 );
        assertTrue( lexer.eof() );
    }	

    public void testParseOpJsr() {
        final Lexer lexer = new Lexer( new Scanner("jsr" ) );
        assertToken(lexer,OpCode.JSR,"jsr" , 0 );
        assertTrue( lexer.eof() );
    }

    public void testLexerDoesNotChoke() {

        String line = "        ; Try some basic stuff\n" + 
                "                      SET A, 0x30              ; 7c01 0030\n" + 
                "                      SET [0x1000], 0x20       ; 7de1 1000 0020\n" + 
                "                      SUB A, [0x1000]          ; 7803 1000\n" + 
                "                      IFN A, 0x10              ; c00d \n" + 
                "                         SET PC, crash         ; 7dc1 001a [*]\n" + 
                "                      \n" + 
                "        ; Do a loopy thing\n" + 
                "                      SET I, 10                ; a861\n" + 
                "                      SET A, 0x2000            ; 7c01 2000\n" + 
                "        :loop         SET [0x2000+I], [A]      ; 2161 2000\n" + 
                "                      SUB I, 1                 ; 8463\n" + 
                "                      IFN I, 0                 ; 806d\n" + 
                "                         SET PC, loop          ; 7dc1 000d [*]\n" + 
                "        \n" + 
                "        ; Call a subroutine\n" + 
                "                      SET X, 0x4               ; 9031\n" + 
                "                      JSR testsub              ; 7c10 0018 [*]\n" + 
                "                      SET PC, crash            ; 7dc1 001a [*]\n" + 
                "        \n" + 
                "        :testsub      SHL X, 4                 ; 9037\n" + 
                "                      SET PC, POP              ; 61c1\n" + 
                "                        \n" + 
                "        ; Hang forever. X should now be 0x40 if everything went right.\n" + 
                "        :crash        SET PC, crash            ; 7dc1 001a [*]\n" + 
                "        \n" + 
                "        ; [*]: Note that these can be one word shorter and one cycle faster by using the short form (0x00-0x1f) of literals,\n" + 
                "        ;      but my assembler doesn't support short form labels yet.  ";

        line = StringUtils.repeat( line  , 200 );

        final Lexer lexer = new Lexer(new Scanner( line ) );
        final StringBuilder builder = new StringBuilder();
        int lineCount=0;
        long time = -System.currentTimeMillis();
        final List<IToken> tokens = new ArrayList<IToken>();
        while ( ! lexer.eof() ) 
        {
            final IToken token = lexer.read();
            tokens.add( token );
            if ( token.hasType( TokenType.EOL ) ) {
                lineCount++;
            }
        }
        time+=System.currentTimeMillis();

        for ( IToken token : tokens ) {
            //			System.out.println( token+" ( offset "+token.getParseStartOffset()+" )" );
            builder.append( token.getContents() );			
        }

        final double linesPerSecond = lineCount / ( time / 1000.0 );
        final double tokensPerSecond = tokens.size() / ( time / 1000.0 );

        System.out.println("Lines   : "+(lineCount+1)+" ( "+linesPerSecond+" lines/s)");
        System.out.println("Tokens  : "+tokens.size()+" ( "+tokensPerSecond+" tokens/s)");

        assertEquals( "Lexer failed to consume input completely ?", line , builder.toString() );
    }

}
