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

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;

/**
 * A 'unit of work' that can be processed by the compiler.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see ICompiler#compile(List, ICompilationListener)
 */
public interface ICompilationUnit {

	/**
	 * Returns the source code resource associated with this compilation unit.
	 * 
	 * @return
	 */
	public IResource getResource();
	
	/**
	 * Returns the relocation table for this compilation unit.
	 * 
	 * @return
	 */
	public RelocationTable getRelocationTable();

	/**
	 * Returns a unique identifier for this compilation unit.
	 * 
	 * @return
	 */
	public String getIdentifier();	

	/**
	 * Returns the symbol table of this compilation unit.
	 * 
	 * @return symbol table for this compilation unit.
	 */
	public ISymbolTable getSymbolTable();
	
	/**
	 * Returns the AST of this compilation unit.
	 * 
	 * @return AST or <code>null</code> if the associated source has either not
	 * been parsed yet or something went seriously wrong when the source was parsed (like
	 * an I/O exception occuring etc.)
	 */
	public AST getAST();
	
	/**
	 * Sets the AST of this compilation unit.
	 * 
	 * @param ast AST or <code>null</code> 
	 */
	public void setAST(AST ast);	
	
	/**
	 * House-keeping method invoked on each compilation unit
	 * before compilation starts.
	 * 
	 * <p>When this method gets called, most implementations want to make sure that no
	 * stale (object) files from a previous compilation run are still around.</p>
	 */
	public void beforeCompilationStart();	
	
    /**
     * Returns the source code line locations that intersect with a given
     * {@link ITextRegion}.
     * 
     * @param region
     * @return lines ordered ascending by line number
     */
    public List<Line> getLinesForRange(ITextRegion region);
    
    /**
     * Returns the source code line location for a given absolute offset.
     *   
     * @param offset
     * @return
     * @throws NoSuchElementException if no line could be found for the given offset
     * (either because the offset is invalid , compilation was not performed yet
     * or compilation failed severely before this line was parsed).
     */
    public Line getLineForOffset(int offset) throws NoSuchElementException;
    
    /**
     * Returns the line that comes right before a given <code>Line</code>
     * instance.
     * @param line
     * @return previous line or <code>null</code> if this is the first line
     */
    public Line getPreviousLine(Line line);
    
    /**
     * Returns the text coordinates for a given line number.
     *   
     * @param lineNumber line number (first line is 1)
     * @return
     * @throws NoSuchElementException if no line could be found with the given line number
     * (either because the line number is invalid , compilation was not performed yet
     * or compilation failed severely before this line was parsed).
     */
    public Line getLineByNumber(int lineNumber) throws IndexOutOfBoundsException;
    
    /**
     * Returns the number of parsed lines in this compilation unit.
     * 
     * <p>Note that the returned value may actually different
     * from the actual line count (because of severe compilation errors etc.).
     * This value is only guaranteed to be exact when a compilation unit
     * compiled without any errors.</p>
     * 
     * @return
     */
    public int getParsedLineCount();
	
    /**
     * Returns the source code for a given source code location.
     * 
     * @param region
     * @return
     * @throws IOException
     */
	public String getSource(ITextRegion region) throws IOException;
	
	/**
	 * Registers a source-code line with this compilation unit.
	 *
	 * @param l line to register, if there already is a line registered for this
	 * line number, it will be replaced
	 */
    public void setLine(Line l);
    
    /**
     * Returns all lines of this compilation unit.
     * 
     * @return
     * 
     * @see #setLine(Line)
     */
    public List<Line> getLines();

    /**
     * Converts a {@link ITextRegion} to a {@link SourceLocation} instance
     * referring to this compilation unit.
     * 
     * @param textRegion
     * @return
     * @throws NoSuchElementException if the source location (line number) for this 
     * text region could not be determined from the AST (maybe because the AST failed to parse
     * or wasn't parsed yet).
     */
	public SourceLocation getSourceLocation(ITextRegion textRegion) throws NoSuchElementException;

	/**
	 * Deletes a specific marker.
	 * 
	 * @param marker
	 */
    public void deleteMarker(IMarker marker);
    
    /**
     * Returns whether this compilation unit contains any errors.
     * 
     * <p>If there are any errors, these can be retrieved by calling {@link #getErrors()}.</p>
     * 
     * @return
     * @see #getErrors()
     */
    public boolean hasErrors();
    
    /**
     * Adds a marker to this compilation unit.
     * 
     * @param error
     */
    public void addMarker(IMarker marker);
    
    /**
     * Returns markers with a specific type. 
     * 
     * @param types list of expected types, if <code>null</code> or empty this method returns <b>ALL</b> markers
     * @return list of markers
     */
    public List<IMarker> getMarkers(String... types);        
    
    /**
     * Returns all compilation error of this compilation unit.
     * 
     * @return compilation errors or an empty list if this unit was not compiled yet.
     */
    public List<ICompilationError> getErrors();    
    
    /**
     * Returns all compilation warnings of this compilation unit.
     * 
     * @return compilation warnings or an empty list if this unit was not compiled yet.
     * @see CompilationWarning
     */    
    public List<ICompilationError> getWarnings();
    
    /**
     * Returns the offset where object code generated from this compilation 
     * unit should be located in-memory.
     * 
     * @return
     */
    public Address getObjectCodeStartOffset();
    
    /**
     * Sets the offset where object code generated from this compilation 
     * unit should be located in-memory.
     * 
     * <p>Note that the DCPU-16 platform requires all addresses to be 16-bit aligned.</p>
     * 
     * @param address
     */
    public void setObjectCodeStartOffset(Address address);
    
    /**
     * Registers a compilation unit <b>this</b> unit depends on.
     * 
     * <p>This method gets invoked when parsing source code inclusion AST nodes.</p>
     * @param unit
     */
    public void addDependency(ICompilationUnit unit);

    /**
     * Returns all compilation units this unit depends on.
     * 
     * <p>To be more precise, this method currently returns all compilation units
     * that are included using a '.include' preprocessor command by <b>this</b> unit.</p>
     * 
     * @return
     */
    public List<ICompilationUnit> getDependencies();
    
    // TODO: Used for debugging only, remove one done !!!
    public void dumpSourceLines();
    
    /**
     * Creates a new compilation unit from this one , associated with a different resource.
     * 
     * <p>
     * This method re-uses the following data:
     * 
     * <ul>
     *   <li>compilation-unit identifier</li>
     *   <li>symbol table</li>
     *   <li>relocation table</li>
     * </ul>
     * 
     * </p>
     * 
     * @param resource
     * @return
     */
    public ICompilationUnit withResource(IResource resource);    
}