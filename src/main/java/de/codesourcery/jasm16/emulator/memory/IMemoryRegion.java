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
package de.codesourcery.jasm16.emulator.memory;

import java.util.List;

import de.codesourcery.jasm16.AddressRange;

/**
 * A memory region.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IMemoryRegion extends IMemory
{
    /**
     * The address range covered by this memory region.
     * 
     * @return
     */
    public AddressRange getAddressRange();
    
    /**
     * Returns an informal name for this memory region.
     * 
     * @return
     */
    public String getRegionName();
    
    /**
     * Check whether this memory region may be merged
     * with adjactant memory regions.
     * 
     * @return
     */
    public boolean supportsMerging();
    
    /**
     * Splits this memory region at a specific gap (aAddress range).
     * 
     * <p>
     * Note that this method does NOT modify this <code>IMemoryRegion</code> instance
     * but instead returns COPIES of this region.
     * </p>  
     * @param gap
     * @return 
     * @throws IllegalArgumentException if gap is <code>null</code> or does not intersect with this memory region
     */
    public List<IMemoryRegion> split(AddressRange gap) throws IllegalArgumentException;
}
