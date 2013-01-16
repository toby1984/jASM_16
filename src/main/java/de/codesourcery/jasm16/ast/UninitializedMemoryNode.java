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
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

/**
 * AST node for the '.bss' instruction (uninitialized memory block of size X).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class UninitializedMemoryNode extends ObjectCodeOutputNode implements IPreprocessorDirective
{
	private int sizeInBytes;
	
	public int getSizeInBytes(long thisNodesObjectCodeOffsetInBytes)
	{
		return Address.alignTo16Bit( sizeInBytes );
	}
	
	private void setSizeInBytes(long size) 
	{
		if ( size < 0 ) {
			throw new IllegalArgumentException("Invalid size "+size+" of uninitialized memory");
		}
		if ( size > 65535 ) {
			throw new IllegalArgumentException("Invalid size "+size+" of uninitialized memory");
		}
		this.sizeInBytes = (int) size;
	}

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException
	{
	    final int factor;
	    if ( context.peek().hasType( TokenType.UNINITIALIZED_MEMORY_BYTES ) ) {
	        factor = 1;
	        mergeWithAllTokensTextRegion( context.read(TokenType.UNINITIALIZED_MEMORY_BYTES ) );
	    } else {
	        factor = 2;
	        mergeWithAllTokensTextRegion( context.read(TokenType.UNINITIALIZED_MEMORY_WORDS) );
	    }
		mergeWithAllTokensTextRegion( context.parseWhitespace() );
		final NumberNode node = (NumberNode) new NumberNode().parse( context );
		mergeWithAllTokensTextRegion( node );
		setSizeInBytes( node.getAsWord()*factor );
		return this;           	    
	}

    @Override
	protected UninitializedMemoryNode copySingleNode()
    {
        final UninitializedMemoryNode result = new UninitializedMemoryNode();
        result.sizeInBytes = sizeInBytes;
        result.setAddress( getAddress() );
        return result;
    }
    
    @Override
    public void writeObjectCode(IObjectCodeWriter writer, ICompilationContext compContext) throws IOException
    {
        setAddress( writer.getCurrentWriteOffset() );
        final int bytesToWrite = Address.alignTo16Bit( sizeInBytes );
        final byte[] data = new byte[ bytesToWrite ];
        writer.writeObjectCode( data );
    }

    @Override
    public void symbolsResolved(ICompilationContext context)
    {
        // nothing to see here...
    }
    
    @Override
    public boolean supportsChildNodes() {
        return false;
    }    
}