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
package de.codesourcery.jasm16.disassembler;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.utils.Misc;

/**
 * A line of source code created from disassembling an object file.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class DisassembledLine 
{
    private final Address address;
    private final Size instructionLength;
    private final String contents;
    
    public DisassembledLine(Address address, String contents,Size instructionLength)
    {
        if ( address== null ) {
            throw new IllegalArgumentException("address  must not be NULL.");
        }
        if ( contents == null ) {
            throw new IllegalArgumentException("contents must not be NULL.");
        }            
        if ( instructionLength == null ) {
			throw new IllegalArgumentException("instructionLen must not be null");
		}
        this.instructionLength = instructionLength;
        this.address = address;
        this.contents = contents;
    }
    
    public Size getInstructionLength() {
		return instructionLength;
	}
    
    public Address getAddress()
    {
        return address;
    }
    
    public String getContents()
    {
        return contents;
    }
    
    @Override
    public String toString() {
    	return Misc.toHexString( getAddress() )+": "+getContents()+" ( "+instructionLength+" )";
    }
}