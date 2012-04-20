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
package de.codesourcery.jasm16.compiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.StringResource;
import de.codesourcery.jasm16.utils.ITextRange;
import de.codesourcery.jasm16.utils.Line;

/**
 * Default {@link ICompilationUnit} implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class CompilationUnit implements ICompilationUnit {

    private static final Logger LOG = Logger.getLogger(CompilationUnit.class);
    
    private final String identifier;
    private final Map<String,List<IMarker>> markers = new HashMap<String,List<IMarker>>();
    private final IResource resource;
    private AST ast;
    private Address objectCodeStartAddress = Address.ZERO;

    private final Map<Integer,Line> lines = new HashMap<Integer,Line>();

    @Override
    public void beforeCompilationStart() {
        this.lines.clear();
        this.ast = null;
        this.markers.clear();
        this.objectCodeStartAddress=Address.ZERO;
    }
    
    public static ICompilationUnit createInstance(final String identifier, final IResource resource) 
    {
        return new CompilationUnit(identifier,resource);
    }
    
    public static ICompilationUnit createInstance(final String identifier, final String source) 
    {
        return new CompilationUnit( identifier  , new StringResource(source) );
    }

    @Override
    public void setLine(Line l) 
    {
        if (l == null) {
            throw new IllegalArgumentException("line must not be NULL.");
        }
        lines.put( l.getLineNumber() , l );
    }
    
    @Override
    public int getParsedLineCount() {
        return lines.size();
    }
    
    @Override
    public SourceLocation getSourceLocation(ITextRange textRange)
    {
        final List<Line> lines = getLinesForRange( textRange );
        if ( lines.isEmpty() ) {
            throw new NoSuchElementException("No line for range "+textRange);
        }
        
        return new SourceLocation(this,lines.get(0),textRange);
    }

    @Override
    public Line getLineForOffset(int offset) throws NoSuchElementException
    {
        if ( offset < 0 ) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        Line previousLine = null;
        for ( Line l : lines.values() ) 
        {
            if ( l.getLineStartingOffset() == offset ) {
                return l;
            }
            if ( previousLine != null && 
                 previousLine.getLineStartingOffset() < offset &&
                 l.getLineStartingOffset() > offset ) 
            {
                return previousLine;
            }
            previousLine = l;
        }
        throw new NoSuchElementException("Found no line with offset "+offset);
    }

    /**
     * 
     * @param range
     * @return lines ordered ascending by line number
     */
    @Override
    public List<Line> getLinesForRange(ITextRange range) 
    {
        if (range == null) {
            throw new IllegalArgumentException("range must not be NULL.");
        }
        
        final List<Line> result = new ArrayList<Line>();
        // note that lines must be returned ordered ascending by line number here !!!
        for ( Line l : lines.values() ) 
        {
            if ( range.contains( l.getLineStartingOffset() ) ) {
                result.add( l );
            }
        }
        return result;
    }

    public static ICompilationUnit createInstance(final String identifier, 
            final File sourceFile) 
    {
        return new CompilationUnit(identifier , new FileResource( sourceFile ) );
    }	

    @SuppressWarnings({ "unchecked", "cast" })
    @Override
    public List<ICompilationError> getErrors() {
        return (List<ICompilationError>) internalGetMarkers( IMarker.TYPE_COMPILATION_ERROR , IMarker.TYPE_GENERIC_COMPILATION_ERROR );
    }

    @Override
    public boolean hasErrors() 
    {
        return this.markers.get( IMarker.TYPE_COMPILATION_ERROR) != null || this.markers.get( IMarker.TYPE_GENERIC_COMPILATION_ERROR ) != null;
    }

    @Override
    public void addMarker(IMarker marker) {
        if (marker == null) {
            throw new IllegalArgumentException("marker must not be NULL");
        }
        
        LOG.info("addMarker(): "+marker);        
        
        List<IMarker> markersByType = this.markers.get( marker.getType() );
        if ( markersByType == null ) {
            markersByType = new ArrayList<IMarker>();
            this.markers.put( marker.getType() , markersByType );
        }
        markersByType.add( marker );
    }

    protected CompilationUnit(String identifier,IResource resource) 
    {
        if (StringUtils.isBlank(identifier)) {
            throw new IllegalArgumentException(
                    "identifier must not be NULL/blank");
        }
        if ( resource == null ) {
            throw new IllegalArgumentException("resource must not be NULL.");
        }
        this.resource = resource;
        this.identifier=identifier;
    }

    @Override
    public boolean equals(Object obj) {
        if ( obj == this ) {
            return true;
        }
        if ( obj instanceof ICompilationUnit ) {
            return this.identifier.equals( ((ICompilationUnit) obj).getIdentifier() );
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return this.identifier.hashCode();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public AST getAST() {
        return ast;
    }

    @Override
    public void setAST(AST ast) {
        this.ast = ast;
    }
    
    @Override
    public IResource getResource()
    {
        return resource;
    }

    @Override
    public String getSource(ITextRange range) throws IOException
    {
        return getResource().readText(range);
    }

    @Override
    public Line getLineByNumber(int lineNumber) throws IndexOutOfBoundsException
    {
        final Line result = lines.get( lineNumber );
        if ( result == null ) {
            throw new IndexOutOfBoundsException("No line with number "+lineNumber);
        }
        return result;
    }

    @Override
    public void deleteMarker(IMarker marker)
    {
        final List<IMarker> markers = this.markers.get( marker.getType() );
        if ( markers != null ) {
            for (Iterator<IMarker> iterator = markers.iterator(); iterator.hasNext();) {
                final IMarker iMarker = iterator.next();
                if ( iMarker == marker ) {
                    iterator.remove();
                    break;
                }
            }
            if ( markers.isEmpty() ) {
                this.markers.remove( marker.getType() );
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public List<IMarker> getMarkers(String... types)
    {
        return internalGetMarkers( types );
    }
    
    @SuppressWarnings("rawtypes")
    private List internalGetMarkers(String... types)
    {
        final List<IMarker>  result = new ArrayList<IMarker>();
        
        if ( ArrayUtils.isEmpty( types ) ) 
        {
            for ( List<IMarker> l : this.markers.values() ) {
            	result.addAll( l );
            }
            return result;
        }
        
        for ( String expectedType : types ) 
        {
            final List<IMarker> existing = this.markers.get(expectedType);
            if ( existing != null ) {
                result.addAll( new ArrayList<IMarker>( existing ) );
            }
        }
        return result;
    }
    
    @Override
    public String toString()
    {
        return getResource().toString();
    }

    @Override
    public Address getObjectCodeStartOffset()
    {
        return objectCodeStartAddress;
    }

    @Override
    public void setObjectCodeStartOffset(Address address)
    {
        if (address == null) {
            throw new IllegalArgumentException("address must not be NULL.");
        }
        
        this.objectCodeStartAddress = address;
    }
}
