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

import java.io.IOException;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

public class OriginNode extends ObjectCodeOutputNode
{
    private Address address;
    
    @Override
    public ASTNode copySingleNode()
    {
        final OriginNode result = new OriginNode();
        result.address = this.address;
        return result;
    }

    @Override
    public boolean supportsChildNodes()
    {
        return true;
    }

    @Override
    protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
        mergeWithAllTokensTextRange( context.read( TokenType.ORIGIN ) );
        mergeWithAllTokensTextRange( context.parseWhitespace() );
        
        final NumberNode number;
        try {
            context.mark();
            number = (NumberNode) addChild( new NumberNode().parseInternal( context ) , context );
        } catch(Exception e) {
            addCompilationErrorAndAdvanceParser( e , context );
            return this;
        } finally {
            context.clearMark();
        }
        
        try {
            address = Address.valueOf( number.getValue() );
        } catch(IllegalArgumentException e) {
            context.addCompilationError( "Address value is out-of-range" , number );            
        }
        return this;
    }

    @Override
    public void symbolsResolved(ISymbolTable symbolTable)
    {
        // nothing to do here
    }

    @Override
    public int getSizeInBytes()
    {
        return address.getValue();
    }

    @Override
    public void writeObjectCode(IObjectCodeWriter writer, ICompilationContext compContext) throws IOException, ParseException
    {
        if ( this.address == null ) {
            throw new RuntimeException("Internal error, .origin node has no address ?");
        }
        
        writer.advanceToWriteOffset( this.address );
    }
    
    public Address getAddress()
    {
        return address;
    }
}
