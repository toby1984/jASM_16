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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.codesourcery.jasm16.exceptions.DuplicateSymbolException;
import de.codesourcery.jasm16.parser.Identifier;

public class ParentSymbolTable implements IParentSymbolTable
{
    private final IdentityHashMap<ICompilationUnit,ISymbolTable> tables = new IdentityHashMap<ICompilationUnit,ISymbolTable>();
    
    public ParentSymbolTable() {
    }
    
    @Override
    public ISymbol getSymbol(Identifier identifier)
    {
    	final ISymbolTable table = findSymbolTable( identifier );
    	return table == null ? null : table.getSymbol( identifier );
    }
    
	@Override
	public IParentSymbolTable createCopy() 
	{
		final ParentSymbolTable result = new ParentSymbolTable();
		for ( Map.Entry<ICompilationUnit,ISymbolTable> entry : tables.entrySet() ) {
			result.tables.put( entry.getKey() , entry.getValue().createCopy() );
		}
		return result;
	}    
    
    private ISymbolTable findSymbolTable(Identifier identifier) {
        for ( ISymbolTable table : tables.values() ) 
        {
            final ISymbol result = table.getSymbol( identifier );
            if ( result != null ) {
                return table;
            }
        }
        return null;
    }
    
    @Override
    public List<ISymbol> getSymbols(Identifier identifier)
    {
        final List<ISymbol>  result = new ArrayList<ISymbol>();
        for ( ISymbolTable table : tables.values() ) 
        {
            ISymbol s = table.getSymbol( identifier );
            if ( s != null ) {
                result.add( s );
            }
        }        
        return result;
    }    
    
	@Override
	public ISymbol renameSymbol(ISymbol symbol, Identifier newIdentifier) throws DuplicateSymbolException 
	{
		final ISymbolTable existingTable = findSymbolTable( symbol.getIdentifier() );
		
		final ISymbol existing = existingTable == null ? null : existingTable.getSymbol( symbol.getIdentifier() );
		if ( existing == null ) {
			throw new IllegalArgumentException("Symbol "+symbol+" is not part of this symbol table?");
		}
		
		return existingTable.renameSymbol( existing , newIdentifier );
	}    
    
    @Override
    public List<ISymbol> getSymbols()
    {
        final List<ISymbol>  result = new ArrayList<ISymbol>();
        for ( ISymbolTable table : tables.values() ) {
            result.addAll( table.getSymbols() );
        }        
        return result;
    }

    @Override
    public void defineSymbol(ISymbol symbol) throws DuplicateSymbolException
    {
        final ICompilationUnit unit = symbol.getCompilationUnit();
        ISymbolTable table = findSymbolTable( unit );
        if ( table == null ) {
            table = unit.getSymbolTable();
            table.setParent( this );
            tables.put( unit  , table );
        }
        
        for ( ISymbolTable tmp : tables.values() ) 
        {
            if ( tmp.containsSymbol( symbol.getIdentifier() ) ) {
                throw new DuplicateSymbolException( tmp.getSymbol( symbol.getIdentifier() ) , symbol );
            }
        }
        table.defineSymbol( symbol );
    }

    @Override
    public boolean containsSymbol(Identifier identifier)
    {
        for ( ISymbolTable table : tables.values() ) 
        {
            if ( table.containsSymbol( identifier ) ) {
                return true;
            }
        }        
        return false;
    }

    @Override
    public void clear()
    {
        for ( ISymbolTable table : tables.values() ) 
        {
            table.clear();
        }              
    }
    
    @Override
    public IParentSymbolTable getParent()
    {
        return null;
    }
    
    @Override
    public void setParent(IParentSymbolTable table)
    {
        throw new UnsupportedOperationException("Parent symbol tables cannot have other parents");
    }

    @Override
    public int getSize()
    {
        int result = 0;
        for ( ISymbolTable table : tables.values() ) 
        {
            result += table.getSize();
        }            
        return result;
    }

    private ISymbolTable findSymbolTable(ICompilationUnit unit) 
    {
		final ISymbolTable symbolTable = tables.get( unit );
		if ( symbolTable != null ) 
		{
			return symbolTable;
		}
		for ( Iterator<Map.Entry<ICompilationUnit,ISymbolTable>> it = tables.entrySet().iterator() ; it.hasNext() ; ) 
		{
			final Entry<ICompilationUnit, ISymbolTable> entry = it.next();
			if ( entry.getKey().getResource().getIdentifier().equals( unit.getResource().getIdentifier() ) ) {
				return entry.getValue();
			}
		}
		return null;
    }
	@Override
	public void clear(ICompilationUnit unit) 
	{
		final ISymbolTable symbolTable = findSymbolTable( unit );
		if ( symbolTable != null ) {
			symbolTable.clear();
		}
	}
}