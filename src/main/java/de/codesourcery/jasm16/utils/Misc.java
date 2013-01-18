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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.compiler.CompilationMarker;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.IMarker;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.disassembler.DisassembledLine;
import de.codesourcery.jasm16.exceptions.NoDirectoryException;
import de.codesourcery.jasm16.scanner.IScanner;
import de.codesourcery.jasm16.scanner.Scanner;

/**
 * Class with various utility methods (most are only used by unit-tests).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Misc {

    private static final Logger LOG = Logger.getLogger(Misc.class);

    // code assumes these characters are lowercase !!! Do NOT change this
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

    public static String toHexDumpWithAddresses(Address startingAddressInBytes , byte[] data, int wordsPerLine) 
    {
        return toHexDumpWithAddresses( startingAddressInBytes , data , data.length , wordsPerLine );
    }

    public static String toHexDumpWithAddresses(Address startingAddressInBytes , byte[] data, int length , int wordsPerLine) 
    {
        return toHexDumpWithAddresses(startingAddressInBytes, data, length, wordsPerLine, false );
    }

    public static String toHexDumpWithoutAddresses(Address startingAddressInBytes , byte[] data, int length , int wordsPerLine) 
    {
        return toHexDump(startingAddressInBytes, data, length, wordsPerLine, false , false );
    }    

    public static String toHexDumpWithAddresses(Address startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII) 
    {
        return toHexDump(startingAddressInBytes, data, length, wordsPerLine, printASCII, true);
    }

    public static String toHexDumpWithAddresses(int startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII) 
    {
        return toHexDump(startingAddressInBytes, data, length, wordsPerLine, printASCII, true , false );
    }    

    public static String toHexDumpWithAddresses(Address startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII,boolean wrapAddress) 
    {
        return toHexDump(startingAddressInBytes, data, length, wordsPerLine, printASCII, true,wrapAddress);
    }

    public static String toHexDump(Address startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII,boolean printAddress) 
    {
        return toHexDump( startingAddressInBytes, data , length , wordsPerLine , printASCII, printAddress , false );
    }

    public static String toHexDump(Address startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII,boolean printAddress,boolean wrapAddress) 
    {
        return toHexDump( startingAddressInBytes.getByteAddressValue() ,data, length , wordsPerLine,printASCII, printAddress,wrapAddress); 
    }

    public static String toHexDump(int startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII,boolean printAddress,boolean wrapAddress) 
    {
        final List<String> lines = toHexDumpLines(startingAddressInBytes, data, length, wordsPerLine, printASCII, printAddress,wrapAddress);
        StringBuilder result = new StringBuilder();
        for (Iterator<String> iterator = lines.iterator(); iterator.hasNext();) {
            String line = iterator.next();
            result.append( line );
            if ( iterator.hasNext() ) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    public static List<String> toHexDumpLines(Address startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII,boolean printAddress) 
    {
        return toHexDumpLines(startingAddressInBytes, data, length, wordsPerLine, printASCII, printAddress, false );
    }

    public static List<String> toHexDumpLines(Address startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII,boolean printAddress,boolean wrapAddress) 
    {
        return toHexDumpLines(startingAddressInBytes.getByteAddressValue() , data, length ,  wordsPerLine, printASCII, printAddress, wrapAddress);
    }

    public static List<String> toHexDumpLines(int startingAddressInBytes , byte[] data, int length , int wordsPerLine,boolean printASCII,boolean printAddress,boolean wrapAddress) 
    {
        final List<String> result = new ArrayList<String>();

        final StringBuilder asciiBuilder = new StringBuilder(); 
        final StringBuilder hexBuilder = new StringBuilder();
        
        int current = 0;
        while( current < length )
        {
            if ( printAddress ) 
            {
                int wordAddress = (startingAddressInBytes+current) >> 1;
                if ( wrapAddress ) {
                    wordAddress = (int) ( wordAddress % (WordAddress.MAX_ADDRESS+1) );
                }
                hexBuilder.append( toHexString( wordAddress ) ).append(": ");
            }

            for ( int i = 0 ; current < length && i < wordsPerLine  ; i++)
            {
                byte b1 = data[current++];
                hexBuilder.append( toHexString( b1 ) );
                if ( printASCII ) {
                    asciiBuilder.append( toASCII(b1) );
                }
                if ( current >= length ) {
                    break;
                }

                b1 = data[current++];
                hexBuilder.append( toHexString( b1 ) );
                if ( printASCII ) {
                    asciiBuilder.append( toASCII(b1) );
                }                
                if ( current >= length ) {
                    break;
                } 
                hexBuilder.append(" ");
            }
            if ( printASCII ) {
                hexBuilder.append(" ").append( asciiBuilder.toString() );
                asciiBuilder.setLength( 0 );
            } 
            result.add( hexBuilder.toString() );
            hexBuilder.setLength( 0 );
            asciiBuilder.setLength( 0 );
        }

        if ( printASCII && asciiBuilder.length() > 0 ) {
            hexBuilder.append(" ").append( asciiBuilder.toString() );
        } 		
        if ( hexBuilder.length() > 0 ) {
            result.add( hexBuilder.toString() );
        }
        return result;
    }

    private static char toASCII(byte b) 
    {
        int val = b;
        if ( val < 0 ) {
            val+=256;
        }
        if ( val < 32 || val > 126 ) {
            return '.';
        }
        return (char) val;
    }

    public static String toHexString(Address address) 
    {
        return toHexString( address.getValue() ); 
    } 

    public static String toHexString(int val) 
    {    	
        if ( ( val & 0xff000000 ) != 0 ) {
            return toHexString( (byte) ( (val >>> 24 ) & 0x00ff ) )+
                    toHexString( (byte) ( (val >>> 16 ) & 0x00ff ) )+
                    toHexString( (byte) ( (val >>> 8 ) & 0x00ff ) )+
                    toHexString( (byte) ( val & 0x00ff ) );             

        }

        if ( val > 0xffff && val <= 0xffffff )
        {
            return toHexString( (byte) ( (val >>> 16 ) & 0x00ff ) )+toHexString( (byte) ( (val >>> 8 ) & 0x00ff ) )+toHexString( (byte) ( val & 0x00ff ) );             
        } 

        if ( val <= 0xffff ) {
            return toHexString( (byte) ( (val >>> 8 ) & 0x00ff ) )+toHexString( (byte) ( val & 0x00ff ) );
        }
        return "Value out-of-range: "+val;
    }    

    public static String toHexString(long val) 
    {
        return toHexString( (byte) ( (val >>> 24 ) & 0xff ) )+
                toHexString( (byte) ( (val >>> 16 ) & 0xff ) )+
                toHexString( (byte) ( (val >>> 8 ) & 0xff ) )+
                toHexString( (byte) ( val & 0xff ) );             
    }

    public static String toHexString(byte val) 
    {
        final int lo = ( val & 0x0f );
        final int hi = ( val >>> 4) & 0x0f;
        return ""+HEX_CHARS[ hi ]+HEX_CHARS[ lo ];
    }	

    public static byte[] readBytes(IResource resource) throws IOException {
        final InputStream in = resource.createInputStream();
        try {
            return readBytes( in );
        } finally {
            IOUtils.closeQuietly( in );
        }
    }

    public static byte[] readBytes(InputStream in) throws IOException {

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = 0 ;
        final byte[] buffer = new byte[1024];
        while ( ( len = in.read( buffer) ) > 0 ) {
            out.write( buffer ,0 , len );
        }
        out.flush();
        return out.toByteArray();
    }	

    public static String readSource(IResource resource) throws IOException 
    {
        return new String( readBytes( resource ) );
    }	

    public static String readSource(InputStream in) throws IOException {
        return new String( readBytes( in ) );
    }

    public static String readSource(ICompilationUnit unit) throws IOException 
    {
        return readSource( unit.getResource().createInputStream() );
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

    public static String toPrettyString(String errorMessage, int errorOffset , ITextRegion location , IScanner input) 
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

    public static void printCompilationErrors(ICompilationUnit unit,IResource resource,boolean printStackTraces) throws IOException 
    {
        final String source = readSource( resource );
        printCompilationErrors( unit , source , printStackTraces );
    }

    public static void printCompilationErrors(ICompilationUnit unit,String source,boolean printStackTraces) 
    {
        final List<IMarker> markers = unit.getMarkers( IMarker.TYPE_COMPILATION_ERROR , IMarker.TYPE_COMPILATION_WARNING , IMarker.TYPE_GENERIC_COMPILATION_ERROR );
        printCompilationMarkers(unit,source,printStackTraces, markers );
    }

    public static void printCompilationMarkers(ICompilationUnit unit,String source,boolean printStackTraces, final List<IMarker> errors ) 
    {
        if ( errors.isEmpty() ) 
        {
            return;
        }

        Collections.sort( errors ,new Comparator<IMarker>() {

            @Override
            public int compare(IMarker o1, IMarker o2) 
            {
                if ( o1 instanceof CompilationMarker && o2 instanceof CompilationMarker ) 
                {
                    CompilationMarker err1 = (CompilationMarker) o1;
                    CompilationMarker err2 = (CompilationMarker) o2;
                    final ITextRegion loc1 = err1.getLocation();
                    final ITextRegion loc2 = err2.getLocation();
                    if ( loc1 != null && loc2 != null ) {
                        return Integer.valueOf( err1.getLocation().getStartingOffset() ).compareTo( Integer.valueOf( err2.getLocation().getStartingOffset() ) );
                    }
                    if ( loc1 != null ) {
                        return -1;
                    } else if ( loc2 != null ) {
                        return 1;
                    }
                    return 0;
                } 
                return 0;
            }

        });

        for ( Iterator<IMarker> it = errors.iterator(); it.hasNext(); )
        {
            final IMarker tmp = it.next();
            if ( ! ( tmp instanceof CompilationMarker) ) {
                continue;
            }

            final  CompilationMarker error=(CompilationMarker) tmp;
            final int errorOffset;
            ITextRegion range;
            if ( error.getLocation() != null ) {
                range = error.getLocation();
                errorOffset= range.getStartingOffset();
            } 
            else 
            {
                errorOffset=error.getErrorOffset();
                if ( errorOffset != -1 ) {
                    range = new TextRegion( errorOffset , 1 );
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
            final String severity = error.getSeverity() != null ? error.getSeverity().getLabel()+": " : "";

            final String locationString;
            if ( hasLocation ) {
                locationString= severity+"line "+line+", column "+column+": ";
            } else if ( errorOffset != -1 ) {
                locationString=severity+"offset "+errorOffset+": ";
            } else {
                locationString=severity+"< unknown location >: ";
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
                        sourceLine = new TextRegion( thisLine.getLineStartingOffset() , nextLine.getLineStartingOffset() - thisLine.getLineStartingOffset() ).apply( source );
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

    public static String toBinaryString(int value,int padToLength) {
        return toBinaryString(value,padToLength,new int[0]);
    }

    public static String toBinaryString(int value,int padToLength,int... separatorsAtBits) {

        final StringBuilder result = new StringBuilder();
        final Set<Integer> separators = new HashSet<Integer>();
        if ( ! ArrayUtils.isEmpty( separatorsAtBits ) ) {
            for ( int bitPos : separatorsAtBits ) {
                separators.add( bitPos );
            }
        }

        for ( int i = 15 ; i >= 0 ; i-- ) {
            if ( ( value & ( 1 << i ) ) != 0 ) {
                result.append("1");
            } else {
                result.append("0");
            }
        }

        final String s = result.toString();
        if ( s.length() < padToLength ) {
            final int delta = padToLength - s.length();
            return StringUtils.repeat("0" , delta )+s;
        }
        if ( ! separators.isEmpty() ) 
        {
            final StringBuilder finalResult = new StringBuilder();
            for ( int i = result.length() -1 ; i >= 0 ; i-- ) {
                finalResult.append( result.charAt( i ) );
                final int bitOffset = result.length() -2-i;
                if ( separators.contains( bitOffset ) ) {
                    finalResult.append(" ");
                }
            }
            return finalResult.toString();
        }
        return s;
    }	

    public static String toString(List<DisassembledLine> lines) 
    {
        StringBuilder result = new StringBuilder();
        final Iterator<DisassembledLine> it = lines.iterator();
        while( it.hasNext() ) 
        {
            final DisassembledLine line = it.next();
            result.append( Misc.toHexString( line.getAddress().getValue() ) ).append(": ").append( line.getContents() );
            if ( it.hasNext() ) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    public static void copyResource(IResource source,IResource target) throws IOException {

        if ( source == null ) {
            throw new IllegalArgumentException("source must not be NULL.");
        }
        if ( target == null ) {
            throw new IllegalArgumentException("target must not be NULL.");
        }
        final InputStream in = source.createInputStream();
        try {
            final OutputStream out = target.createOutputStream(false);
            try {
                IOUtils.copy(in , out );
            } finally {
                IOUtils.closeQuietly( out );
            }
        } finally {
            IOUtils.closeQuietly( in );
        }
    }

    /**
     * Check whether a given file exists and is a directory, optionally
     * creating it.
     * 
     * @param f
     * @param createIfMissing
     * @return <code>true</code> if the directory was missing and has been created
     * @throws FileNotFoundException thrown if the directory does not exist 
     * @throws IOException thrown if creating the directory failed 
     * @throws NoDirectoryException thrown if the file exists but is no directory
     */
    public static boolean checkFileExistsAndIsDirectory(File f,boolean createIfMissing) throws 
    FileNotFoundException,IOException,NoDirectoryException 
    {
        if ( ! f.exists() ) 
        {
            if ( createIfMissing )
            {
                if ( f.mkdirs() ) {
                    return true;
                } 
                throw new IOException( "Failed to create missing directory "+f.getAbsolutePath() );
            }
            throw new FileNotFoundException("Non-existant directory "+f.getAbsolutePath() );
        }
        if ( ! f.isDirectory() ) {
            throw new IOException( f.getAbsolutePath()+" is no directory");
        }
        return false;
    }

    public static File getUserHomeDirectory() 
    {
        final String homeDirectory = System.getProperty("user.home");
        if ( StringUtils.isBlank( homeDirectory ) ) 
        {
            LOG.fatal("createDefaultConfiguration(): Failed to get user's home directory");
            throw new RuntimeException("Failed to get user's home directory");
        }
        return new File( homeDirectory );
    }

    public static void writeResource(IResource resource,String s) throws IOException 
    {
        final OutputStreamWriter writer = new OutputStreamWriter( resource.createOutputStream( false ) );
        try {
            writer.write( s );
        } finally {
            IOUtils.closeQuietly( writer );
        }
    }    

    public static void writeFile(File file,String s) throws IOException {
        writeFile( file , s.getBytes() );
    }

    public static void writeFile(File file,byte[] data) throws IOException {

        final FileOutputStream out = new FileOutputStream(file);
        try {
            IOUtils.write( data , out );
        } finally {
            IOUtils.closeQuietly( out );
        }
    }

    public static void deleteRecursively(File file) throws IOException 
    {
        deleteRecursively( file , null );
    }

    /**
     * Visit directory tree in post-order, deleting files as we go along.
     * 
     * @param file
     * @param visitor <code>null</code> or visitor that is invoked on each file/directory BEFORE
     * it get's deleted. If the visitor returns <code>false</code> , the file/directory will NOT be deleted.
     * @throws IOException
     */
    public static void deleteRecursively(File file,final IFileVisitor visitor) throws IOException {

        if ( ! file.exists() ) {
            return;
        }

        final IFileVisitor deletingVisitor = new IFileVisitor() {

            @Override
            public boolean visit(File file) throws IOException 
            {
                if ( visitor == null || visitor.visit( file ) ) 
                {
                    file.delete();
                }
                return true;
            }
        };

        visitDirectoryTreePostOrder( file , deletingVisitor );
    }

    public interface IFileVisitor 
    {
        public boolean visit(File file) throws IOException;
    }	

    public static boolean visitDirectoryTreePostOrder(File currentDir,IFileVisitor visitor) throws IOException 
    {
        if ( currentDir.isDirectory() ) 
        {
            for ( File f : currentDir.listFiles() ) 
            {
                if ( ! visitDirectoryTreePostOrder( f , visitor ) ) {
                    return false;
                }
            }
        }

        final boolean cont = visitor.visit( currentDir );
        if ( ! cont ) {
            return false;
        }		
        return true;
    }    

    public static boolean visitDirectoryTreeInOrder(File currentDir,IFileVisitor visitor) throws IOException 
    {
        final boolean cont = visitor.visit( currentDir );
        if ( ! cont ) {
            return false;
        }		

        if ( currentDir.isDirectory() ) 
        {
            for ( File f : currentDir.listFiles() ) 
            {
                if ( ! visitDirectoryTreeInOrder( f , visitor ) ) {
                    return false;
                }
            }
        }
        return true;
    }

    public static String calcHash(String data) {

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final byte[] result = digest.digest( data.getBytes()  );
        return toHexString( result );
    }

    public static <T> T[] subarray(T[] array , int beginIndex , int endIndex) {

        final Class<?> componentType = array.getClass().getComponentType();

        @SuppressWarnings("unchecked")
        final T[] result = (T[]) Array.newInstance( componentType , endIndex - beginIndex );

        int offset = 0;
        for ( int i = beginIndex ; i < endIndex ; i++ , offset++) {
            result[offset] = array[i];
        }
        return result;
    }

    public static long parseHexString(String s) throws NumberFormatException {

        String trimmed = s.toLowerCase().trim();
        if ( trimmed.startsWith("0x") ) {
            trimmed = trimmed.substring( 2 , trimmed.length() );
        }

        long result = 0;
        for ( int i = 0 ; i < trimmed.length() ; i++ ) 
        {
            result = result << 4;
            int nibble = -1;
            for ( int j = 0 ; j < HEX_CHARS.length ; j++ ) 
            {
                if ( HEX_CHARS[j] == trimmed.charAt( i ) ) {
                    nibble = j;
                    break;
                }
            }
            if ( nibble < 0 ) {
                throw new NumberFormatException("Not a valid hex string: '"+s+"'");
            }
            result = result | nibble;
        }
        return result;
    }
}