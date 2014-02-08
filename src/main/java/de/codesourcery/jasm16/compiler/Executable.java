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

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.utils.ITextRegion;

public abstract class Executable extends FileResource 
{
	private final String identifier;
	private final DebugInfo debugInfo;
	
	public Executable(String identifier,DebugInfo debugInfo) 
	{
		super(new File(identifier),ResourceType.EXECUTABLE);
		
		if (StringUtils.isBlank(identifier)) {
			throw new IllegalArgumentException(
					"identifier must not be blank/null");
		}
		if ( debugInfo == null ) {
            throw new IllegalArgumentException("debugInfo must not be NULL.");
        }
		this.identifier =identifier;
		this.debugInfo = debugInfo;
	}

	public DebugInfo getDebugInfo()
    {
        return debugInfo;
    }

	@Override
	public String getIdentifier() {
		return identifier;
	}
	
	public boolean refersTo(IResource r) 
	{
		if ( getIdentifier().equals( r.getIdentifier() ) ) 
		{
			return true;
		}
		
		for ( ICompilationUnit unit : debugInfo.getCompilationUnits() ) 
		{
			if ( unit.getResource().getIdentifier().equals( r.getIdentifier() ) ) {
				return true;
			}
		}		
		return false;
	}

	@Override
	public String readText(ITextRegion range) throws IOException 
	{
		throw new UnsupportedOperationException("Not implemented");
	}
}
