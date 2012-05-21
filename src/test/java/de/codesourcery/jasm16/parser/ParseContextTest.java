package de.codesourcery.jasm16.parser;

import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;

public class ParseContextTest extends TestHelper
{
    public void testParseStringWithDoubleQuotes() throws EOFException, ParseException {
        final String source = "\"test\"";
        assertEquals( "test" , createParseContext( source ).parseString( null ) );
    }
    
    public void testParseStringWithSingleQuotes() throws EOFException, ParseException {
        final String source = "'test'";
        assertEquals( "test" , createParseContext( source ).parseString( null ) );
    }    
    
    public void testParseStringWithSingleQuotes2() throws EOFException, ParseException {
        final String source = "'te\"st'";
        assertEquals( "te\"st" , createParseContext( source ).parseString( null ) );
    }    
    
    public void testParseStringWithDoubleQuotes2() throws EOFException, ParseException {
        final String source = "\"te\\\"st\"";
        System.out.println("String: "+source);
        assertEquals( "te\"st" , createParseContext( source ).parseString( null ) );
    }     
}
