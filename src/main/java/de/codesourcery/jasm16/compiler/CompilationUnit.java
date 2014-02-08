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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.StringResource;
import de.codesourcery.jasm16.exceptions.CircularSourceIncludeException;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Default {@link ICompilationUnit} implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class CompilationUnit implements ICompilationUnit {

    private static final Logger LOG = Logger.getLogger(CompilationUnit.class);
    
    private final String identifier;
    private final Map<String,List<IMarker>> markers = new HashMap<String,List<IMarker>>();
    private final IResource resource;
    private volatile AST ast;
    private volatile Address objectCodeStartAddress = Address.ZERO;
    private final List<ICompilationUnit> dependencies;
    private final ISymbolTable symbolTable;
    private final RelocationTable relocationTable;

    private Map<Integer,Line> lines = new HashMap<Integer,Line>();

    private CompilationUnit(CompilationUnit unit, IResource resource) 
    {
    	this.identifier = unit.getIdentifier();
    	// this.markers = unit.markers;
    	this.resource = resource;
    	// this.ast = unit.getAST();
    	// this.objectCodeStartAddress = unit.getObjectCodeStartOffset();
    	this.dependencies = unit.dependencies;
    	this.symbolTable = unit.symbolTable;
    	this.relocationTable = unit.relocationTable;
    	this.lines = unit.lines;
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
        if ( ! resource.hasType( ResourceType.SOURCE_CODE ) ) {
        	throw new IllegalArgumentException("Cannot create compilation unit from resource "+resource);
        }
        this.dependencies = new ArrayList<ICompilationUnit>();
        this.resource = resource;
        this.identifier=identifier;
        this.symbolTable = new SymbolTable("CompilationUnit: "+resource);
        this.relocationTable = new RelocationTable();
    }    
    
    @Override
    public ICompilationUnit withResource(IResource resource) 
    {
    	return new CompilationUnit(this,resource);
    }
    
    @Override
    public void dumpSourceLines()
    {
        List<Integer> keys = new ArrayList<>(lines.keySet());
        Collections.sort(keys);
        for ( Integer key : keys ) {
            System.out.println( lines.get(key) );
        }
    }
    
    @Override
    public void beforeCompilationStart() 
    {
        System.err.println("----------------- before compilation of "+this+" -----------------");
        this.relocationTable.clear();
        this.lines.clear();
        this.ast = null;
        this.symbolTable.clear();
        this.symbolTable.setParent( null );
        this.markers.remove( IMarker.TYPE_COMPILATION_WARNING);
        this.markers.remove( IMarker.TYPE_COMPILATION_ERROR );
        this.markers.remove( IMarker.TYPE_GENERIC_COMPILATION_ERROR );
        this.objectCodeStartAddress=Address.ZERO;
        this.dependencies.clear();
    }
    
    public static ICompilationUnit createInstance(final String identifier, final IResource resource) 
    {
        return new CompilationUnit(identifier,resource);
    }
    
    public static ICompilationUnit createInstance(final String identifier, final String source) 
    {
        return new CompilationUnit( identifier  , new StringResource( identifier , source , ResourceType.SOURCE_CODE) );
    }

    @Override
    public RelocationTable getRelocationTable()
    {
        return relocationTable;
    }
    
    @Override
    public void setLine(Line l) 
    {
        if (l == null) {
            throw new IllegalArgumentException("line must not be NULL.");
        }
        Line existing = lines.get( l.getLineNumber() );
        
        /* 
         * Do not update line references that have already been parsed.
         * 
         * The sanity check (commented-out below) is wrong during macro expansion because
         * instructions/lines in expanded macros get mapped to the
         * lines of the macro definition. If the macro takes parameters,
         * the absolute line starting offsets may change because of different
         * parameter lengths.
         * 
         * Example:
         * 
         * .macro dummy(param1) // line start offset: 0
         *    SET A,param1 // line start offset: 21
         *    SET B,2  // line start offset: 37 <<<<<<<<<< sanity check would break compilation 
         * .endmacro
         * 
         * dummy(X)
         * 
         * For the marked line, parsing the source yields an absolute line start offset of 37.
         * When the macro invocation is expanded (virtually at the same location of the original macro body)
         * , "param1' will be replaced with 'X' , shifting the line starting offset of the marked line by -5.
         * Since AST#parseInternal() unconditionally invokes ICompilationUnit#setLine() , this would trip
         * our sanity check which is thus disabled.  
         */
        if ( existing == null )  
        {
        	lines.put( l.getLineNumber() , l );
        }
//        if ( existing != null && ! existing.equals( l ) ) {
//        	TextRegion r1= new TextRegion(existing.getLineStartingOffset() , 10 );
//        	TextRegion r2= new TextRegion(l.getLineStartingOffset() , 10 );
//        	String text1 = "<invalid region "+r1+">";
//        	String text2 = "<invalid region "+r2+">";
//        	try {
//        		text1 = getSource(r1);
//        	} catch(Exception e) { /* can't help it */ }
//        	try {
//        		text2 = getSource(r2);
//        	} catch(Exception e) { /* can't help it */ }
//        	throw new RuntimeException("Won't replace existing line "+existing+" with "+l);
//        }
    }
    
    @Override
    public int getParsedLineCount() {
        return lines.size();
    }
    
    @Override
    public SourceLocation getSourceLocation(ITextRegion textRegion)
    {
    	final Line l = getLineForOffset( textRegion.getStartingOffset() );
        return new SourceLocation(this,l,textRegion);
    }
    
    @Override
    public Line getPreviousLine(Line line)
    {
        if (line == null) {
            throw new IllegalArgumentException("line must not be NULL.");
        }
        
        Line previous = null;
        for (Iterator<Line> it = lines.values().iterator(); it.hasNext();) 
        {
            final Line l = it.next();
            if ( line.equals( l ) ) {
                return previous;
            }
            previous = l;
        }
        return null;
    }
    @Override
    public Line getLineForOffset(int offset) throws NoSuchElementException
    {
        if ( offset < 0 ) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        Line previousLine = null;
        for (Iterator<Line> it = lines.values().iterator(); it.hasNext();) 
        {
            final Line l = it.next();
            
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
        if ( previousLine != null && previousLine.getLineStartingOffset() <= offset ) {
            return previousLine;
        }
        throw new NoSuchElementException("Found no line with offset "+offset);
    }

    /**
     * 
     * @param range
     * @return lines ordered ascending by line number
     */
    @Override
    public List<Line> getLinesForRange(ITextRegion range) 
    {
        if (range == null) {
            throw new IllegalArgumentException("range must not be NULL.");
        }
        
        final List<Line> result = new ArrayList<Line>();
        for ( Line l : lines.values() ) 
        {
            if ( range.contains( l.getLineStartingOffset() ) ) {
                result.add( l );
            }
        }
        Comparator<Line> comparator = new Comparator<Line>() {
			
			@Override
			public int compare(Line o1, Line o2) {
				return Integer.valueOf( o1.getLineNumber() ).compareTo( Integer.valueOf( o2.getLineNumber() ) );
			}
		}; 
		Collections.sort( result , comparator );
        return result;
    }

    public static ICompilationUnit createInstance(final String identifier,final File sourceFile) 
    {
        return new CompilationUnit(identifier , new FileResource( sourceFile , ResourceType.SOURCE_CODE ) );
    }	

    @SuppressWarnings({ "unchecked", "cast" })
    @Override
    public List<ICompilationError> getErrors() {
        return (List<ICompilationError>) internalGetMarkers( IMarker.TYPE_COMPILATION_ERROR , IMarker.TYPE_GENERIC_COMPILATION_ERROR );
    }
    
    @SuppressWarnings("unchecked")
	@Override
    public List<ICompilationError> getWarnings() {
        return (List<ICompilationError>) internalGetMarkers( IMarker.TYPE_COMPILATION_WARNING );
    }

    @Override
    public boolean hasErrors() 
    {
        return this.markers.get( IMarker.TYPE_COMPILATION_ERROR) != null ||
        	   this.markers.get( IMarker.TYPE_GENERIC_COMPILATION_ERROR ) != null;
    }

    @Override
    public void addMarker(IMarker marker) {
        if (marker == null) {
            throw new IllegalArgumentException("marker must not be NULL");
        }
        
        LOG.debug("addMarker(): "+marker);        
        
        List<IMarker> markersByType = this.markers.get( marker.getType() );
        if ( markersByType == null ) {
            markersByType = new ArrayList<IMarker>();
            this.markers.put( marker.getType() , markersByType );
        }
        markersByType.add( marker );
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
    	if (ast == null) {
			throw new IllegalArgumentException("ast must not be NULL");
		}
        this.ast = ast;
    }
    
    @Override
    public IResource getResource()
    {
        return resource;
    }

    @Override
    public String getSource(ITextRegion range) throws IOException
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
        
        final Set<String> uniqueTypes = new HashSet<String>( Arrays.asList( types ) );
        
        for ( String expectedType : uniqueTypes ) 
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
        return getResource().toString()+" (has_errors="+hasErrors()+"]";
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

	@Override
	public void addDependency(ICompilationUnit unit) 
	{
		if (unit == null) {
			throw new IllegalArgumentException("unit must not be NULL");
		}
		checkForCircularDependencies( unit );
		this.dependencies.add( unit );
	}

	private void checkForCircularDependencies(ICompilationUnit unit) 
	{
		final Set<String> resourceIdentifiers = new HashSet<String>();
		
		final List<ICompilationUnit> newSet = new ArrayList<ICompilationUnit>( this.dependencies );
		newSet.add( unit );
		
		for ( ICompilationUnit current : newSet ) {
			checkForCircularDependencies( current , resourceIdentifiers );
		}
	}

	private void checkForCircularDependencies(ICompilationUnit current, Set<String> resourceIdentifiers) 
	{
		if ( resourceIdentifiers.contains( current.getResource().getIdentifier() ) ) {
			LOG.error("createParseContextForInclude(): Circular dependency detected: resource '"+resource.getIdentifier()+"'");
			throw new CircularSourceIncludeException( "Detected circular source inclusion" , current );
		}
		resourceIdentifiers.add( current.getResource().getIdentifier() );
		for ( ICompilationUnit dep : current.getDependencies() ) {
			checkForCircularDependencies( dep , resourceIdentifiers );
		}
	}

	@Override
	public List<ICompilationUnit> getDependencies() {
		return new ArrayList<ICompilationUnit>( this.dependencies );
	}
	
	@Override
	public ISymbolTable getSymbolTable()
	{
	    return symbolTable;
	}

	@Override
	public List<Line> getLines() 
	{
		return new ArrayList<>( this.lines.values() );
	}
}
