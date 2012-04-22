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

import java.util.List;

/**
 * A block of text, identified by it's starting offset and length.
 * 
 * <p>Implementations of this interface are heavily used
 * throughout the compiler to associate AST nodes with
 * the source code they came from.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface ITextRegion
{
	/**
	 * Returns the starting offset of this text region.
	 * @return
	 */
    public int getStartingOffset(); 
    
    /**
     * Returns the end offset of this text region.
     * 
     * @return end offset ( starting offset + length )
     */
    public int getEndOffset(); 
    
    /**
     * Returns the length of this text region in characters.
     * 
     * @return
     */
    public int getLength(); 
    
    /**
     * Calculates the union of this text region with another.
     * 
     * @param other
     */
    public void merge(ITextRegion other); 
    
    /**
     * Calculates the union of this text region with several others.
     * @param ranges
     */
    public void merge(List<? extends ITextRegion> ranges);
    
    /**
     * Calculates the intersection of this text region with another.
     * 
     * @param other text region to calculate intersection with
     * @throws IllegalArgumentException if <code>other</code> is <code>null</code>
     * or does not overlap with this text range at all.
     */
    public void intersect(ITextRegion other) throws IllegalArgumentException;
    
    /**
     * Subtracts another text range from this one.
     * 
     * <p>
     * Note that this method (intentionally) does <b>not</b> handle
     * intersections where the result would actually be two non-adjactent
     * regions of text.</p>     
     * @param other
     * @throws UnsupportedOperationException
     */
    public void subtract(ITextRegion other) throws UnsupportedOperationException;  
    
    /**
     * Check whether this text region is the same as another.
     * 
     * @param other
     * @return <code>true</code> if this region as the same length and starting offset
     * as the argument
     */
    public boolean isSame(ITextRegion other); 
    
    /**
     * Check whether this text region fully contains another region.
     * @param other
     * @return
     */
    public boolean contains(ITextRegion other); 
    
    /**
     * Check whether this text region overlaps with another.
     * 
     * @param other
     * @return
     */
    public boolean overlaps(ITextRegion other);
    
    /**
     * Check whether this text region covers a given offset.
     * @param offset
     * @return
     */
    public boolean contains(int offset);
    
    /**
     * Extract the region denoted by this text region from a string.
     * 
     * @param string
     * @return
     */
    public String apply(String string); 
}
