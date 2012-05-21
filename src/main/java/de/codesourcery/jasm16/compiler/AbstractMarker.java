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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

/**
 * Base-class to help with implementing {@link IMarker}s.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AbstractMarker implements IMarker
{
    private final String type;
    private final ICompilationUnit unit;
    
    private final Map<String,Object> attrs = new HashMap<String,Object>();
    
    @Override
    public String toString()
    {
        return "marker_type="+type+", compilation unit= "+unit.getIdentifier()+" , attributes="+attrs;
    }
    
    protected AbstractMarker(String type,ICompilationUnit unit) {
        
        if (StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("type must not be NULL/blank.");
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be NULL.");
        }
        this.type = type;
        this.unit = unit;
    }

    @Override
    public boolean hasAttribute(String name)
    {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be NULL/blank.");
        }
        return attrs.containsKey( name );
    }
    
    @Override
    public ICompilationUnit getCompilationUnit() {
        return unit;
    }
    
    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public boolean hasType(String type)
    {
        if (type == null) {
            throw new IllegalArgumentException("type must not be NULL.");
        }
        return getType().equals( type );
    }

    @Override
    public int getAttribute(String name, int defaultValue)
    {
        Object object = attrs.get( name );
        if ( object == null ) {
            return defaultValue;
        }
        return (Integer) object;
    }

    @Override
    public String getAttribute(String name, String defaultValue)
    {
        Object object = attrs.get( name );
        if ( object == null ) {
            return defaultValue;
        }
        return (String) object;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAttribute(String name, T defaultValue)
    {
        Object object = attrs.get( name );
        if ( object == null ) {
            return defaultValue;
        }
        return (T) object;
    }

    @Override
    public void delete()
    {
        unit.deleteMarker( this );
    }

    @Override
    public void setAttribute(String name, int value)
    {
        attrs.put( name , value );
    }

    @Override
    public void setAttribute(String name, String value)
    {
        attrs.put( name , value );
    }

    @Override
    public void setAttribute(String name, Object value)
    {
        attrs.put( name , value );
    }

}
