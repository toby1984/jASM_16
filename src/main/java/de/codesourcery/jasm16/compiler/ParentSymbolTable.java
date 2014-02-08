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
    private final IdentityHashMap<String,ISymbolTable> tablesByUnitIdentifier = new IdentityHashMap<String,ISymbolTable>();
    
    private final String debugIdentifier;
    
    public ParentSymbolTable(String debugIdentifier) 
    {
    	this.debugIdentifier = debugIdentifier;
    }
    
	@Override
	public String dumpToString() 
	{
		String result = "ParentSymbolTable ("+debugIdentifier+")\n";
		
		for ( Entry<String, ISymbolTable> t : tablesByUnitIdentifier.entrySet() ) {
			result += "\n "+t.getKey()+" => "+t.getValue().dumpToString();
		}
		return result;
	}
	
    @Override
    public String toString()
    {
        return "ParentSymbolTable( "+debugIdentifier+" ) { "+org.apache.commons.lang.StringUtils.join( tablesByUnitIdentifier.values() , " , " )+"}"; 
    }
    
	@Override
	public IParentSymbolTable createCopy() 
	{
		final ParentSymbolTable result = new ParentSymbolTable(this.debugIdentifier);
		for ( Map.Entry<String,ISymbolTable> entry : tablesByUnitIdentifier.entrySet() ) {
			result.tablesByUnitIdentifier.put( entry.getKey() , entry.getValue().createCopy() );
		}
		return result;
	}    
    
    private ISymbolTable findSymbolTable(Identifier identifier,ISymbol scope) 
    {
        for ( ISymbolTable table : tablesByUnitIdentifier.values() ) 
        {
            final ISymbol result = table.getSymbol( identifier , scope );
            if ( result != null ) {
                return table;
            }
        }
        return null;
    }
    
	@Override
	public ISymbol renameSymbol(ISymbol symbol, Identifier newIdentifier) throws DuplicateSymbolException 
	{
		final ISymbol scope = symbol.isLocalSymbol() ? null : symbol.getScope();
		
		final ISymbolTable existingTable= findSymbolTable( symbol.getName() , scope );
		
		final ISymbol existing = existingTable == null ? null : existingTable.getSymbol( symbol.getName() , scope );
		if ( existing == null ) {
			throw new IllegalArgumentException("Symbol "+symbol+" is not part of this symbol table?");
		}
		
		return existingTable.renameSymbol( existing , newIdentifier );
	}    
    
    @Override
    public List<ISymbol> getSymbols()
    {
        final List<ISymbol>  result = new ArrayList<ISymbol>();
        for ( ISymbolTable table : tablesByUnitIdentifier.values() ) {
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
            tablesByUnitIdentifier.put( unit.getIdentifier()  , table );
        }
        
        for ( ISymbolTable tmp : tablesByUnitIdentifier.values() ) 
        {
            if ( tmp.containsSymbol( symbol.getName() , symbol.getScope() ) ) 
            {
            	final ISymbol existing = tmp.getSymbol( symbol.getName() , symbol.getScope() );
                throw new DuplicateSymbolException( existing , symbol );
            }
        }
        if ( DEBUG_SYMBOLS ) {
        	System.out.println("+++ Defining symbol "+symbol+" in "+this);
        }
        table.defineSymbol( symbol );
    }

	@Override
	public ISymbol getSymbol(Identifier identifier, ISymbol scope) 
	{
		final ISymbolTable table = findSymbolTable( identifier , scope  );
    	return table == null ? null : table.getSymbol( identifier , scope );
	}

	@Override
	public boolean containsSymbol(Identifier identifier, ISymbol scope) {
		return getSymbol(identifier,scope ) != null;
	}    

    @Override
    public void clear()
    {
        for ( ISymbolTable table : tablesByUnitIdentifier.values() ) 
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
        for ( ISymbolTable table : tablesByUnitIdentifier.values() ) 
        {
            result += table.getSize();
        }            
        return result;
    }

    private ISymbolTable findSymbolTable(ICompilationUnit unit) 
    {
		final ISymbolTable symbolTable = tablesByUnitIdentifier.get( unit.getIdentifier() );
		if ( symbolTable != null ) 
		{
			return symbolTable;
		}
		for ( Iterator<Entry<String, ISymbolTable>> it = tablesByUnitIdentifier.entrySet().iterator() ; it.hasNext() ; ) 
		{
			final Entry<String, ISymbolTable> entry = it.next();
			if ( entry.getKey().equals( unit.getResource().getIdentifier() ) ) {
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