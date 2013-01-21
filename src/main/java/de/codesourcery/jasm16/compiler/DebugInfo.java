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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.codesourcery.jasm16.Address;

public class DebugInfo
{
    private final Map<Address,SourceLocation> location = new HashMap<>();
    
    private final Map<String,ICompilationUnit> compilationUnits = new HashMap<>();
    
    public DebugInfo() {
    }
    
    public void clear() {
        location.clear();
        compilationUnits.clear();
    }
    
    public Collection<ICompilationUnit> getCompilationUnits()
    {
        return compilationUnits.values();
    }
    
    public void relocate(Address offset) {
        
        final Map<Address,SourceLocation> newLocations = new HashMap<>();
        
        for ( Entry<Address, SourceLocation> entry : location.entrySet() ) {
            newLocations.put( entry.getKey().plus( offset,false ) , entry.getValue() );
        }
        location.clear();
        location.putAll(newLocations);
    }
    
    public void addSourceLocation(Address address,SourceLocation loc) {
        if ( address == null ) {
            throw new IllegalArgumentException("address must not be NULL.");
        }
        if ( loc == null ) {
            throw new IllegalArgumentException("location must not be NULL.");
        }
        location.put(address,loc);
        compilationUnits.put( loc.getCompilationUnit().getResource().getIdentifier() , loc.getCompilationUnit() );
    }
    
    public SourceLocation getSourceLocation(Address address) 
    {
        if (address == null) {
            throw new IllegalArgumentException("address must not be NULL.");
        }
        return location.get(address);
    }
    
    // debug
    public String dumpToString() {
        
        List<Address> addresses = new ArrayList<>( location.keySet() );
        Collections.sort(addresses);
        StringBuilder result = new StringBuilder();
        for (Iterator<Address> it = addresses.iterator(); it.hasNext();) 
        {
            final Address address = it.next();
            SourceLocation loc = location.get(address);
            result.append( address.toString()+" = "+loc.getCompilationUnit());
            if ( it.hasNext() ) {
                result.append("\n");
            }
        }
        return result.toString(); 
    }
}