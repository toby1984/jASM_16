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
package de.codesourcery.jasm16.ast;

import org.apache.commons.lang.ObjectUtils;

import de.codesourcery.jasm16.compiler.Equation;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.IValueSymbol;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.SymbolTable;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * An AST node that represents a references to a symbol.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see Label
 * @see ISymbol
 * @see Equation
 */
public class SymbolReferenceNode extends ConstantValueNode
{
	private Identifier identifier;

	public Identifier getIdentifier()
	{
		return identifier;
	}

	@Override
	protected SymbolReferenceNode parseInternal(IParseContext context) throws ParseException
	{
		final int startOffset = context.currentParseIndex();
		this.identifier = context.parseIdentifier( null , context.hasParserOption(ParserOption.LOCAL_LABELS_SUPPORTED ) );
		mergeWithAllTokensTextRegion( new TextRegion( startOffset , identifier.getRawValue().length() ) );
		return this;
	}

	public void setIdentifier(Identifier identifier) {
		this.identifier = identifier;
	}
	
    @Override
    public boolean equals(Object obj)
    {
        if ( obj == this ) {
            return true;
        }
        if ( obj instanceof SymbolReferenceNode) {
            return ObjectUtils.equals( this.identifier , ((SymbolReferenceNode) obj).identifier );
        }
        return false; 
    }
    
	@Override
	public SymbolReferenceNode reduce(ICompilationContext context) {
		return (SymbolReferenceNode) createCopy(false);
	}

	@Override
	protected SymbolReferenceNode copySingleNode()
	{
		final SymbolReferenceNode result = new SymbolReferenceNode();
		result.identifier = identifier;
		return result;
	}

	@Override
	public Long getNumericValue(ISymbolTable table)
	{
		ISymbol symbol = resolve(table);
		
		if ( symbol == null ) {
		    return null;
		}
		if ( !( symbol instanceof IValueSymbol ) ) {
			throw new RuntimeException("Internal error, symbol reference does not refer to a value symbol but to "+symbol);        	
		}
		return ((IValueSymbol) symbol).getValue( table );
	}
	
	public ISymbol resolve(ISymbolTable table) {
		return resolve(table,false);
	}
	
	public ISymbol resolve(ISymbolTable table,boolean searchParentTables) 
	{
		ISymbolTable currentTable = table;
		do {
			ISymbol symbol = internalResolve(currentTable);
			if ( symbol != null ) {
				if ( SymbolTable.DEBUG_SYMBOLS ) {
					System.out.println("RESOLVED => Symbol '"+getIdentifier()+"' => "+symbol.getFullyQualifiedName());
				}
				return symbol;
			}
			if ( SymbolTable.DEBUG_SYMBOLS ) {
				System.out.println("!!! Symbol '"+getIdentifier()+"' not found in "+currentTable);				
			}
			currentTable = currentTable.getParent();
		} while( currentTable != null && searchParentTables );
		if ( SymbolTable.DEBUG_SYMBOLS ) {
			System.out.println("Failed to resolve symbol '"+getIdentifier()+"'");
		}
		return null;
	}
	
	private ISymbol internalResolve(ISymbolTable table) {
		
		ISymbol symbol = null;
		
		// try to resolve as local reference first
		LabelNode labelNode = getPreviousGlobalLabel();
		if ( labelNode != null ) 
		{
			symbol = table.getSymbol(identifier, labelNode.getLabel() );
		}
		
		if ( symbol == null ) { // try to resolve as global label
			symbol = table.getSymbol( this.identifier , null );
		}
		return symbol;
	}
	
	@Override
	public String toString() 
	{
		return identifier != null ? identifier.toString() : "<null identifier?>";
	}
	
    @Override
    public boolean supportsChildNodes() {
        return false;
    }

	@Override
	public Long calculate(ISymbolTable symbolTable) 
	{
		return getNumericValue( symbolTable );
	}	
}