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
import java.util.Map.Entry;

import de.codesourcery.jasm16.exceptions.DuplicateSymbolException;
import de.codesourcery.jasm16.parser.Identifier;

/**
 * Default {@link ISymbolTable} implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class SymbolTable implements ISymbolTable {

	private final Map<Identifier,ISymbol> globalSymbols = new HashMap<>();

	// key is scope (=global symbol from globalSymbols map) , value
	// is map of local symbols associated with the scope 
	private final Map<Identifier,Map<Identifier,ISymbol>> localSymbols = new HashMap<>();

	private final String debugIdentifier;
	private IParentSymbolTable parent;

	public SymbolTable(String debugIdentifier) {
		this.debugIdentifier = debugIdentifier;
	}

	@Override
	public ISymbolTable createCopy() 
	{
		SymbolTable result = new SymbolTable(this.debugIdentifier);

		// copy global symbols
		for ( Map.Entry<Identifier,ISymbol> entry : globalSymbols.entrySet() ) {
			result.globalSymbols.put( entry.getKey() , entry.getValue().createCopy() );
		}

		// copy local symbols
		for ( Map.Entry<Identifier,Map<Identifier,ISymbol>> entry : localSymbols.entrySet() ) 
		{
			final Map<Identifier,ISymbol> copy = new HashMap<>();
			result.localSymbols.put( entry.getKey() , copy );

			for ( Map.Entry<Identifier,ISymbol> entry2 : entry.getValue().entrySet() ) 
			{
				copy.put( entry.getKey() , entry2.getValue().createCopy() );
			}
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

		synchronized( unit ) 
		{		
			final Identifier identifier = symbol.getIdentifier();

			if ( symbol.isLocalSymbol() ) 
			{
				// assert that global symbol exists
				final ISymbol globalSymbol = globalSymbols.get( symbol.getScope().getIdentifier() );
				
				if ( globalSymbol == null ) {
					throw new IllegalArgumentException("Cannot define local symbol "+symbol+" without defining scope "+symbol.getScope()+" first");
				}

				if ( symbol.getScope() != globalSymbol ) {
					throw new IllegalArgumentException("Local symbol needs to use the SAME global scope symbol instance contained in this ("+this+") symbol table");
				}
				
				Map<Identifier,ISymbol> locals = localSymbols.get( symbol.getScope().getIdentifier() );
				if ( locals == null ) {
					locals = new HashMap<>();
					localSymbols.put( symbol.getScope().getIdentifier() , locals );
				}

				// check for duplicate local label
				if ( locals.containsKey( symbol.getIdentifier() ) ) {
					throw new DuplicateSymbolException( locals.get( symbol.getIdentifier() ) , symbol );
				}
				locals.put( symbol.getIdentifier() , symbol );
			} 
			else 
			{
				// define global symbol
				final ISymbol existing = globalSymbols.get( identifier );
				if ( existing != null )
				{
					throw new DuplicateSymbolException( existing , symbol );
				}
				globalSymbols.put( identifier, symbol );
			}
		}
	}

	@Override
	public ISymbol renameSymbol(ISymbol symbol, Identifier newIdentifier) throws DuplicateSymbolException 
	{
		// TODO: Handle local symbols correctly
		
		final Identifier scope = symbol.getScopeIdentifier();
		final ISymbol oldSymbol = getSymbol( symbol.getIdentifier() ,scope  );
		
		if ( oldSymbol == null ) {
			throw new IllegalArgumentException("Symbol "+symbol+" is not part of this symbol table?");
		}
		final ISymbol newSymbol = oldSymbol.withIdentifier( newIdentifier );
		if ( containsSymbol( newIdentifier , scope ) ) {
			throw new DuplicateSymbolException( oldSymbol , newSymbol ); 
		}
		
		if ( symbol.isLocalSymbol() ) 
		{
			localSymbols.get( scope ).put( newIdentifier , newSymbol );
		} else {
			globalSymbols.remove( symbol.getIdentifier() );
			globalSymbols.put( newIdentifier , newSymbol );
			
			// need to update all local symbols that were attached to the old symbol
			final Map<Identifier, ISymbol> oldLocals = localSymbols.get( oldSymbol.getIdentifier() );
			if ( oldLocals != null && ! oldLocals.isEmpty() ) 
			{
				final Map<Identifier, ISymbol>  newLocals = new HashMap<>();
				for ( Entry<Identifier, ISymbol> i : oldLocals.entrySet() ) {
					newLocals.put( i.getKey() , i.getValue().withScope( newSymbol ) );
				}
				localSymbols.put( newIdentifier , newLocals );
			}
		}
		return newSymbol;
	}

	@Override
	public ISymbol getSymbol(Identifier identifier, Identifier scope) 
	{
		if ( identifier == null ) {
			throw new IllegalArgumentException("identifier must not be NULL");
		}
		
		if ( scope == null ) 
		{
			return globalSymbols.get( identifier );
		}
		
		// local symbol
		final Map<Identifier, ISymbol> result = localSymbols.get( scope );
		if ( result == null ) {
			return null;
		}		
		return result.get( identifier );
	}

	@Override
	public boolean containsSymbol(Identifier identifier, Identifier scope) {
		return getSymbol( identifier , scope ) != null;
	}	

	@Override
	public String toString() 
	{
		return "SymbolTable{"+debugIdentifier+"}";
	}

	@Override
	public void clear() {
		globalSymbols.clear();
		localSymbols.clear();
	}

	@Override
	public List<ISymbol> getSymbols() 
	{
		final List<ISymbol> result = new ArrayList<ISymbol>( globalSymbols.values() );
		for ( Entry<Identifier, Map<Identifier, ISymbol>> locals : localSymbols.entrySet() ) {
			result.addAll( locals.getValue().values() );
		}
		return result;
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
		int count = globalSymbols.size();
		for ( Entry<Identifier, Map<Identifier, ISymbol>> entry : localSymbols.entrySet() ) {
			count += entry.getValue().size();
		}
		return count;
	}
}