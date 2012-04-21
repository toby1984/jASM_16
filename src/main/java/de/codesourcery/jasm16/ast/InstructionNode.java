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
import java.util.List;

import de.codesourcery.jasm16.OpCode;
import de.codesourcery.jasm16.ast.OperandNode.OperandPosition;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;

/**
 * An AST node that represents an assembly opcode along with its operands.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class InstructionNode extends ObjectCodeOutputNode
{
    private OpCode opCode;
    private int sizeInBytes = UNKNOWN_SIZE;
    
    private TokenType[] errorRecoveryTokenTypes = DEFAULT_ERROR_RECOVERY_TOKEN;

    public InstructionNode() {
    }    
    
	public static boolean isValidOperandValue(long value) {
		return value >= 0 && value <= 0x0000FFFF;
	}    

    public OpCode getOpCode()
    {
        return opCode;
    }

    public int getOperandCount() 
    {
        int counter = 0;
        for ( ASTNode child : getChildren() ) 
        {
            if ( child instanceof OperandNode) 
            {
                counter++;
            }
        }
        return counter;
    }

    public int getOperandIndex(OperandNode node) 
    {
        int counter = 0;
        for ( ASTNode child : getChildren() ) 
        {
            if ( child instanceof OperandNode)
            {
                if ( child == node )
                {
                    return counter;
                }
                counter++;
            }
        }
        return -1;
    }

    public OperandNode getOperand(int index) {
    	return getOperand( index , true );
    }
    
    public OperandNode getOperand(int index,boolean failIfAbsent) 
    {
        int counter = 0;
        for ( ASTNode child : getChildren() ) 
        {
            if ( child instanceof OperandNode) 
            {
                if ( counter == index ) {
                    return (OperandNode) child;
                }
                counter++;
            }
        }
        if ( failIfAbsent ) {
        	throw new RuntimeException("Internal error, no operand #"+index);
        }
        return null;
    }
    
    @Override
    protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
        final IToken token = context.read( "Expected a DCPU-16 mnemonic", TokenType.INSTRUCTION );

        final OpCode opCode = OpCode.fromIdentifier( token.getContents().toUpperCase() );
        this.opCode = opCode;

        mergeWithAllTokensTextRegion( token );

        mergeWithAllTokensTextRegion( context.parseWhitespace() );
        
        errorRecoveryTokenTypes = new TokenType[] {TokenType.COMMA };
        for ( int i = 0 ; i < opCode.getOperandCount() ; i++ ) 
        {
            try {
                context.mark();
                parseOperand( opCode , i , context );
            } catch(ParseException e) {
                addCompilationErrorAndAdvanceParser( e , new TokenType[] {TokenType.COMMA} , context );
            } finally {
                context.clearMark();
            }
            if ( (i+1) < opCode.getOperandCount() ) {
                parseArgumentSeparator( context );
            }
        }
        return this;
    }

    protected void parseOperand(OpCode opcode, int index , IParseContext context) throws ParseException {

        final ASTNode node = new OperandNode().parseInternal( context );
        if ( node instanceof OperandNode) // parsing might've failed and thus the actual returned type may be UnparsedContentNode...
        {
            final OperandNode op = (OperandNode) node;
            if ( ! opcode.isValidAddressingMode( index , op.getAddressingMode() ) ) {
                throw new ParseException("Opcode "+opcode+" does not support addressing mode "+
                        op.getAddressingMode()+" for parameter "+(index+1) , op.getTextRegion() );
            }

            final OperandPosition position;
            if ( index == 0 ) {
                position = OperandPosition.TARGET_OPERAND;
            } else if ( index == 1 ) {
                position = OperandPosition.SOURCE_OPERAND;
            } else {
                throw new RuntimeException("Unreachable code reached");
            }

            if ( ! getOpCode().isOperandValidInPosition( position , op.getAddressingMode(), op.getRegister() ) ) 
            {
                throw new ParseException("Operand cannot be used as "+position,op.getTextRegion());
            }
        }
        addChild( node , context );
    }

    protected void parseArgumentSeparator(IParseContext context) throws ParseException {

        if ( ! context.eof() && context.peek().isWhitespace() ) {
            mergeWithAllTokensTextRegion( context.parseWhitespace() );
        }

        mergeWithAllTokensTextRegion( context.read(TokenType.COMMA ) );

        if ( context.peek().isWhitespace() ) 
        {
            mergeWithAllTokensTextRegion( context.parseWhitespace() );
        }
    }

    @Override
    public InstructionNode copySingleNode()
    {
        final InstructionNode result= new InstructionNode();
        result.opCode = opCode;
        result.sizeInBytes = sizeInBytes;
        return result;
    }

    @Override
    public int getSizeInBytes()
    {
        return sizeInBytes;
    }

    @Override
    public void symbolsResolved(ISymbolTable symbolTable)
    {
    	this.sizeInBytes = getOpCode().calculateSizeInBytes( symbolTable , this );
    }    

	@Override
    public void writeObjectCode(IObjectCodeWriter writer, ICompilationContext compContext) throws IOException
    {	
        final byte[] objectCode = getOpCode().generateObjectCode( compContext.getSymbolTable() , this );		
        if ( objectCode == null ) {
            throw new IllegalStateException("writeObjectCode() called on "+this+" although no object code generated?");
        }
        
        writer.writeObjectCode( objectCode ); 
    }
	
    public List<OperandNode> getOperands() 
    {
        return ASTUtils.getNodesByType( this , OperandNode.class , true );
    }

    @Override
    public boolean supportsChildNodes() {
        return true;
    }
    
    protected TokenType[] getParseRecoveryTokenTypes() {
        return errorRecoveryTokenTypes;
    }    
}
