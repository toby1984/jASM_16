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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.scanner.IScanner;
import de.codesourcery.jasm16.scanner.Scanner;

/**
 * Class with various utility methods (most are only used by unit-tests).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Misc {
	
	private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
	
    public static String toHexString(byte[] data) 
    {
        final StringBuilder builder = new StringBuilder();
        for ( int i = 0 ; i < data.length ; i++)
        {
            builder.append( "0x"+toHexString( data[i] ) );
            if ( (i+1) < data.length ) {
                builder.append(",");
            }
        }

        return builder.toString();
    }
    
    public static String toHexDumpWithAddresses(int startingAddress , byte[] data, int wordsPerLine) 
    {
        final StringBuilder builder = new StringBuilder();
        int current = 0;
        while( current < data.length )
        {
            final int wordAddress = (startingAddress+current) >> 1;
            builder.append( toHexString( wordAddress ) ).append(": ");
            
            for ( int i = 0 ; current < data.length && i < wordsPerLine  ; i++)
            {
                byte b1 = data[current++];
                builder.append( toHexString( b1 ) );
                if ( current >= data.length ) {
                    break;
                }
                
                b1 = data[current++];
                builder.append( toHexString( b1 ) );   
                if ( current >= data.length ) {
                    break;
                } 
                builder.append(" ");
            }
            builder.append("\n");
        }
        return builder.toString();
    }
    
    public static String toHexString(int val) 
    {
        return toHexString( (byte) ( (val >>8 ) & 0x00ff ) )+toHexString( (byte) ( val & 0x00ff ) ); 
    }       
    
	public static String toHexString(byte val) 
	{
		final int lo = ( val & 0x0f );
		final int hi = ( val >> 4) & 0x0f;
		return ""+HEX_CHARS[ hi ]+HEX_CHARS[ lo ];
	}	

	public static String readSource(InputStream in) throws IOException {

		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		int len = 0 ;
		final byte[] buffer = new byte[1024];
		while ( ( len = in.read( buffer) ) > 0 ) {
			out.write( buffer ,0 , len );
		}
		out.flush();
		return new String( out.toByteArray() );
	}

	public static String readSource(ICompilationUnit unit) throws IOException 
	{
		final InputStream inputStream = unit.getResource().createInputStream();
		try {
			return readSource( inputStream );
		} finally {
			try {
				inputStream.close();
			} catch(IOException e) {
				/* ignored */
			}
		}
	}	

	public static String toPrettyString(String errorMessage, int errorOffset , String input) 
	{
		return toPrettyString( errorMessage , errorOffset , new Scanner( input ) );
	}
	
	public static String toPrettyString(String errorMessage, SourceLocation location , IScanner input) 
	{
		return toPrettyString( errorMessage , location.getStartingOffset() , location , input );
	}
	
	public static String toPrettyString(String errorMessage, int errorOffset , IScanner input) 
	{
		return toPrettyString( errorMessage , errorOffset , null , input );
	}

	public static String toPrettyString(String errorMessage, int errorOffset , ITextRange location , IScanner input) 
	{
		int oldOffset = input.currentParseIndex();
		try {
			input.setCurrentParseIndex( errorOffset );
		} 
		catch(IllegalArgumentException e) {
			return "ERROR at offset "+errorOffset+": "+errorMessage;
		}

		try {
			StringBuilder context = new StringBuilder();
			while ( ! input.eof() && input.peek() != '\n' ) {
				context.append( input.read() );
			}
			final String loc;
			if ( location instanceof SourceLocation) 
			{
				final SourceLocation srcLoc = (SourceLocation) location;
				loc = "Line "+
					srcLoc.getLineNumber()+",column "+
					srcLoc.getColumnNumber()+" ("+srcLoc.getOffset()+"): ";
			} else {
				loc = "index "+errorOffset+": ";
			}
			
			final String line1 = loc+context.toString();
	        final String indent = StringUtils.repeat(" ", loc.length() );
			final String line2 = indent+"^ "+errorMessage;
			return line1+"\n"+line2;
		} 
		finally {
			try {
				input.setCurrentParseIndex( oldOffset );
			} catch(Exception e2) {
				/* swallow */
			}    		
		}
	}

	public static void printCompilationErrors(ICompilationUnit unit,String source,boolean printStackTraces) 
	{
		if ( unit.hasErrors() ) 
		{
			final List<ICompilationError> errors = new ArrayList<ICompilationError>(unit.getErrors() );
			
			Collections.sort( errors ,new Comparator<ICompilationError>() {

				@Override
				public int compare(ICompilationError o1, ICompilationError o2) 
				{
					if ( o1 instanceof CompilationError && o2 instanceof CompilationError ) 
					{
						CompilationError err1 = (CompilationError) o1;
						CompilationError err2 = (CompilationError) o2;
						return Integer.valueOf( err1.getLocation().getStartingOffset() ).compareTo( Integer.valueOf( err2.getLocation().getStartingOffset() ) );
					}
					return 0;
				}} );
			
			for ( Iterator<ICompilationError> it = errors.iterator(); it.hasNext(); )
			{
			    final  ICompilationError error=it.next(); 
			    final int errorOffset;
                ITextRange range;
                if ( error.getLocation() != null ) {
                    range = error.getLocation();
                    errorOffset= range.getStartingOffset();
                } 
                else 
                {
                    errorOffset=error.getErrorOffset();
                    if ( errorOffset != -1 ) {
                        range = new TextRange( errorOffset , 1 );
                    } else {
                        range = null;
                    }
                }                
                
			    int line = error.getLineNumber();
			    int column = error.getColumnNumber();
			    if ( column == -1 && ( error.getErrorOffset() != -1 && error.getLineStartOffset() != -1 ) )
			    {
			        column = error.getErrorOffset() - error.getLineStartOffset()+1;
			    }
			    
			    if ( (line == -1 || column == -1) & errorOffset != -1 ) {
			        try {
			            SourceLocation location = unit.getSourceLocation( range );
			            line = location.getLineNumber();
			            column = errorOffset - location.getLineStartOffset()+1;
			            if ( column < 1 ) {
			                column = -1;
			            }
			        } catch(Exception e) {
			            // can't help it
			        }
			    }
			    
			    final boolean hasLocation = line != -1 && column != -1;
			    final String locationString;
			    if ( hasLocation ) {
			        locationString="line "+line+", column "+column+": ";
			    } else if ( errorOffset != -1 ) {
	                locationString="offset "+errorOffset+": ";
			    } else {
	                locationString="< unknown location >: ";
			    }
			    
			    boolean hasSource=false;
			    String sourceLine = null;
			    if ( line != -1 || range != null ) 
			    {
			        Line thisLine=null;
			        Line nextLine=null;
			        
			        if ( line != -1 ) 
			        {
    			        try {
    			            thisLine = error.getCompilationUnit().getLineByNumber( line );
    			            IScanner scanner = new Scanner( source );
    			            scanner.setCurrentParseIndex( thisLine.getLineStartingOffset() );
    			            while ( ! scanner.eof() && scanner.peek() != '\n' ) {
    			                scanner.read();
    			            }
    			            nextLine = new Line( line+1 , scanner.currentParseIndex() );
    			            
    			        } catch(Exception e) {
    			            // can't help it
    			        }
                        if ( thisLine != null && nextLine != null ) {
                            sourceLine = new TextRange( thisLine.getLineStartingOffset() , nextLine.getLineStartingOffset() - thisLine.getLineStartingOffset() ).apply( source );
                        } else {
                            sourceLine = range.apply( source );
                            column=1;      
                        }
                        hasSource = true;
			        } else { // range != null
			            sourceLine = range.apply( source );
			            column=1;
			            hasSource = true;
			        }
			    }
			    
			    if ( hasSource ) {
			        sourceLine = sourceLine.replaceAll( Pattern.quote("\r\n") , "" ).replaceAll( Pattern.quote("\n") , "" ).replaceAll("\t" , " ");		
			        final String trimmedSourceLine = removeLeadingWhitespace( sourceLine );
			        if ( column != -1 && trimmedSourceLine.length() != sourceLine.length() ) {
			            column -= ( sourceLine.length() - trimmedSourceLine.length() );
			        }
			        sourceLine = trimmedSourceLine;			        
			    }
			    
			    String firstLine;
                String secondLine=null;			    
			    if ( hasLocation ) 
			    {
			        if ( hasSource ) {
			            firstLine=locationString+sourceLine+"\n";
			            secondLine=StringUtils.repeat(" " ,locationString.length())+StringUtils.repeat( " ", (column-1) )+"^ "+error.getMessage()+"\n";
			        } else {
                        firstLine=locationString+error.getMessage();			            
			        }
			    } else {
			        firstLine="Unknown error: "+error.getMessage();
			    }
			    System.err.print( firstLine );
			    if ( secondLine != null ) {
			        System.err.print( secondLine );
			    }
			    System.out.println();
			    
				if ( printStackTraces && error.getCause() != null ) {
					error.getCause().printStackTrace();
				}
				
			}
		}    	
	}
	
	public static String removeLeadingWhitespace(String input) 
	{
	    StringBuilder output = new StringBuilder(input);
	    while ( output.length() > 0 && Character.isWhitespace( output.charAt( 0 ) ) ) {
	        output.delete( 0 , 1 );
	    }
	    return output.toString();
	}
	
    public static String padRight(String input,int length) 
    {
        final int delta = length - input.length();
        final String result;
        if ( delta <= 0 ) {
            result = input;
        } else {
            result = input+StringUtils.repeat(" ",delta);
        }
        return result;
    }

    public static String padLeft(String input,int length) {
        final int delta = length - input.length();
        final String result;
        if ( delta <= 0 ) {
            result = input;
        } else {
            result = StringUtils.repeat(" ",delta)+input;
        }
        return result;
    }		
	
}