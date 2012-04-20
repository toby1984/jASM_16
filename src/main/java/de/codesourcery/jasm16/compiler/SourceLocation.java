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

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * A source code location identified by line number and column number.
 *   
 * @author tobias.gierke@code-sourcery.de
 * @see ITextRegion
 */
public class SourceLocation extends TextRegion implements Comparable<SourceLocation>{

	private static final Logger LOG = Logger.getLogger( SourceLocation.class );
	
	private final ICompilationUnit compilationUnit;
	private final int lineNumber;
	private final int lineStartOffset;

	public SourceLocation(SourceLocation loc , int length) 
	{
		this( loc.getCompilationUnit() , loc.getOffset() , loc.getLineNumber() , loc.getLineStartOffset() , length );
	}
	
	public SourceLocation(ICompilationUnit unit,Line line, ITextRegion range) 
	{
	    this( unit , range.getStartingOffset() , line.getLineNumber() , line.getLineStartingOffset() , range.getLength() );
	}
	
	public SourceLocation(ICompilationUnit compilationUnit,
			int offset,
			int lineNumber,
			int lineStartOffset,
			int length) 
	{
	    super( offset , length );
		if ( compilationUnit == null ) {
			throw new IllegalArgumentException("compilationUnit must not be NULL");
		}
		if ( lineNumber < 1 ) {
			throw new IllegalArgumentException("offset must not be >= 1");
		}		
		if ( lineStartOffset < 0 ) {
			throw new IllegalArgumentException("lineStartOffset must not be >= 0");
		}		
		this.lineNumber = lineNumber;
		this.compilationUnit = compilationUnit;
		this.lineStartOffset = lineStartOffset;
	}
	
	public int getLineNumber() {
		return lineNumber;
	}
	
	public int getColumnNumber() {
		int column = getStartingOffset() - lineStartOffset;
		if ( column < 0 ) {
			LOG.warn("getColumnNumber(): Source location with error offset not on the specified line ?"+this);
			return 1; // humans start counting at 1 ....
		}
		return column+1; // humans start counting at 1 ....
	}
	
	public int getLineStartOffset() {
		return lineStartOffset;
	}

	public ICompilationUnit getCompilationUnit() {
		return compilationUnit;
	}

	public int getOffset() {
		return getStartingOffset();
	}
	
	@Override
	public String toString() {
		return compilationUnit+" , line "+lineNumber+" , column "+getColumnNumber()+" , offset "+getStartingOffset();
	}

	@Override
	public int compareTo(SourceLocation o) 
	{
		if ( this.getOffset() < o.getOffset() ) {
			return -1;
		} else if ( this.getOffset() > o.getOffset() ) {
			return 1;
		}
		return 0;
	}

}
