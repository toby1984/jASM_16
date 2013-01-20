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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.codesourcery.jasm16.exceptions.DuplicateSymbolException;
import de.codesourcery.jasm16.parser.Identifier;

/**
 * Default {@link ISymbolTable} implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class SymbolTable implements ISymbolTable {

	private final Map<Identifier,ISymbol> symbols = new HashMap<Identifier,ISymbol>();

	private IParentSymbolTable parent;
	
	public SymbolTable() {
	}
	
	@Override
	public ISymbolTable createCopy() 
	{
		SymbolTable result = new SymbolTable();
		for ( Map.Entry<Identifier,ISymbol> entry : symbols.entrySet() ) {
			result.symbols.put( entry.getKey() , entry.getValue().createCopy() );
		}
		return result;
	}
	
	@Override
	public void defineSymbol( ISymbol symbol) throws DuplicateSymbolException
	{
		if (symbol == null) {
			throw new IllegalArgumentException("symbol must not be NULL");
		}

		final ICompilationUnit unit = symbol.getCompilationUnit();
		final Identifier identifier = symbol.getIdentifier();

		synchronized( unit ) 
		{
			final ISymbol existing = symbols.get( identifier );
			if ( existing != null )
			{
				throw new DuplicateSymbolException( existing , symbol );
			}
			symbols.put( identifier, symbol );
		}
	}
	
	@Override
	public ISymbol renameSymbol(ISymbol symbol, Identifier newIdentifier) throws DuplicateSymbolException 
	{
		final ISymbol existing = getSymbol( symbol.getIdentifier() );
		if ( existing == null ) {
			throw new IllegalArgumentException("Symbol "+symbol+" is not part of this symbol table?");
		}
		final ISymbol newSymbol = existing.withIdentifier( newIdentifier );
		if ( containsSymbol( newIdentifier) ) {
			throw new DuplicateSymbolException( existing , newSymbol ); 
		}
		symbols.remove( symbol.getIdentifier() );
		symbols.put( newIdentifier , newSymbol );
		return newSymbol;
	}

	@Override
	public ISymbol getSymbol(Identifier identifier) 
	{
		if ( identifier == null ) {
			throw new IllegalArgumentException("identifier must not be NULL");
		}
		ISymbol result = symbols.get( identifier );
		if ( result == null ) {
			return null;
		}
		return result;
	}

	@Override
	public boolean containsSymbol(Identifier identifier)
	{
		return getSymbol( identifier ) != null;
	}

	@Override
	public String toString() 
	{
		StringBuilder result = new StringBuilder();
		for ( Identifier key : symbols.keySet() ) 
		{
			result.append("      "+key+" => "+symbols.get( key ) ).append("\n");
		}
		return result.toString();
	}

	@Override
	public void clear() {
		symbols.clear();
	}

	@Override
	public List<ISymbol> getSymbols() {
		return new ArrayList<ISymbol>( symbols.values() );
	}

    @Override
    public IParentSymbolTable getParent()
    {
        return parent;
    }

    @Override
    public void setParent(IParentSymbolTable table)
    {
        this.parent = table;
    }

    @Override
    public int getSize()
    {
        return symbols.size();
    }
}