package de.codesourcery.jasm16.compiler;

import java.util.List;

import de.codesourcery.jasm16.parser.Identifier;

public interface IParentSymbolTable extends ISymbolTable
{
    /**
     * Returns all symbols for a given identifier.
     * 
     * @param identifier
     * @return
     */
    public List<ISymbol> getSymbols(Identifier identifier);

}
