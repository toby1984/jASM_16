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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;

/**
 * Stores object code relocation information.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see ICompilationUnit#getRelocationTable()
 */
public final class RelocationTable
{
    private final Set<Address> relocationTable = new HashSet<Address>();
    
    public void clear() 
    {
        relocationTable.clear();
    }
    
    public void addRelocationEntry(Address address) throws IOException 
    {
        if (address == null) {
            throw new IllegalArgumentException("address must not be NULL.");
        }
        relocationTable.add( address );
    }
    
    public void merge(RelocationTable other,Address offset) {
        
        for ( Address adr : other.relocationTable ) 
        {
            Address realAdr = adr.plus( offset , false );
            if ( realAdr.getWordAddressValue() > WordAddress.MAX_ADDRESS ) {
                throw new RuntimeException("Internal error, adress out-of-range: "+realAdr);
            }
            relocationTable.add( realAdr  );
        }
    }
    
    public Size getBinarySize() {
        return Size.ONE_WORD.plus( Size.words( relocationTable.size() ) );
    }
    
    public byte[] toByteArray() throws IOException 
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        // write number of table entries
        final byte[] data = new byte[2];
        data[0] = (byte) ((relocationTable.size() >> 8) & 0xff);
        data[1] = (byte) (relocationTable.size() & 0xff);
        out.write( data );
        
        // write table entries with offsets that need relocation
        for ( Address adr : relocationTable ) 
        {
            final int value = adr.getWordAddressValue();
            data[0] = (byte) ((value >> 8) & 0xff);
            data[1] = (byte) (value & 0xff);
            out.write(data);
        }
        return out.toByteArray();
    }    
}