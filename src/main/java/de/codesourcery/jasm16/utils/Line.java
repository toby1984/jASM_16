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

/**
 * Represents a line of text.
 * 
 * <p>A line of text is described by it's line number (starting at 1) and
 * it's starting offset relative to the start of the input.</p>
 * @author tobias.gierke@code-sourcery.de
 */
public class Line
{
    private final int lineNumber;
    private final int lineStartingOffset;
    
    /**
     * Create instance.
     * 
     * @param lineNumber
     * @param lineStartingOffset
     */
    public Line(int lineNumber, int lineStartingOffset)
    {
    	if ( lineNumber < 1 ) {
    		throw new IllegalArgumentException("invalid line number "+lineNumber);
    	}
    	if ( lineStartingOffset < 0 ) {
    		throw new IllegalArgumentException("invalid offset "+lineStartingOffset);
    	}
        this.lineNumber = lineNumber;
        this.lineStartingOffset = lineStartingOffset;
    }
    
    /**
     * Returns the line number.
     * @return line number , starting with 1
     */
    public int getLineNumber()
    {
        return lineNumber;
    }
    
    /**
     * Returns the column number for a specific offset.
     * 
     * @param offset
     * @return
     * @throws IllegalArgumentException if the input offset is less than this line's 
     * starting offset.
     */
    public int getColumnNumber(int offset) 
    {
    	if ( offset < lineStartingOffset ) {
    		throw new IllegalArgumentException("Not in line "+this+": "+offset);
    	}
    	return 1+(offset - lineStartingOffset);
    }

    /**
     * Returns the absolute starting offset of this line in the input.
     * 
     * @return absolute index where this line starts. 
     */
    public int getLineStartingOffset()
    {
        return lineStartingOffset;
    }
    
    @Override
    public String toString() {
    	return "line "+lineNumber+" (start: "+lineStartingOffset+")";
    }


    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + lineNumber;
        result = prime * result + lineStartingOffset;
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Line other = (Line) obj;
        if (lineNumber != other.lineNumber) {
            return false;
        }
        if (lineStartingOffset != other.lineStartingOffset) {
            return false;
        }
        return true;
    }
}
