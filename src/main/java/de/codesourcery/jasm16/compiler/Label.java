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

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * A label that identifies a specific location in the
 * generated object code.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Label extends AbstractSymbol implements IValueSymbol {

	private Address address;
	
	public Label(ICompilationUnit unit , ITextRegion location , Identifier identifier) {
		super( unit , location , identifier );
	}
	
	/**
	 * Returns the address associated with this label.
	 * 
	 * @return address or <code>null</code> if this label's address
	 * has not been resolved (yet).
	 */
	public Address getAddress()
    {
        return address;
    }
	
	/**
	 * Sets the address of this label.
	 * 
	 * @param address
	 */
	public void setAddress(Address address)
    {
        this.address = address;
    }
	
	
	public String toString() 
	{
	    if ( address != null ) {
	        return getIdentifier()+"("+address+" , "+getCompilationUnit()+")";	        
	    }
		return getIdentifier().toString();
	}

	
	public Long getValue(ISymbolTable symbolTable) 
	{
		if ( this.address == null ) {
			return null;
		}
		return Long.valueOf( this.address.getValue() );
	}

	
	public void setValue(Long value) {
		if ( value == null ) {
			this.address = null;
		} else {
			this.address = Address.wordAddress( value );
		}
	}
	
}
