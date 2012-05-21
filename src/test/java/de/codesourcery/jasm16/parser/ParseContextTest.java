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
