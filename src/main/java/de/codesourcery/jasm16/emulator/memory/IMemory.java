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

/**
 * Memory that is both readable and writeable.
 * 
 * <p>Implementations need to be THREAD-SAFE.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IMemory extends IReadOnlyMemory {
    
    public void clear();
    
    /**
     * 
     * @param wordAddress
     * @param value
     * @deprecated This method only exists for performance reasons in tight loops (so no intermediate {@link Address} objects need to be created) , try to use
     * {@link #write(Address,int)} wherever possible to avoid conversion errors between byte- and word-sized addresses.    
     */
    public void write(int wordAddress,int value);
    
    public void write(Address address,int value);
    
}