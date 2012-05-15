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

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;

/**
 * Read-only memory.
 * 
 * <p>Note that all memory is assumed to start at address 0 (zero) and since the DCPU-16 only supports
 * word-sized addressing, the memory also only supports addressing individual words.</p>
 * 
 * <p>Implementations need to be THREAD-SAFE.</p>
 * @author tobias.gierke@code-sourcery.de
 */
public interface IReadOnlyMemory 
{
    /**
     * Returns the memory size.
     * 
     * @return
     */
	public Size getSize();
	
	/**
	 * Reads a 16-bit word from a memory location.
	 * 
	 * @param wordAddress
	 * @return
	 * @deprecated This method only exists for performance reasons in tight loops (so no intermediate {@link Address} objects need to be created) , try to use
	 * {@link #read(Address)} wherever possible to avoid conversion errors between byte- and word-sized addresses
	 */
    public int read(int wordAddress);
    
    /**
     * Reads a 16-bit word from a memory location.
     * 
     * @param address
     * @return
     */
    public int read(Address address);
}
