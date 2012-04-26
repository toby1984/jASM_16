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
package de.codesourcery.jasm16.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.codesourcery.jasm16.exceptions.ParseException;

/**
 * Provides helper methods for dealing with number literals in
 * hexadecimal/binary/decimal notation
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class NumberLiteralHelper
{
    private static final Pattern HEX_NUMBER=Pattern.compile("^0x([0-9a-fA-F]+)$");
    private final static char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    
    private static final Pattern BIN_NUMBER=Pattern.compile("^b([0-1]+)$");
    private static final Pattern DEC_NUMBER=Pattern.compile("^([\\-]{0,1}[0-9]+)$");  
    
    /**
     * Check whether a string resembles a valid number literal.
     * 
     * @param s
     * @return
     */
    public static boolean isNumberLiteral(String s) 
    {
        return isHexLiteral( s ) || isBinaryLiteral( s ) || isDecimalLiteral( s );
    }   
    
    private static boolean isHexLiteral( String s ) {
        return HEX_NUMBER.matcher( s ).matches();
    }
    
    private static boolean isBinaryLiteral(String s ) {
           return BIN_NUMBER.matcher( s ).matches();
    }
    
    private static boolean isDecimalLiteral(String s ) {
        return DEC_NUMBER.matcher( s ).matches();
    }
    
    private static long parseHex(String input) throws ParseException {
        
outer:      
        for ( int index = 0 ; index < input.length(); index++ ) 
        {
            final char currentChar = input.charAt(index);
            for ( int i = 0 ; i < HEX_CHARS.length ; i++ ) {
                if ( HEX_CHARS[i] == currentChar ) {
                    continue outer;
                }
            }
            throw new ParseException("Not a valid hex literal '"+currentChar+"'",index,1);
        }
        
        if ( input.length() > 8*2 ) {
            throw new ParseException("Hex literal to long (max. 8 bytes)", 0, input.length() );           
        }
        
        long result = 0;
        for ( int index = 0 ; index < input.length() ; index++) {
            final char currentChar = Character.toLowerCase( input.charAt(index) );
            int i = 15;
            for ( ; i >= 0 ; i-- ) {
                if ( HEX_CHARS[i] == currentChar ) {
                    break;
                }
            }   
            result = result << 4;           
            result = result | i;
        }
        return result;
    }
    
    /**
     * Returns the actual value of this number literal as a <code>long</code>.
     * 
     * @return
     * @throws ParseException
     */
    public static long parseValue(String s) throws ParseException
    {
        if ( isHexLiteral( s ) ) 
        {
            final Matcher matcher = HEX_NUMBER.matcher( s );
            matcher.matches();
            final String rawContents  = matcher.group( 1 ).toLowerCase();
            return parseHex( rawContents );
        } 
        else if ( isBinaryLiteral( s ) ) 
        {
            final Matcher matcher = BIN_NUMBER.matcher( s );
            matcher.matches();
            final String rawContents  = matcher.group( 1 ).toLowerCase();           
            long result = 0;
            for ( char digit : rawContents.toCharArray() ) {
                result = result << 1;
                if ( digit == '1' ) {
                    result = result | 1;
                }
            }
            return result;
        } 
        else if ( isDecimalLiteral( s ) ) 
        {
            Matcher matcher = DEC_NUMBER.matcher( s );
            matcher.matches();
            final String rawContents  = matcher.group( 1 ).toLowerCase();
            int index = 0;
            for ( ; index < rawContents.length() ; index++ )
            {
                final char c = rawContents.charAt(index);
                if ( c != '-' && ! Character.isDigit( c ) ) {
                    throw new ParseException("Not a digit: '"+c+"'",index,1);
                }
            }
            try {
                return Long.parseLong(rawContents);
            } catch(NumberFormatException e) {
                throw new ParseException("Number literal is too large: "+rawContents,0,rawContents.length());                
            }
        }
        throw new RuntimeException("Internal error, unhandled number literal: '"+s+"' ??");
    }   
        
}
