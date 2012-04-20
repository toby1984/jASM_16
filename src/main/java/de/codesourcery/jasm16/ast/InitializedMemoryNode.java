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
import java.util.ArrayList;
import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

/**
 * An AST node that represents values for initialized memory (.dat /.word/.byte).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class InitializedMemoryNode extends ObjectCodeOutputNode
{
    public enum AllowedSize 
    {
        BYTE {
            public boolean supportsCharacterLiterals() {
                return false;
            }
            public int getMaxSupportedValue() {
                return 255;
            }            
        },
        WORD,
        BYTE_OR_WORD;
        
        public boolean supportsCharacterLiterals() {
            return true;
        }
        
        public int getMaxSupportedValue() {
            return 65535;
        }
    }
    
    private transient byte[] parsedData;
    private AllowedSize allowedSize;

    public InitializedMemoryNode() {
    }
    
    @Override
	protected InitializedMemoryNode parseInternal(IParseContext context) throws ParseException
    {
        IToken tok = context.peek();
        
        final TokenType acceptedType;
        if ( tok.hasType( TokenType.INITIALIZED_MEMORY_BYTE ) ) 
        {
            acceptedType = tok.getType(); 
            allowedSize = AllowedSize.BYTE;
        }
        else if ( tok.hasType( TokenType.INITIALIZED_MEMORY_WORD ) ) 
        {
            acceptedType = tok.getType();
            allowedSize = AllowedSize.WORD;
//        }
//        else if ( tok.hasType( TokenType.INITIALIZED_MEMORY ) ) 
//        {
//            acceptedType = tok.getType();
//            allowedSize = AllowedSize.BYTE_OR_WORD;
        }
        else {
            throw new ParseException("Unexpected token type "+tok.getType(), tok );
        }

        mergeWithAllTokensTextRegion( context.read(acceptedType) );
        
        mergeWithAllTokensTextRegion( context.parseWhitespace() );
        
        boolean expectingData = true;
outer:        
        while ( ! context.eof() ) 
        {
            IToken token = context.peek();
            if ( token.isWhitespace() ) 
            {
            	mergeWithAllTokensTextRegion( context.parseWhitespace() );
                continue;
            }
            
            if ( expectingData ) 
            {
            	if ( token.hasType( TokenType.CHARACTERS ) ) 
            	{
            		addChild( new LabelReferenceNode().parse( context ) , context );
            	} 
            	else if ( token.hasType( TokenType.NUMBER_LITERAL ) ) 
            	{
            		ASTNode number = new NumberNode().parse( context );
            		if ( number instanceof NumberNode) {
            		    final int value = ((NumberNode) number).getAsWord();
            		    if (  value < 0 || value > allowedSize.getMaxSupportedValue() ) {
                            throw new ParseException("Number literal "+value+" is out of range, must be >= 0 and <= "+allowedSize.getMaxSupportedValue(), 
                                    number.getTextRegion() );
            		    }
            		}
                    addChild( number , context );
            	} 
            	else if ( token.hasType( TokenType.STRING_DELIMITER ) ) 
            	{
            	    if ( ! allowedSize.supportsCharacterLiterals() ) {
            	        throw new ParseException("Characters are 16 bit each and thus not allowed here", token );
            	    }
            		addChild( new CharacterLiteralNode().parse( context ) , context );
            	} else {
            		throw new ParseException("Expected a number or character literal, got "+token.getType(),token);
            	}
            	expectingData = false;
            }
            
            if ( context.eof() || context.peek().isEOL() ) {
            	break outer;
            }
            
            while ( ! context.eof() ) 
            {
            	token = context.peek();
            	if ( token.isWhitespace() ) {
            		mergeWithAllTokensTextRegion( context.parseWhitespace() );
            	} else if ( token.hasType( TokenType.COMMA ) ) {
            		mergeWithAllTokensTextRegion( context.read( TokenType.COMMA ) );
            		expectingData = true;
            		continue outer;
            	} else {
            		break outer;
            	}
            }
        }
        
        if ( expectingData ) {
        	throw new ParseException("Expected number or character literal",context.currentParseIndex() , 0 );
        }
        
        return this;   
    }
    
    private byte[] internalParseData(ISymbolTable symbolTable) throws ParseException 
    {
    	final List<ASTNode> children = new ArrayList<ASTNode>();
    	for ( ASTNode node : getChildren() ) 
    	{
    		if ( node instanceof NumberNode ||
    			 node instanceof CharacterLiteralNode ||
    			 node instanceof LabelReferenceNode ) 
    		{
    			children.add( node );
    		}
    	}
    	
        final List<Integer> data = new ArrayList<Integer>();
        for ( ASTNode node : children ) 
        {
        	if ( node instanceof LabelReferenceNode || node instanceof NumberNode) 
        	{
        		final boolean fromAddress;
        		final int value;
        		if ( node instanceof LabelReferenceNode ) 
        		{
        			final Long lValue = ((LabelReferenceNode) node).getNumericValue( symbolTable );
        			if ( lValue == null ) {
        				return null;
        			}
        			fromAddress = true;
        			value = lValue.intValue();
        		} else {
        			fromAddress = false;
                    value = ((NumberNode) node).getAsWord();
        		}
        		
                if ( ( value > 255 || fromAddress ) || allowedSize == AllowedSize.WORD ) 
                {
                    data.add( (value & 0xff00) >> 8 );
                    data.add(  value & 0x00ff );
                } else {
                    data.add( value );
                }
            } 
            else if ( node instanceof CharacterLiteralNode ) 
            {
                final List<Integer> bytes = ((CharacterLiteralNode) node).getBytes();
                for ( int value : bytes ) 
                {
                    data.add( value );
                }
            } else {
                throw new RuntimeException("Unreachable code reached");
            }
        }    	
        
        final int aligned = Address.alignTo16Bit( data.size() );
        final byte[] result = new byte[ aligned ];
    	for ( int i = 0 ; i < data.size() ; i++ ) 
    	{
    		result[i] = (byte) data.get(i).intValue();
    	}
        return result;
    }
    
    @Override
    public InitializedMemoryNode copySingleNode()
    {
        final InitializedMemoryNode  result = new InitializedMemoryNode();
        result.allowedSize = allowedSize;
        return result;
    }


    @Override
    public void writeObjectCode(IObjectCodeWriter writer, ICompilationContext compContext) throws IOException
    {
        writer.writeObjectCode( this.parsedData );
    }

    @Override
    public void symbolsResolved(ISymbolTable symbolTable)
    {
        byte[] bytes;
        try {
            bytes = internalParseData( symbolTable );
        } catch (ParseException e) {
            throw new RuntimeException("Internal error, caught unexpected exception",e);
        }
        if ( bytes != null ) {
            final int bytesToWrite = Address.alignTo16Bit( bytes.length );
            final byte[] data = new byte[ bytesToWrite  ];
            System.arraycopy( bytes , 0 , data , 0 , bytes.length );
            this.parsedData = data;
        } else {
            this.parsedData = null;
        }
    }
    
    /** 
     * UNIT-TESTING ONLY.
     * 
     * @return
     */
    public byte[] getBytes() {
        return parsedData;
    }

    @Override
    public int getSizeInBytes()
    {
        if ( this.parsedData == null ) {
            return UNKNOWN_SIZE;
        }
        return parsedData.length;
    }

    @Override
    public boolean supportsChildNodes() {
        return true;
    }    
}
