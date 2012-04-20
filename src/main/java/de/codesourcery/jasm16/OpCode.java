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
package de.codesourcery.jasm16;

import java.util.HashMap;
import java.util.Map;

import de.codesourcery.jasm16.ast.ConstantValueNode;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.ast.OperandNode;
import de.codesourcery.jasm16.ast.OperandNode.OperandPosition;
import de.codesourcery.jasm16.compiler.ISymbolTable;

/**
 * Enumeration of all DCPU-16 instructions / op codes.
 * 
 * <p>This class also implements object code generation
 * for each of the instructions.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public enum OpCode 
{
    /*
     * Basic:
     * 
     * 0x1: SET a, b - sets a to b

     * 0x2: ADD a, b - sets a to a+b, sets O to 0x0001 if there's an overflow, 0x0 otherwise
     * 0x3: SUB a, b - sets a to a-b, sets O to 0xffff if there's an underflow, 0x0 otherwise
     * 0x4: MUL a, b - sets a to a*b, sets O to ((a*b)>>16)&0xffff
     * 0x5: DIV a, b - sets a to a/b, sets O to ((a<<16)/b)&0xffff. if b==0, sets a and O to 0 instead.
     * 0x6: MOD a, b - sets a to a%b. if b==0, sets a to 0 instead.
     * 
     * 0x7: SHL a, b - sets a to a<<b, sets O to ((a<<b)>>16)&0xffff
     * 0x8: SHR a, b - sets a to a>>b, sets O to ((a<<16)>>b)&0xffff
     * 
     * 0x9: AND a, b - sets a to a&b
     * 0xa: BOR a, b - sets a to a|b
     * 0xb: XOR a, b - sets a to a^b
     * 
     * 0xc: IFE a, b - performs next instruction only if a==b
     * 0xd: IFN a, b - performs next instruction only if a!=b
     * 0xe: IFG a, b - performs next instruction only if a>b
     * 0xf: IFB a, b - performs next instruction only if (a&b)!=0	
     * 
     * extended:
     * 
     * JSR
     */
    // general
    SET("set",2,0x01) {
        @Override
        public boolean isValidAddressingMode(int operandIndex , AddressingMode type) 
        {
            if ( operandIndex == 0 && type.equals( AddressingMode.IMMEDIATE ) ) {
                return false;
            }
            return true;
        }
    },
    // arithmetics
    ADD("add",2,0x02),
    SUB("sub",2,0x03),
    MUL("mul",2,0x04),
    DIV("div",2,0x05),
    MOD("mod",2,0x06),	

    // control flow
    JSR( "jsr",1,0x01) {
        protected boolean isBasicOpCode() {
            return false;
        };

        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // operand no. 0 is always considered to TARGET operand
            // while operand no. 1 is always considered the SOURCE operand.

            // This breaks stuff in case of JSR which only takes a single operand
            // that needs to be treated as SOURCE (because it needs to provide the jump target)
            // Thus we'll just hard-code the operand position to always be SOURCE 
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },
    // conditions
    IFE("ife",2,0x0c) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    } ,
    IFN("ifn",2,0x0d) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },	
    IFG("ifg",2,0x0e) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },
    IFB("ifb",2,0x0f) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },
    // bit-shifting
    SHL("shl",2,0x07),
    SHR("shr",2,0x08),	
    // boolean operations
    AND("and",2,0x09),
    OR("bor",2 ,0x0a),	
    XOR("xor",2,0x0b);

    private final int opCodeBits;
    private final String identifier;
    private final int operandCount;

    private static volatile Map<String,OpCode> LOWER_CASE_MAP;
    private static volatile Map<String,OpCode> UPPER_CASE_MAP;

    private OpCode(String identifier,int operandCount,int opCodeBits) {
        this.identifier = identifier;
        this.operandCount=operandCount;
        this.opCodeBits = opCodeBits;
    }

    protected static final class OperandDesc 
    {
        private final int operandBits;
        private final boolean appendAsWord;

        protected OperandDesc(int operandBits) {
            this( operandBits, false);
        }
        
        protected OperandDesc(int operandBits,boolean appendAsWord) {
            this.operandBits=operandBits;
            this.appendAsWord=appendAsWord;
        }	    
    }

    private OperandDesc operandDesc(int operandBits) {
        return new OperandDesc(operandBits);
    }
    
    private OperandDesc operandDesc(int operandBits,boolean appendAsWord) {
        return new OperandDesc(operandBits,appendAsWord);
    }	

    public int getOperandCount()
    {
        return operandCount;
    }

    public String getIdentifier() {
        return identifier.toUpperCase();
    }

    public int getBaseSizeInBytes() {
        return 2;
    }

    public boolean isValidAddressingMode(int operandIndex , AddressingMode type) {
        return true;
    }

    private static Map<String,OpCode> getLowerCaseMap() {
        if ( LOWER_CASE_MAP == null ) {
            LOWER_CASE_MAP = new HashMap<String,OpCode>();
            for ( OpCode code : values() ) {
                LOWER_CASE_MAP.put( code.identifier.toLowerCase() , code );
            }
        }
        return LOWER_CASE_MAP;
    }

    protected boolean isBasicOpCode() {
        return true;
    }

    /**
     * Non-basic opcodes always have their lower four bits unset, have one value and a six bit opcode.
     * 
     * In binary, they have the format: aaaaaaoooooo0000
     * The value (a) is in the same six bit format as defined earlier.
     * @return
     */
    private final boolean isExtendedOpCode() {
        return ! isBasicOpCode();
    }    

    private static Map<String,OpCode> getUpperCaseMap() {
        if ( UPPER_CASE_MAP == null ) {
            UPPER_CASE_MAP = new HashMap<String,OpCode>();
            for ( OpCode code : values() ) {
                UPPER_CASE_MAP.put( code.identifier.toUpperCase() , code );
            }
        }
        return UPPER_CASE_MAP;
    }	

    public static OpCode fromIdentifier(String identifier) 
    { 
        OpCode result = getLowerCaseMap().get( identifier );
        if ( result == null ) {
            result = getUpperCaseMap().get( identifier );
        }
        return result;
    }

    public int calculateSizeInBytes(ISymbolTable symbolTable , InstructionNode instruction)
    {
        final byte[] buffer = new byte[6];
        return writeInstruction( symbolTable , instruction.getOperand(  0, false ) , instruction.getOperand(  1, false ) , buffer );
    }	

    public byte[] generateObjectCode(ISymbolTable symbolTable , InstructionNode instruction) 
    {
        final byte[] buffer = new byte[6]; // max. instruction length: three words (3*2 bytes)
        final OperandNode operandA;
        if ( instruction.getOperandCount() > 0 ) {
            operandA = instruction.getOperand( 0 );
        } else {
            operandA = null;
        }

        final OperandNode operandB;
        if ( instruction.getOperandCount() > 1 ) {
            operandB = instruction.getOperand( 1 );
        } else {
            operandB = null;
        }        

        final int bytesToWrite = writeInstruction( symbolTable , operandA, operandB , buffer );
        if ( bytesToWrite != ObjectCodeOutputNode.UNKNOWN_SIZE ) 
        {
            final byte[] result = new byte[ bytesToWrite ];
            System.arraycopy( buffer , 0 , result , 0 , bytesToWrite );
            return result;
        }
        return null;
    }

    private int writeInstruction(ISymbolTable symbolTable,OperandNode a , OperandNode b, byte[] buffer) {

        /*
         * BASIC instructions are 1-3 words long and are fully defined by the first word.
         * 
         * In a basic instruction, the lower four bits of the first word of the instruction are the opcode,
         * and the remaining twelve bits are split into two six bit values, called a and b.
         * 
         * a is always handled by the processor before b, and is the lower six bits.
         * 
         * In bits (with the least significant being last), a basic instruction has the format: 
         * 
         * bbbbbbaaaaaaoooo
         * 
         * NON-BASIC instructions always have their lower four bits unset, have one value and a six bit opcode.
         * 
         * In binary, they have the format: aaaaaaoooooo0000
         * The value (a) is in the same six bit format as defined earlier.
         *       
         */
        int opcode = 0;
        OperandDesc descA=null;
        OperandDesc descB=null;

        if ( isExtendedOpCode() ) 
        {
            if ( a == null ) {
                throw new RuntimeException("Extended instruction "+this+" requires a single operand, got none");
            }
            if ( b != null ) {
                throw new RuntimeException("Extended instruction "+this+" requires a single operand, got two ?");
            }            
            opcode |= ( opCodeBits << 4 );
            descA = getOperandBits( symbolTable , 0 , a ,b );
            if ( descA == null ) {
                return ObjectCodeOutputNode.UNKNOWN_SIZE;
            }
            opcode |= ( descA.operandBits << 10 );
        } 
        else 
        {
            // handle basic opcode
            opcode |= ( opCodeBits & 0xf);
            if ( a != null ) 
            {
                descA = getOperandBits( symbolTable , 0 , a ,b );
                if ( descA == null ) {
                    return ObjectCodeOutputNode.UNKNOWN_SIZE;
                }
                opcode |= ( descA.operandBits << 4 );
            }
            if ( b != null ) {
                descB = getOperandBits( symbolTable , 1 , a ,b );
                if ( descB == null ) {
                    return ObjectCodeOutputNode.UNKNOWN_SIZE;
                }
                opcode |= ( descB.operandBits << 10 );
            }
        }

        // write instruction word 
        int idx = 0;
        buffer[idx++] = (byte) ( ( opcode >> 8 ) & 0xff  );            
        buffer[idx++] = (byte) ( opcode & 0xff );

        // write operand A
        if ( descA != null && descA.appendAsWord ) 
        {
            ConstantValueNode valueNode = a.getLiteralValueNode();
			Long literalValue = valueNode != null ? valueNode.getNumericValue( symbolTable ) : null;
            if ( literalValue == null ) {
                return ObjectCodeOutputNode.UNKNOWN_SIZE;
            }
            final long value = literalValue;
            buffer[idx++] = (byte) ( ( value  >> 8 ) & 0xff );      
            buffer[idx++] = (byte) ( value & 0xff );            
        }

        // write operand B
        if ( descB != null && descB.appendAsWord ) 
        {
            ConstantValueNode valueNode = b.getLiteralValueNode();
			Long literalValue = valueNode != null ? valueNode.getNumericValue( symbolTable ) : null;
            if ( literalValue == null ) {
                return ObjectCodeOutputNode.UNKNOWN_SIZE;
            }            
            final long  value = literalValue;
            buffer[idx++] = (byte) ( ( value >> 8 ) & 0xff );             
            buffer[idx++] = (byte) ( value & 0x00ff );            
        }       
        return idx;
    }
    
    private OperandDesc getOperandBits(ISymbolTable symbolTable,int operandIndex,OperandNode a,OperandNode b) 
    {
    	final OperandNode node=operandIndex == 0 ? a : b;
    	
        /*
         * Values: (6 bits)
         * 
         * OK 0x00-0x07: register (A, B, C, X, Y, Z, I or J, in that order)      
         * OK 0x08-0x0f: [register]                                              
         * OK 0x10-0x17: [next word + register]                                  
         * OK      0x18: POP / [SP++]                                            
         * OK      0x19: PEEK / [SP]                                             
         * OK      0x1a: PUSH / [--SP]                                           
         * OK      0x1b: SP                                                      
         * OK      0x1c: PC                                                      
         * OK      0x1d: O                                                       
         * OK      0x1e: [next word]                                             
         * OK      0x1f: next word (literal)                                     
         * OK      0x20-0x3f: literal value 0x00-0x1f (literal)                  
         *     
         * "next word" really means "[PC++]". These increase the word length of the instruction by 1. 
         * If any instruction tries to assign a literal value, the assignment fails silently. Other than that, the instruction behaves as normal.
         * All values that read a word (0x10-0x17, 0x1e, and 0x1f) take 1 cycle to look up. The rest take 0 cycles.
         * By using 0x18, 0x19, 0x1a as POP, PEEK and PUSH, there's a reverse stack starting at memory location 0xffff. Example: "SET PUSH, 10", "SET X, POP"        
         */
        switch ( node.getAddressingMode() ) 
        {
            case IMMEDIATE:
                ConstantValueNode valueNode = node.getLiteralValueNode();
                Long value2 = valueNode == null ? null : valueNode.getNumericValue(symbolTable);
                if ( value2 == null ) {
                    return null; // => ObjectCodeOutputNode#UNKNOWN_SIZE
                }
                long value = value2;
                
                if ( value <= 0x1f ) 
                {
                    return operandDesc( 0x20 + (int) value );
                }
                return operandDesc( 0x1f , true  ); // value too large , cannot be inlined
            case INDIRECT:
                return operandDesc( 0x1e , true );
            case INDIRECT_REGISTER:
                Register register = node.getRegister();
                if ( register == Register.SP ) 
                {
                    return operandDesc( 0x19 );
                } else if ( register == Register.PC ||register == Register.O ) {
                    throw new RuntimeException("Internal error, register "+register+" must not be used with addressing mode INDIRECT_REGISTER");                    
                }
                return operandDesc( getRegisterBitmask( register , 0x08 ) );
            case INDIRECT_REGISTER_POSTINCREMENT:
                register = node.getRegister();
                if ( register != Register.SP ) 
                {
                    throw new RuntimeException("Internal error, register "+register+" must not be used with addressing mode INDIRECT_REGISTER_POSTINCREMENT");                    
                }
                return operandDesc( 0x18 );
            case INDIRECT_REGISTER_PREDECREMENT:
                register = node.getRegister();
                if ( register != Register.SP ) 
                {
                    throw new RuntimeException("Internal error, register "+register+" must not be used with addressing mode INDIRECT_REGISTER_PREDECREMENT");                    
                }
                return operandDesc( 0x1a );                 
            case INDIRECT_REGISTER_OFFSET:
            	
                valueNode = node.getLiteralValueNode();
                value2 = valueNode == null ? null : valueNode.getNumericValue(symbolTable);
                if ( value2 == null ) {
                    return null; // => ObjectCodeOutputNode#UNKNOWN_SIZE
                }
                value = value2;
                
                register = node.getRegister();
                if ( register == Register.SP || register == Register.PC || register == Register.O ) {
                    throw new RuntimeException("Internal error, register "+register+" must not be used with addressing mode INDIRECT_REGISTER_OFFSET");
                }
                
                if ( value <= 0x1f ) {
                    return operandDesc( (int) ( getRegisterBitmask( register , 0x10 ) | ( 0x20+value ) ) );                	
                }
                return operandDesc( getRegisterBitmask( register , 0x10 ) , true  );             
            case REGISTER:
                register = node.getRegister();
                if ( register == Register.SP ) {
                    return operandDesc( 0x1b );
                } else if ( register == Register.PC ) {
                    return operandDesc( 0x1c );
                } else if ( register == Register.O ) {
                    return operandDesc( 0x1d );
                }
                return operandDesc( getRegisterBitmask( register , 0x00 ) );                
        }
        throw new RuntimeException("Unhandled addressing mode: "+node.getAddressingMode()+" , operand: "+node);     
    }

    private int getRegisterBitmask(Register register , int offset) {
        /*
         * 0x00-0x07: register (A, B, C, X, Y, Z, I or J, in that order)
         * 0x08-0x0f: [register]
         * 0x10-0x17: [next word + register]         
         */
        switch( register ) {
            case A:
                return 0+offset;
            case B:
                return 1+offset;
            case C:
                return 2+offset;
            case X:
                return 3+offset;
            case Y:
                return 4+offset;
            case Z:
                return 5+offset;
            case I:
                return 6+offset;
            case J:
                return 7+offset;
        }
        throw new RuntimeException("Cannot handle register: "+register);
    }

    public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) {

        switch( getValidOperandPositions( mode, register ) ) 
        {
            case SOURCE_OPERAND:
                return pos == OperandPosition.SOURCE_OPERAND;
            case SOURCE_OR_TARGET:
                return pos == OperandPosition.SOURCE_OPERAND ||
                pos == OperandPosition.TARGET_OPERAND ;
            case TARGET_OPERAND:
                return pos == OperandPosition.TARGET_OPERAND ;
            case NOT_POSSIBLE:
                return false;
        }
        throw new RuntimeException("Unreachable code reached");
    }

    private OperandPosition getValidOperandPositions(AddressingMode addressingMode, Register register) 
    {
        switch( addressingMode ) 
        {
            case REGISTER: // SET a,10 
                return OperandPosition.SOURCE_OR_TARGET;
            case INDIRECT_REGISTER:
                return OperandPosition.SOURCE_OR_TARGET;
            case INDIRECT_REGISTER_POSTINCREMENT:
                if ( register == Register.SP ) {
                    return OperandPosition.SOURCE_OR_TARGET; // POP / [SP++] 
                }
                return OperandPosition.NOT_POSSIBLE;
            case INDIRECT_REGISTER_PREDECREMENT:
                if ( register == Register.SP ) {
                    return OperandPosition.TARGET_OPERAND; // PUSH / [--SP]  
                }
                return OperandPosition.NOT_POSSIBLE;
            case INDIRECT_REGISTER_OFFSET:
                if (register == Register.SP) {
                    return OperandPosition.NOT_POSSIBLE;
                }
                return OperandPosition.SOURCE_OR_TARGET;			
            case INDIRECT:
                return OperandPosition.SOURCE_OR_TARGET;
            case IMMEDIATE:
                if ( isConditionalBranchOpCode() ) {
                    return OperandPosition.SOURCE_OR_TARGET;  
                }
                return OperandPosition.SOURCE_OPERAND;			
        }
        throw new RuntimeException("Internal error, unreachable code " +
                "reached (addressing mode: "+addressingMode+")");
    }    
    
    protected boolean isConditionalBranchOpCode() {
        return false;
    }

} 