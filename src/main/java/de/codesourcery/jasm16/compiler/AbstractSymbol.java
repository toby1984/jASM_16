package de.codesourcery.jasm16.compiler;

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

import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Abstract base-class for symbols.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AbstractSymbol implements ISymbol {

	private final ISymbol scope;
	private final ICompilationUnit unit;
	private final Identifier identifier;
	private final ITextRegion location;
	
	protected AbstractSymbol(ICompilationUnit unit , ITextRegion location , Identifier identifier) {
		this(unit,location,identifier,null);
	}
	
	protected AbstractSymbol(ICompilationUnit unit , ITextRegion location , Identifier identifier,ISymbol scope) {
		if (identifier == null) {
			throw new IllegalArgumentException("identifier must not be NULL");
		}
		if ( location == null ) {
            throw new IllegalArgumentException("location must not be NULL.");
        }
		this.scope = scope;
		this.location = new TextRegion( location );
		this.unit = unit;
		this.identifier = identifier;
	}
	
	@Override
	public String getFullyQualifiedName() 
	{
		return scope == null ? identifier.getRawValue() : scope.getFullyQualifiedName()+"."+identifier.getRawValue();
	}
	
	@Override
	public boolean isLocalSymbol() {
		return scope != null;
	}
	
	@Override
	public boolean isGlobalSymbol() {
		return scope == null;
	}	
	
	@Override
	public ISymbol getScope() {
		return scope;
	}
	
	@Override
	public Identifier getScopeName() {
		return scope != null ? scope.getName() : null;
	}
	
	@Override
	public final Identifier getName() {
		return identifier;
	}
	
	@Override
	public final ICompilationUnit getCompilationUnit() {
		return unit;
	}
	
    @Override
    public final ITextRegion getLocation()
    {
        return location;
    }
}