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
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Operator;

/**
 * An AST node that represents values for initialized memory (.dat /.word/.byte).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class InitializedMemoryNode extends ObjectCodeOutputNode implements IPreprocessorDirective
{
	public enum AllowedSize 
	{
		BYTE {
			@Override
			public int getMaxSupportedValue() {
				return 255;
			}            
		},
		WORD,
		PACK {
		    @Override
			public boolean use16BitCharacterLiterals() {
				return false;
			}		
			
		};
		
		public boolean use16BitCharacterLiterals() {
			return true;
		}

		public int getMaxSupportedValue() {
			return 65535;
		}
	}

	private byte[] parsedData;
	private AllowedSize allowedSize;

	public InitializedMemoryNode() {
	}

	@Override
	protected InitializedMemoryNode parseInternal(IParseContext context) throws ParseException
	{
		final ICompilationUnit unit = context.getCompilationUnit();

		IToken tok = context.peek();

		final TokenType acceptedType;
		if ( tok.hasType( TokenType.INITIALIZED_MEMORY_PACK ) ) 
		{
			acceptedType = tok.getType(); 
			allowedSize = AllowedSize.PACK;
		} 
		else if ( tok.hasType( TokenType.INITIALIZED_MEMORY_BYTE ) ) 
		{
			acceptedType = tok.getType(); 
			allowedSize = AllowedSize.BYTE;
		}
		else if ( tok.hasType( TokenType.INITIALIZED_MEMORY_WORD ) ) 
		{
			acceptedType = tok.getType();
			allowedSize = AllowedSize.WORD;
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
					if ( (token.hasType(TokenType.OPERATOR ) && Operator.fromString( token.getContents()  ) == Operator.MINUS) ||
					     token.hasType( TokenType.CHARACTERS )  || 
					     token.hasType( TokenType.NUMBER_LITERAL ) ||
						 token.hasType( TokenType.PARENS_OPEN ) ) 
					{
						try {
							context.mark();
							final ASTNode expr = new ExpressionNode().parseInternal( context );
							if ( ASTUtils.getRegisterReferenceCount( expr ) > 0 ) {
								unit.addMarker(
										new CompilationError("Expression must not refer to a register",
												unit,expr ) 
								);
							}
							if ( ! isValueInRange( expr , context.getSymbolTable() ) ) 
							{
								unit.addMarker(
										new CompilationError("Value "+getValue( expr , context.getSymbolTable() )+" is out-of-range",
												unit,expr ) 
								);            					
							}
							addChild( expr , context );
						} 
						catch(Exception e) {
							addCompilationErrorAndAdvanceParser( e , new TokenType[] {
									TokenType.COMMA , TokenType.EOL
							} , context );
						} finally {
							context.clearMark();
						}
					} 
					else if ( token.hasType( TokenType.STRING_DELIMITER ) ) 
					{
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

	private Long getValue(ASTNode node,ISymbolTable table) {
		if ( !(node instanceof TermNode) ) {
			return null;
		}

		return ((TermNode) node).calculate( table );
	}

	private boolean isValueInRange(ASTNode node,ISymbolTable table) 
	{
		final Long value = getValue( node , table );
		if ( value != null && value.longValue() > allowedSize.getMaxSupportedValue() ) {
			return false;
		}
		return true;
	}

	private byte[] internalParseData(ICompilationContext context) throws ParseException 
	{
		final ICompilationUnit unit = context.getCurrentCompilationUnit();
		final ISymbolTable symbolTable = context.getSymbolTable();

		final List<ASTNode> children = new ArrayList<ASTNode>();
		for ( ASTNode node : getChildren() ) 
		{
			if ( node instanceof TermNode ||
				 node instanceof CharacterLiteralNode )
			{
				children.add( node );
			}
		}

		final List<Integer> data = new ArrayList<Integer>();
		for ( ASTNode node : children ) 
		{
			if ( node instanceof CharacterLiteralNode ) 
			{
				final List<Integer> bytes = ((CharacterLiteralNode) node).getBytes();
				int index ;
				if ( allowedSize.use16BitCharacterLiterals() ) {
					index = 0;
				} else {
					index = 1;
				}
				
				for ( ; index < bytes.size() ; ) {
					data.add( bytes.get(index ) );
					if ( allowedSize.use16BitCharacterLiterals() ) {
						index++;
					} else {
						index += 2; // skip over color information byte
					}
				}
			} 
			else if ( node instanceof TermNode) 
			{
				final TermNode termNode = (TermNode) node;
				final Long lValue = termNode.calculate( symbolTable );
				if ( lValue == null ) {
					return null;
				}

				if ( ! isValueInRange( termNode , symbolTable ) ) 
				{
					unit.addMarker(
						new CompilationError("Value "+getValue( termNode , symbolTable )+" is out-of-range",
								unit,termNode ) 
					);  
					data.add( 0xff );
					if ( allowedSize.getMaxSupportedValue() > 255 ) {
						data.add(  0xff );        				
					}
				}
				else 
				{
					final int value = lValue.intValue();
					final boolean fromAddress = ( node instanceof SymbolReferenceNode);
					if ( ( value > 255 || fromAddress ) || allowedSize == AllowedSize.WORD ||
							allowedSize == AllowedSize.PACK ) 
					{
						data.add( (value & 0xff00) >> 8 );
						data.add(  value & 0x00ff );
					} else {
						data.add( value );
					}
				}
			} 
			else {
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
	protected InitializedMemoryNode copySingleNode()
	{
		final InitializedMemoryNode  result = new InitializedMemoryNode();
		result.allowedSize = allowedSize;
		result.setAddress( getAddress() );
		if ( parsedData != null ) {
			result.parsedData = new byte[ this.parsedData.length ];
			System.arraycopy( this.parsedData , 0 , result.parsedData , 0 , this.parsedData.length );
		}
		return result;
	}


	@Override
	public void writeObjectCode(IObjectCodeWriter writer, ICompilationContext compContext) throws IOException
	{
		setAddress( writer.getCurrentWriteOffset() );
		writer.writeObjectCode( this.parsedData );
	}

	@Override
	public void symbolsResolved(ICompilationContext context)
	{
		byte[] bytes;
		try {
			bytes = internalParseData( context );
		} 
		catch (ParseException e) {
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
	public int getSizeInBytes(long thisNodesObjectCodeOffsetInBytes)
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
