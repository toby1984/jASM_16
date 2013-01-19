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

import de.codesourcery.jasm16.AddressingMode;
import de.codesourcery.jasm16.OpCode;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.ast.OperandNode.OperandPosition;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.RelocationTable;
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
            ASTNode operandNode = null;
            try {
                context.mark();
                operandNode = parseOperand( opCode , i , context );
            } catch(ParseException e) {
                addCompilationErrorAndAdvanceParser( e , new TokenType[] {TokenType.COMMA} , context );
            } finally {
                context.clearMark();
            }
            
            if ( opCode.isBasicOpCode() && i == 1 ) {
                /*
                 * SET a, [--SP] is not possible because the instruction bitmask uses the same value for PUSH/POP
                 * and the meaning only depends on whether is the source or target operand                 
                 */
                if ( operandNode instanceof OperandNode ) 
                {
                    final OperandNode opNode = (OperandNode) operandNode;
                    if ( opNode.getAddressingMode() == AddressingMode.INDIRECT_REGISTER_PREDECREMENT 
                    		&& opNode.getRegister() == Register.SP ) 
                    {
                        if ( opNode.getRegisterReferenceNode().hasPreDecrement() ) { 
                            context.addCompilationError("PUSH cannot be used as SOURCE operand",opNode); 
                        }
                    }
                }            	
            } 
            else if ( opCode.isBasicOpCode() && i == 0 ) // target operand of basic instruction 
            {
                /*
                 * SET [SP++] , a  is not possible because the instruction bitmask uses the same value for PUSH/POP
                 * and the meaning only depends on whether is the source or target operand                 
                 */
                if ( operandNode instanceof OperandNode ) 
                {
                    final OperandNode opNode = (OperandNode) operandNode;
                    if ( opNode.getAddressingMode() == AddressingMode.INDIRECT_REGISTER_POSTINCREMENT 
                    		&& opNode.getRegister() == Register.SP ) 
                    {
                        if ( opNode.getRegisterReferenceNode().hasPostIncrement() ) { 
                            context.addCompilationError("POP cannot be used as TARGET operand",opNode); 
                        }
                    }
                }
            }
            
            if ( (i+1) < opCode.getOperandCount() ) {
                parseArgumentSeparator( context );
            }
        }
        return this;
    }

    protected ASTNode parseOperand(OpCode opcode, int index , IParseContext context) throws ParseException 
    {
        final OperandPosition position;
        switch( index ) {
            case 0:
                position = OperandPosition.TARGET_OPERAND;
                break;
            case 1:
                position = OperandPosition.SOURCE_OPERAND;
                break;
            default:
                throw new RuntimeException("Unreachable code reached");
        }
        
        final ASTNode node = new OperandNode().parseInternal( context );
        
        if ( node instanceof OperandNode) // parsing might've failed and thus the actual returned type may be UnparsedContentNode...
        {
            final OperandNode op = (OperandNode) node;
            if ( ! opcode.isValidAddressingMode( position , op.getAddressingMode() ) ) {
                throw new ParseException("Opcode "+opcode+" does not support addressing mode "+
                        op.getAddressingMode()+" for parameter "+(index+1) , op.getTextRegion() );
            }
            
            /*
             * getRegister() chokes on 1+ register references but 
             * this case is already flagged as an error by
             * OperandNode#parseInternal()
             */
            if ( ASTUtils.getRegisterReferenceCount( op ) <= 1 && 
            	  ! getOpCode().isOperandValidInPosition( position , op.getAddressingMode(), op.getRegister() ) ) 
            {
                throw new ParseException("Operand cannot be used as "+position,op.getTextRegion());
            }
        }
        addChild( node , context );
        return node;
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
	protected InstructionNode copySingleNode()
    {
        final InstructionNode result= new InstructionNode();
        result.opCode = opCode;
        result.setAddress( getAddress() );
        result.sizeInBytes = sizeInBytes;
        return result;
    }

    @Override
    public int getSizeInBytes(long thisNodesObjectCodeOffsetInBytes)
    {
        return sizeInBytes;
    }

    @Override
    public void symbolsResolved(ICompilationContext context)
    {
    	this.sizeInBytes = getOpCode().calculateSizeInBytes( context , this );
    }    

	@Override
    public void writeObjectCode(IObjectCodeWriter writer, ICompilationContext compContext) throws IOException
    {	
		setAddress( writer.getCurrentWriteOffset() );
		
        final byte[] objectCode = getOpCode().generateObjectCode( compContext  , this );		
        if ( objectCode == null ) {
            throw new IllegalStateException("Could not generate instruction,operand size not known yet");
        }
        
        if ( compContext.hasCompilerOption( CompilerOption.GENERATE_RELOCATION_INFORMATION ) ) 
        {
            final RelocationTable table = compContext.getCurrentCompilationUnit().getRelocationTable();
            
            // the following code assumes that operand literals have not been inlined by OpCode#generateObjectCode()
            // which is made sure because the CompilerOption#GENERATE_RELOCATION_TABLE flag is checked there as well  
            
            // instructions are written in the following order
            // word 0 - instruction
            // word 1 - source operand
            // word 2 - target operand
            final int operandCount = getOperandCount();
            if ( operandCount == 1 ) 
            {
                final Boolean referencesLabel = getOperand(0).referencesLabel( compContext.getSymbolTable() ); 
                if ( referencesLabel == Boolean.TRUE) {
                    table.addRelocationEntry( getAddress().plus( Size.ONE_WORD , false ) );
                }
            }
            else if ( operandCount == 2 ) 
            {
                Boolean referencesLabel = getOperand(0).referencesLabel( compContext.getSymbolTable() ); 
                if ( referencesLabel == Boolean.TRUE) {
                    table.addRelocationEntry( getAddress().plus( Size.ONE_WORD , false ) );
                }   
                referencesLabel = getOperand(1).referencesLabel( compContext.getSymbolTable() ); 
                if ( referencesLabel == Boolean.TRUE) {
                    table.addRelocationEntry( getAddress().plus( Size.TWO_WORDS , false ) );
                }                   
            }
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
