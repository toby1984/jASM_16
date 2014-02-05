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

import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.MacroNameSymbol;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * AST node representing a statement.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class StatementNode extends ASTNode
{
	private boolean parseStartOfLine(IParseContext context) throws ParseException {

		/*
		 * LINE_START := <WS> |
		 *               <LABEL> <WS>
		 */
		if ( ! context.eof() && context.peek().isWhitespace() ) {
			mergeWithAllTokensTextRegion( context.parseWhitespace() );
		}

		if ( ! context.eof() ) 
		{
			if ( context.peek( TokenType.CHARACTERS ) && Identifier.isValidIdentifier( context.peek().getContents() )) 
			{
				final Identifier id = new Identifier(context.peek().getContents());
				if ( context.getSymbolTable().getSymbol( id , null ) instanceof MacroNameSymbol ) 
				{
					int offset = context.currentParseIndex();
					context.mark();
					try {
						addChild( new InvokeMacroNode().parse( context ) , context );
					} 
					catch(Exception e) 
					{
						final ITextRegion range;
						if ( e instanceof ParseException) {
							range = ((ParseException) e).getTextRegion();
						} else {
							range = new TextRegion( offset , context.currentParseIndex()-offset );
						}
						addCompilationErrorAndAdvanceParser( new CompilationError( 
								"Failed to parse label: "+e.getMessage() ,
								context.getCompilationUnit(),
								range,e ) , new TokenType[]{TokenType.WHITESPACE,TokenType.EOL} , context );						
					} finally {
						context.clearMark();
					}
					return false;
				}
			}
			
			if ( context.peek().hasType( TokenType.COLON ) || 
					context.peek().hasType( TokenType.DOT ) || 
					context.peek().hasType( TokenType.CHARACTERS ) ) 
			{
				int offset = context.currentParseIndex();                
				try {
					context.mark();
					addChild( new LabelNode().parseInternal( context ) , context );
				} 
				catch(Exception e) 
				{
					final ITextRegion range;
					if ( e instanceof ParseException) {
						range = ((ParseException) e).getTextRegion();
					} else {
						range = new TextRegion( offset , context.currentParseIndex()-offset );
					}
					addCompilationErrorAndAdvanceParser( new CompilationError( 
							"Failed to parse label: "+e.getMessage() ,
							context.getCompilationUnit(),
							range,e ) , new TokenType[]{TokenType.WHITESPACE,TokenType.EOL} , context );
				} finally {
					context.clearMark();
				}
			}
		}

		if ( ! context.eof() && context.peek().isWhitespace() ) {
			mergeWithAllTokensTextRegion( context.parseWhitespace() );
		}    
		return ! context.eof() && ! context.peek().isEOL();
	}

	public InstructionNode getInstructionNode() 
	{
		for ( ASTNode n : getChildren() ) {
			if ( n instanceof InstructionNode) {
				return (InstructionNode) n;
			}
		}
		return null;
	}

	public boolean hasInstruction() {
		return getInstructionNode() != null;
	}

	public LabelNode getLabelNode() 
	{
		for ( ASTNode n : getChildren() ) 
		{
			LabelNode result = searchLabelNode(n);
			if ( result != null ) {
				return result;
			}
		}
		return null;
	}
	
	private LabelNode searchLabelNode(ASTNode toCheck)
	{
		if ( toCheck instanceof LabelNode) {
			return (LabelNode) toCheck;
		}
		if ( toCheck instanceof InvokeMacroNode) {
			for ( ASTNode child : toCheck.getChildren() ) {
				LabelNode result = searchLabelNode( child );
				if ( result != null ) {
					return result;
				}
			}
		}
		return null;
	}

	public List<ObjectCodeOutputNode> getObjectOutputNodes() {
		return ASTUtils.getNodesByType( this , ObjectCodeOutputNode.class , false );
	}

	private void parseEndOfLine(IParseContext context) throws ParseException {

		/*
		 * LINE_END:= <EOL> |
		 *           <WS> <EOL>
		 *           <SINGLE_LINE_COMMENT>
		 */
		if ( ! context.eof() && context.peek().hasType( TokenType.WHITESPACE ) ) 
		{
			mergeWithAllTokensTextRegion( context.read() );
		}

		if ( context.eof() ) {
			return;
		} 

		if ( context.peek().isEOL() ) {
			mergeWithAllTokensTextRegion( context.read() );
			return;
		}

		if ( context.peek().hasType( TokenType.SINGLE_LINE_COMMENT ) ) 
		{
			addChild( new CommentNode().parse( context ) , context );
		} else {
			throw new ParseException("Unexpected character '"+context.peek().getContents()+"' at end of statement", context.peek() );
		}
	}    

	@Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException
	{
		if ( parseStartOfLine( context ) && ! context.peek().hasType( TokenType.SINGLE_LINE_COMMENT ) ) 
		{
			try {
				context.mark();
				parseStatementBody( context );
			} catch(Exception e) {
				addCompilationErrorAndAdvanceParser( e , context );
			} finally {
				context.clearMark();
			}
		}
		parseEndOfLine( context );
		return this;
	}

	protected TokenType getParseRecoveryTokenType() {
		return TokenType.SINGLE_LINE_COMMENT;
	}

	private void parseStatementBody(IParseContext context) throws ParseException
	{
		final IToken tok = context.peek();

		switch( tok.getType() ) 
		{
		case START_MACRO:
			addChild( new StartMacroNode().parseInternal( context ) , context );				
			break;
		case END_MACRO:
			addChild( new EndMacroNode().parseInternal( context ) , context );				
			break;				
		case EQUATION:
			addChild( new EquationNode().parseInternal( context ) , context );
			break;
		case INITIALIZED_MEMORY_PACK:
			// $//$FALL-THROUGH$
		case INITIALIZED_MEMORY_BYTE: 
			// $FALL-THROUGH$
		case INITIALIZED_MEMORY_WORD: 
			addChild( new InitializedMemoryNode().parseInternal( context ) , context );
			break;
		case UNINITIALIZED_MEMORY_WORDS:	
		case UNINITIALIZED_MEMORY_BYTES:
			addChild( new UninitializedMemoryNode().parseInternal( context ) , context );
			break;
		case INSTRUCTION: 
			addChild( new InstructionNode().parseInternal( context ) , context );
			break;
		case INCLUDE_SOURCE:
			addChild( new IncludeSourceFileNode().parseInternal( context ) , context );
			break;				
		case INCLUDE_BINARY: 
			addChild( new IncludeBinaryFileNode().parseInternal( context ) , context );
			break;
		case ORIGIN: 
			final ASTNode origin = addChild( new OriginNode().parseInternal( context ) , context );

			if ( origin instanceof OriginNode ) 
			{
				final Address newOffset = ((OriginNode) origin).getAddress();
				final Address currentOffset = context.getCompilationUnit().getObjectCodeStartOffset();
				if ( Address.ZERO.equals( currentOffset ) ) 
				{
					context.getCompilationUnit().setObjectCodeStartOffset( newOffset );
				}
			}
			break;
		default:
			// check for macro invocation
			boolean isMacroInvocation = false;
			context.mark();				
			try 
			{
				context.skipWhitespace(false);
				if ( context.peek( TokenType.CHARACTERS ) ) 
				{
					IToken identifierToken = context.read();
					if ( Identifier.isValidIdentifier( identifierToken.getContents() ) ) 
					{
						ISymbol symbol = context.getSymbolTable().getSymbol( new Identifier( identifierToken.getContents() ) , null );
						isMacroInvocation  = ( symbol instanceof MacroNameSymbol );
					}
				}
			} finally {
				context.reset();
				context.clearMark();
			}
			if ( isMacroInvocation ) {
				addChild( new InvokeMacroNode().parseInternal( context ) , context );					
			}
			throw new ParseException( "Unexpected token '"+tok.getContents()+"' in statement, expected an instruction" , context.peek() );
		}
	}

	@Override
	protected StatementNode copySingleNode()
	{
		return new StatementNode();
	}

	@Override
	public boolean supportsChildNodes() {
		return true;
	}    
}
