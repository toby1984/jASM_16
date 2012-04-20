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

import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * Abstract base-class for AST nodes that may evaluate to
 * a constant value.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class ConstantValueNode extends TermNode
{
    public ConstantValueNode() {
    }
    
    public ConstantValueNode(ITextRegion range) {
        super( range );
    }
    
    public abstract Long getNumericValue(ISymbolTable symbolTable);
    
    public final NumberNode toNumberNode(ICompilationContext context,ITextRegion range) throws ParseException
    {
        final Long value = getNumericValue( context.getSymbolTable() );
        if ( value != null ) 
        {
            return new NumberNode( value , range );
        }
        return null;
    }
}
