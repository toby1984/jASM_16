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

import de.codesourcery.jasm16.OpCode;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * A valid , IMMUTABLE identifier in the assembler source code.
 * 
 * <p>Currently this assembler only supports labels.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see LabelNode
 */
public class Identifier
{
    private static final char[] VALID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_$".toCharArray();
    
    private final String identifier; // client code relies on identifiers being immutable !!!
    
    /**
     * Create identifier.
     * 
     * @param identifier
     * @throws ParseException If the input string is not a valid identifier.
     */
    public Identifier(String identifier) throws ParseException 
    {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be NULL.");
        }
        if ( identifier.length() == 0 ) {
            throw new ParseException("Identifier expected", 0 ,0 );
        }
        final char[] actual = identifier.toCharArray();
        int i = 0;
outer:        
        for ( ; i < actual.length ; i++ ) 
        {
            final char currentChar = actual[i];
            for ( char validChar : VALID_CHARACTERS ) {
                if ( currentChar == validChar ) {
                    continue outer;
                }
            }
            throw new ParseException("Found invalid character '"+currentChar+"' in identifier '"+identifier+"'", i , 1);
        }
        this.identifier = identifier;
    }
    
    public static boolean isValidIdentifier(String identifier) 
    {
        if (identifier == null) {
            return false;
        }
        if ( identifier.length() == 0 ) {
            return false;
        }
        final char[] actual = identifier.toCharArray();
        int i = 0;
outer:        
        for ( ; i < actual.length ; i++ ) 
        {
            final char currentChar = actual[i];
            for ( char validChar : VALID_CHARACTERS ) {
                if ( currentChar == validChar ) {
                    continue outer;
                }
            }
            if ( i == 0 && currentChar == '.' ) { // local labels may start with '.'
            	continue;
            }            
            return false;
        }
        return true;
    }
    
    @Override
    public boolean equals(Object obj) 
    {
    	if ( this == obj ) {
    		return true;
    	}
    	if ( obj instanceof Identifier) {
    		return this.identifier.equals( ((Identifier) obj).identifier );
    	}
    	return false;
    }
    
    @Override
    public int hashCode() {
    	return identifier.hashCode();
    }
    
    /**
     * Returns the 'raw' value (as returned by the lexer) of
     * this identifier.
     * 
     * @return
     */
    public String getRawValue() {
    	return identifier;
    }
    
    @Override
    public String toString()
    {
        return identifier;
    }
    
    /**
     * Check whether a character may be used in an identifier.
     * 
     * <p>This method is used by the lexer.</p>
     * 
     * @param c
     * @return
     */
    public static boolean isValidIdentifierChar(char c) 
    {
        for ( char valid : VALID_CHARACTERS ) {
            if ( valid == c) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Performs additional checks (not an opcode etc.) on an identifier , throwing a {@link ParseException}
     * if any of these checks fails.
     *  
     * @param s
     * @param textRegion text region to report when throwing a <code>ParseException</code>
     * @throws ParseException
     */
    public static void assertValidIdentifier(String s,ITextRegion textRegion) throws ParseException 
    {
        final OpCode op = OpCode.fromIdentifier( s );
        if ( op != null ) {
            throw new ParseException("Reserved keywords (opcode) must not be used as identifiers", textRegion );
        }
        
        final Register register = Register.fromString( s );
        if ( register != null ) {
            throw new ParseException("Reserved keywords (register name) must not be used as identifiers", textRegion );
        } 
    }
}
