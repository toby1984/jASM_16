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
import java.io.InputStream;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

/**
 * '.incbin' AST node.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class IncludeBinaryFileNode extends ObjectCodeOutputNode implements IPreprocessorDirective
{
    private static final Logger LOG = Logger.getLogger(IncludeBinaryFileNode.class);
    
    private IResource resource;
    private String resourceIdentifier;
    private int resourceSize = UNKNOWN_SIZE;
    
    @Override
	protected ASTNode copySingleNode()
    {
        final IncludeBinaryFileNode result= new IncludeBinaryFileNode();
        result.resourceIdentifier = resourceIdentifier;
        result.setAddress( getAddress() );
        result.resource = resource;
        result.resourceSize = resourceSize;
        return result;
    }

    @Override
    public boolean supportsChildNodes()
    {
        return false;
    }

    @Override
    protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
        mergeWithAllTokensTextRegion( context.read( TokenType.INCLUDE_BINARY ) );
        mergeWithAllTokensTextRegion( context.read( TokenType.WHITESPACE) );
        mergeWithAllTokensTextRegion( context.read( "Expected a filename enclosed in string delimiters but no delimiter found" , TokenType.STRING_DELIMITER) );
        
        final IToken tok = context.read( TokenType.CHARACTERS );
        mergeWithAllTokensTextRegion( tok );
        resourceIdentifier = tok.getContents();
        try {
            this.resource = context.resolveRelative( resourceIdentifier , context.getCompilationUnit().getResource() );
            final long size = resource.getAvailableBytes();
            if ( size < 0 || size > Integer.MAX_VALUE ) {
                throw new RuntimeException("Internal error, resource "+resource+" returned size that does not fit into an Integer");
            }
            this.resourceSize = (int) size;
        } catch(IOException e) {
            LOG.error("parseInternal(): Failed to look up resource '"+resourceIdentifier+"'",e);
            throw new ParseException("File \""+tok.getContents()+"\" does not exist" , tok );            
        } catch (ResourceNotFoundException e) {
            throw new ParseException("File \""+tok.getContents()+"\" does not exist" , tok );
        }
        
        mergeWithAllTokensTextRegion(context.read( "Missing string delimiter at end of filename" , TokenType.STRING_DELIMITER) );
        return this;
    }

    @Override
    public void symbolsResolved(ICompilationContext context)
    {
        // nothing to do
    }

    @Override
    public int getSizeInBytes(long thisNodesObjectCodeOffsetInBytes)
    {
        return resourceSize;
    }

    @Override
    public void writeObjectCode(IObjectCodeWriter writer, ICompilationContext compContext) throws IOException, ParseException
    {
    	setAddress( writer.getCurrentWriteOffset() );
        final InputStream inputStream = resource.createInputStream();
        try {
            byte[] buffer = new byte[1024];
            int len=0;
            while ( ( len = inputStream.read( buffer ) ) > 0 ) {
                writer.writeObjectCode( buffer , 0 , len );
            }
        } finally {
            inputStream.close();
        }
    }
}
