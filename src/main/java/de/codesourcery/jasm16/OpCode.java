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

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.ast.OperandNode;
import de.codesourcery.jasm16.ast.OperandNode.OperandPosition;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
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
     *                === Basic opcodes (5 bits) ===
     *   |
     *   |C | VAL  | NAME     | DESCRIPTION
     *   +--+------+----------+---------------------------------------------------------
     *   |- | 0x00 | n/a      | special instruction - see below
     *   |1 | 0x01 | SET b, a | sets b to a
     *   |2 | 0x02 | ADD b, a | sets b to b+a, sets EX to 0x0001 if there's an overflow,  0x0 otherwise
     *   |2 | 0x03 | SUB b, a | sets b to b-a, sets EX to 0xffff if there's an underflow, 0x0 otherwise
     *   |2 | 0x04 | MUL b, a | sets b to b*a, sets EX to ((b*a)>>16)&0xffff (treats b, a as unsigned)
     *   |2 | 0x05 | MLI b, a | like MUL, but treat b, a as signed
     *   |3 | 0x06 | DIV b, a | sets b to b/a, sets EX to ((b<<16)/a)&0xffff. if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
     *   |3 | 0x07 | DVI b, a | like DIV, but treat b, a as signed. Rounds towards 0
     *   |3 | 0x08 | MOD b, a | sets b to b%a. if a==0, sets b to 0 instead.
     *   |3 | 0x09 | MDI b, a | like MOD, but treat b, a as signed. Rounds towards 0
     *   
     *   |1 | 0x0a | AND b, a | sets b to b&a
     *   |1 | 0x0b | BOR b, a | sets b to b|a
     *   |1 | 0x0c | XOR b, a | sets b to b^a
     *   
     *   |2 | 0x0d | SHR b, a | sets b to b>>>a, sets EX to ((b<<16)>>a)&0xffff (logical shift)
     *   |2 | 0x0e | ASR b, a | sets b to b>>a, sets EX to ((b<<16)>>>a)&0xffff  (arithmetic shift) (treats b as signed)
     *   |2 | 0x0f | SHL b, a | sets b to b<<a, sets EX to ((b<<a)>>16)&0xffff
     *   
     *   |2+| 0x10 | IFB b, a | performs next instruction only if (b&a)!=0
     *   |2+| 0x11 | IFC b, a | performs next instruction only if (b&a)==0
     *   |2+| 0x12 | IFE b, a | performs next instruction only if b==a 
     *   |2+| 0x13 | IFN b, a | performs next instruction only if b!=a 
     *   |2+| 0x14 | IFG b, a | performs next instruction only if b>a 
     *   |2+| 0x15 | IFA b, a | performs next instruction only if b>a (signed)
     *   |2+| 0x16 | IFL b, a | performs next instruction only if b<a 
     *   |2+| 0x17 | IFU b, a | performs next instruction only if b<a (signed)
     *   |- | 0x18 | -        |
     *   |- | 0x19 | -        |
     *   |3 | 0x1a | ADX b, a | sets b to b+a+EX, sets EX to 0x0001 if there is an over-
     *   |  |      |          | flow, 0x0 otherwise
     *   |3 | 0x1b | SBX b, a | sets b to b-a+EX, sets EX to 0xFFFF if there is an under-
     *   |  |      |          | flow, 0x0 otherwise
     *   |- | 0x1c | -        | 
     *   |- | 0x1d | -        |
     *   |2 | 0x1e | STI b, a | sets b to a, then increases I and J by 1
     *   |2 | 0x1f | STD b, a | sets b to a, then decreases I and J by 1
     *   +--+------+----------+---------------------------------------------------------
     *   
     *   
     *                       === Special opcodes: (5 bits) ===
     *                       
     * 
     *  | C | VAL  | NAME  | DESCRIPTION
     *  +---+------+-------+-------------------------------------------------------------
     *  | - | 0x00 | n/a   | reserved for future expansion
     *  | 3 | 0x01 | JSR a | pushes the address of the next instruction to the stack,
     *  |   |      |       | then sets PC to a
     *  | - | 0x02 | -     |
     *  | - | 0x03 | -     |
     *  | - | 0x04 | -     |
     *  | - | 0x05 | -     |
     *  | - | 0x06 | -     |
     *  | 9 | 0x07 | HCF a | use sparingly
     *  | 4 | 0x08 | INT a | triggers a software interrupt with message a
     *  | 1 | 0x09 | IAG a | sets a to IA 
     *  | 1 | 0x0a | IAS a | sets IA to a
     *  | 3 | 0x0b | IAP a | if IA is 0, does nothing, otherwise pushes IA to the stack,
     *  |   |      |       | then sets IA to a
     *  | 2 | 0x0c | IAQ a | if a is nonzero, interrupts will be added to the queue
     *  |   |      |       | instead of triggered. if a is zero, interrupts will be
     *  |   |      |       | triggered as normal again
     *  | - | 0x0d | -     |
     *  | - | 0x0e | -     |
     *  | - | 0x0f | -     |
     *  | 2 | 0x10 | HWN a | sets a to number of connected hardware devices
     *  | 4 | 0x11 | HWQ a | sets A, B, C, X, Y registers to information about hardware a
     *  |   |      |       | A+(B<<16) is a 32 bit word identifying the hardware id
     *  |   |      |       | C is the hardware version
     *  |   |      |       | X+(Y<<16) is a 32 bit word identifying the manufacturer
     *  | 4+| 0x12 | HWI a | sends an interrupt to hardware a
     *  | - | 0x13 | -     |
     *  | - | 0x14 | -     |
     *  | - | 0x15 | -     |
     *  | - | 0x16 | -     |
     *  | - | 0x17 | -     |
     *  | - | 0x18 | -     |
     *  | - | 0x19 | -     |
     *  | - | 0x1a | -     |
     *  | - | 0x1b | -     |
     *  | - | 0x1c | -     |
     *  | - | 0x1d | -     |
     *  | - | 0x1e | -     |
     *  | - | 0x1f | -     |
     *  +---+------+-------+-------------------------------------------------------------     
     */
    
    // general
    SET("set",2,0x01) // sets b to a 
    {
        @Override
        public boolean isValidAddressingMode(OperandPosition position , AddressingMode type) 
        {
            if ( position == OperandPosition.TARGET_OPERAND ) 
            {
                if ( type.equals( AddressingMode.IMMEDIATE ) ) {
                    return false;
                }
            }
            return true;
        }
    },
    
    // arithmetics
    ADD("add",2,0x02),
    SUB("sub",2,0x03),
    MUL("mul",2,0x04),
    MLI("mli",2,0x05),
    DIV("div",2,0x06),
    DVI("dvi",2,0x07),    
    MOD("mod",2,0x08),	
    MDI("mdi",2,0x09), 
    ADX("adx",2,0x1a),
    SBX("sbx",2,0x1b),
    STI("sti",2,0x1e),
    STD("std",2,0x1f),

    // EXTENDED opcodes / control flow
    JSR( "jsr",1,0x01) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // operand no. 0 is always considered to be the TARGET operand
            // while operand no. 1 is always considered to be the SOURCE operand.

            // This breaks stuff in case of JSR which only takes a single operand
            // that needs to be treated as SOURCE (because it needs to provide the jump target)
            // Thus we'll just hard-code the operand position to always be SOURCE before
            // actually doing the check
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },
    /**
     * Officially no longer supported.
     * 
     * @see #isHaltInstruction(int)
     */
    HCF( "hcf",1,0x07) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // use hard-coded operand position here, see in-line comment in JSR enum constant for reason
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },    
    INT( "int",1,0x08) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // use hard-coded operand position here, see in-line comment in JSR enum constant for reason
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },    
    IAG( "iag",1,0x09) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            return super.isOperandValidInPosition( OperandPosition.TARGET_OPERAND , mode , register ); // IAG assigns IA to a so cannot have a literal value as a target
        }
    },  
    IAS( "ias",1,0x0a) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // use hard-coded operand position here, see in-line comment in JSR enum constant for reason            
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },  
    RFI( "rfi",1,0x0b) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // use hard-coded operand position here, see in-line comment in JSR enum constant for reason
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },   
    IAQ( "iaq",1,0x0c) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // use hard-coded operand position here, see in-line comment in JSR enum constant for reason
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },  
    HWN( "hwn",1,0x10) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // use hard-coded operand position here, see in-line comment in JSR enum constant for reason
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },  
    HWQ( "hwq",1,0x11) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // use hard-coded operand position here, see in-line comment in JSR enum constant for reason
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    }, 
    HWI( "hwi",1,0x12) 
    {
        public boolean isBasicOpCode() { return false; };
        
        public boolean isOperandValidInPosition(OperandPosition pos,AddressingMode mode, Register register) 
        {
            // use hard-coded operand position here, see in-line comment in JSR enum constant for reason
            return super.isOperandValidInPosition( OperandPosition.SOURCE_OPERAND , mode , register );
        }
    },      
    
    
    // conditions
    IFB("ifb",2,0x10) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },  
    IFC("ifc",2,0x11) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },       
    IFE("ife",2,0x12) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },
    IFN("ifn",2,0x13) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },	
    IFG("ifg",2,0x14) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },
    IFA("ifa",2,0x15) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    }, 
    IFL("ifl",2,0x16) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },
    IFU("ifu",2,0x17) {
        @Override
        protected boolean isConditionalBranchOpCode() { return true; }
    },     
    // bit-shifting
    SHR("shr",2,0x0d),    
    ASR("asr",2,0x0e),    
    SHL("shl",2,0x0f),
    
    // boolean operations
    
    AND("and",2,0x0a),
    OR("bor",2 ,0x0b),	
    XOR("xor",2,0x0c);

    private final int opCodeBits;
    private final String identifier;
    private final int operandCount;

    private static volatile Map<String,OpCode> LOWER_CASE_MAP;
    private static volatile Map<String,OpCode> UPPER_CASE_MAP;

    private final Logger LOG = Logger.getLogger( OpCode.class );
    
    private OpCode(String identifier,int operandCount,int opCodeBits) {
        this.identifier = identifier;
        this.operandCount=operandCount;
        this.opCodeBits = opCodeBits;
    }

    protected static final class OperandDesc 
    {
        public final int operandBits;
        public final boolean appendAsWord;

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

    public boolean isValidAddressingMode(OperandPosition position, AddressingMode type) {
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

    /**
     * Returns whether this is a basic (two-operand) opcode.
     * 
     * @return
     * @see #isSpecialOpCode()
     */
    public boolean isBasicOpCode() {
        return true;
    }

    /**
     * Non-basic opcodes always have their lower four bits unset, have one value and a six bit opcode.
     * 
     * In binary, they have the format: aaaaaaoooooo0000
     * The value (a) is in the same six bit format as defined earlier.
     * @return
     * @see #isBasicOpCode()
     */
    public final boolean isSpecialOpCode() {
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

    public int calculateSizeInBytes(ICompilationContext context , InstructionNode instruction)
    {
        final OperandNode operandA = instruction.getOperand(  0, false );
        final OperandNode operandB = instruction.getOperand(  1, false );
		return getInstructionSizeInBytes( context , operandA , operandB );
    }	

    private int getInstructionSizeInBytes(ICompilationContext context, OperandNode operandA, OperandNode operandB) 
    {
    	final byte[] buffer = new byte[6];
    	final int calculatedSize = writeInstruction( context , operandA , operandB , buffer );
    	if ( calculatedSize == InstructionNode.UNKNOWN_SIZE ) {
    		return getMaximumInstructionSizeInBytes(); // assume worst-case scenario
    	}
		return calculatedSize;
	}

	protected int getMaximumInstructionSizeInBytes() {
		return 6;
	}

	/**
	 * Generates object code for a given instruction.
	 * 
	 * @param context
	 * @param instruction
	 * @return object code or <code>null</code> if no object code could be generated because
	 * this method was unable to determine the operands sizes (probably because symbols could not be resolved)
	 */
	public byte[] generateObjectCode(ICompilationContext context , InstructionNode instruction) 
    {
        final byte[] buffer = new byte[6]; // max. instruction length: three words (3*2 bytes)
        final OperandNode targetOperand;
        if ( instruction.getOperandCount() > 0 ) {
            targetOperand = instruction.getOperand( 0 );
        } else {
            targetOperand = null;
        }

        final OperandNode sourceOperand;
        if ( instruction.getOperandCount() > 1 ) {
            sourceOperand = instruction.getOperand( 1 );
        } else {
            sourceOperand = null;
        }        

        final int bytesToWrite = writeInstruction( context , targetOperand, sourceOperand , buffer );
        if ( bytesToWrite != ObjectCodeOutputNode.UNKNOWN_SIZE ) 
        {
            final byte[] result = new byte[ bytesToWrite ];
            System.arraycopy( buffer , 0 , result , 0 , bytesToWrite );
            return result;
        }
        return null;
    }

    private int writeInstruction(ICompilationContext context,OperandNode targetOperand , OperandNode sourceOperand, byte[] buffer) 
    {
        /*
         * Instructions are 1-3 words long and are fully defined by the first word.
         * In a basic instruction, the lower FIVE bits of the first word of the instruction
         * are the opcode, and the remaining ELEVEN bits are split into a FIVE bit value b
         * and a SIX bit value a.
         * 
         * b is always handled by the processor after a, and is the LOWER FIVE bits.
         * 
         * In bits (in LSB-0 format), a basic instruction has the format: 
         * 
         * aaaaaabbbbbooooo
         * 
         * NON-BASIC opcodes always have their lower five bits unset, have one value and a
         * five bit opcode. 
         * 
         * In binary, they have the format: 
         * 
         * aaaaaaooooo00000
         * 
         * The value (a) is in the same six bit format as defined earlier. 
         */
    	final ISymbolTable symbolTable = context.getSymbolTable();
        int opcode = 0;
        OperandDesc descTarget=null;
        OperandDesc descSource=null;
        
        final int OPCODE_BITS = 5;
        
        final boolean inlineLiterals = ! context.hasCompilerOption(CompilerOption.DISABLE_INLINING ) &&
                                         ! context.hasCompilerOption( CompilerOption.GENERATE_RELOCATION_INFORMATION );
        
        if ( isSpecialOpCode() ) 
        {
            if ( targetOperand == null ) {
                throw new RuntimeException("Extended instruction "+this+" requires a single operand, got none");
            }
            if ( sourceOperand != null ) {
                throw new RuntimeException("Extended instruction "+this+" requires a single operand, got two ?");
            }            
            opcode |= ( opCodeBits << OPCODE_BITS );
            descTarget = getOperandBits( symbolTable , OperandPosition.TARGET_OPERAND , targetOperand ,sourceOperand , inlineLiterals );
            if ( descTarget == null ) {
                return ObjectCodeOutputNode.UNKNOWN_SIZE;
            }
            opcode |= ( descTarget.operandBits << 10 );
        } 
        else 
        {
            // handle basic opcode
            opcode |= ( opCodeBits & 0x1f); // 5-bit opcodes = 0x1F
            if ( targetOperand != null ) 
            {
                descTarget = getOperandBits( symbolTable , OperandPosition.TARGET_OPERAND , targetOperand ,sourceOperand , inlineLiterals);
                if ( descTarget == null ) {
                    return ObjectCodeOutputNode.UNKNOWN_SIZE;
                }
                opcode |= ( descTarget.operandBits << OPCODE_BITS );
            }
            if ( sourceOperand != null ) {
                descSource = getOperandBits( symbolTable , OperandPosition.SOURCE_OPERAND , targetOperand ,sourceOperand , inlineLiterals );
                if ( descSource == null ) {
                    return ObjectCodeOutputNode.UNKNOWN_SIZE;
                }
                opcode |= ( descSource.operandBits << 10 );
            }
        }

        // write instruction word 
        int idx = 0;
        buffer[idx++] = (byte) ( ( opcode >> 8 ) & 0xff  );            
        buffer[idx++] = (byte) ( opcode & 0xff );

        // write operand A
        if ( descSource != null && descSource.appendAsWord ) 
        {
			Long literalValue = sourceOperand.getLiteralValue( symbolTable );
            if ( literalValue == null ) {
                return ObjectCodeOutputNode.UNKNOWN_SIZE;
            }            
            final long  value = literalValue;
            buffer[idx++] = (byte) ( ( value >> 8 ) & 0xff );             
            buffer[idx++] = (byte) ( value & 0x00ff );            
        }     
        
        // write operand B
        if ( descTarget != null && descTarget.appendAsWord ) 
        {
            Long literalValue = targetOperand.getLiteralValue( symbolTable );
            if ( literalValue == null ) {
                return ObjectCodeOutputNode.UNKNOWN_SIZE;
            }
            final long value = literalValue;
            buffer[idx++] = (byte) ( ( value  >> 8 ) & 0xff );      
            buffer[idx++] = (byte) ( value & 0xff );            
        }        
        return idx;
    }
    
    private OperandDesc getOperandBits(ISymbolTable symbolTable,
            OperandPosition operandToHandle,
            OperandNode targetOperand,
            OperandNode sourceOperand,boolean inlineLiterals) 
    {
    	final OperandNode operand = operandToHandle == OperandPosition.TARGET_OPERAND ? targetOperand : sourceOperand;
    	
        /*
         * SET TARGET (b) ,SOURCE (a)
         * 
         * aaaaaabbbbbooooo
         * 
         * - 5 bits for the TARGET operand (b)
         * - 6 bits for the SOURCE operand (a)
         *
         * --- Values: (5/6 bits) ---------------------------------------------------------
         *  C | VALUE     | DESCRIPTION
         * ---+-----------+----------------------------------------------------------------
         *  0 | 0x00-0x07 | register (A, B, C, X, Y, Z, I or J, in that order)
         *  0 | 0x08-0x0f | [register]
         *  1 | 0x10-0x17 | [register + next word]
         *  0 |      0x18 | (PUSH / [--SP]) if in b, or (POP / [SP++]) if in a
         *  0 |      0x19 | [SP] / PEEK
         *  1 |      0x1a | [SP + next word] / PICK n
         *  0 |      0x1b | SP
         *  0 |      0x1c | PC
         *  0 |      0x1d | EX
         *  1 |      0x1e | [next word]
         *  1 |      0x1f | next word (literal)
         *  0 | 0x20-0x3f | literal value 0xffff-0x1e (-1..30) (literal) (only for a)
         *  --+-----------+----------------------------------------------------------------      
         *  
         * - "next word" means "[PC++]". Increases the word length of the instruction by 1.
         * - By using 0x18, 0x19, 0x1a as PEEK, POP/PUSH, and PICK there's a reverse stack
         *   starting at memory location 0xffff. Example: "SET PUSH, 10", "SET X, POP"
         *
         */
        switch ( operand.getAddressingMode() ) 
        {
            case IMMEDIATE:
                Long value2 = operand.getLiteralValue( symbolTable );
                if ( value2 == null ) {
                    return null; // => ObjectCodeOutputNode#UNKNOWN_SIZE
                }
                long value = value2;
                
                // inlining is ONLY supported for source operand (a) OR the only operand in single-operand opcodes 
                if ( inlineLiterals && ( operandToHandle == OperandPosition.SOURCE_OPERAND || getOperandCount() == 1 ) && 
                     value >= -1 && value <= 30  ) 
                {
                    value+=1; // shift because -1 = 0x20 , 0 = 0x21 , +++
                    return operandDesc( 0x20 + (int) value );
                }
                return operandDesc( 0x1f , true  ); // value too large (for this operand position), cannot be inlined
            case INDIRECT:
                return operandDesc( 0x1e , true );
            case INDIRECT_REGISTER:
                Register register = operand.getRegister();
                if ( register == Register.SP ) 
                {
                    return operandDesc( 0x19 );
                } else if ( register == Register.PC ||register == Register.EX ) {
                    throw new RuntimeException("Internal error, register "+register+" must not be used with addressing mode INDIRECT_REGISTER");                    
                }
                return operandDesc( getRegisterBitmask( register , 0x08 ) );
            case INDIRECT_REGISTER_POSTINCREMENT:
                register = operand.getRegister();
                if ( register != Register.SP ) 
                {
                    throw new RuntimeException("Internal error, register "+register+" must not be used with addressing mode INDIRECT_REGISTER_POSTINCREMENT");                    
                }
                // SET b,[SP++] => VALID
                // SET [SP++],a => INVALID because there's no opcode for it
                if ( operandToHandle == OperandPosition.TARGET_OPERAND ) {
                    LOG.warn("POP/[SP++] cannot be used as TARGET operand");
                    return null;
                }
                return operandDesc( 0x18 ); // (PUSH / [--SP]) if in b, or (POP / [SP++]) if in a
            case INDIRECT_REGISTER_PREDECREMENT:
                register = operand.getRegister();
                if ( register != Register.SP ) 
                {
                    throw new RuntimeException("Internal error, register "+register+" must not be used with addressing mode INDIRECT_REGISTER_PREDECREMENT");                    
                }
                // SET [--SP],a => VALID
                // SET b,[--SP] => INVALID because there's no opcode for it
                if ( operandToHandle == OperandPosition.SOURCE_OPERAND ) {
                    LOG.warn("PUSH/[--SP] cannot be used as SOURCE operand");
                    return null;
                }                
                return operandDesc( 0x18 ); // (PUSH / [--SP]) if in b, or (POP / [SP++]) if in a                
            case INDIRECT_REGISTER_OFFSET:
                value2 = operand.getLiteralValue(symbolTable);
                if ( value2 == null ) {
                    return null; // => ObjectCodeOutputNode#UNKNOWN_SIZE
                }
                value = value2;
                
                register = operand.getRegister();
                if ( register == Register.SP ) { 
                    return operandDesc( 0x1a , true ); // new since DCPU 1.1: [SP + next word] / PICK n
                }
                
                if ( register == Register.PC || register == Register.EX ) {
                    throw new RuntimeException("Internal error, register "+register+" must not be used with addressing mode INDIRECT_REGISTER_OFFSET");
                }
                return operandDesc( getRegisterBitmask( register , 0x10 ) , true  );             
            case REGISTER:
                register = operand.getRegister();
                if ( register == Register.SP ) {
                    return operandDesc( 0x1b );
                } else if ( register == Register.PC ) {
                    return operandDesc( 0x1c );
                } else if ( register == Register.EX ) {
                    return operandDesc( 0x1d );
                }
                return operandDesc( getRegisterBitmask( register , 0x00 ) );                
        }
        throw new RuntimeException("Unhandled addressing mode: "+operand.getAddressingMode()+" , operand: "+operand);     
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
                    return OperandPosition.SOURCE_OR_TARGET; // PUSH / [--SP]  
                }
                return OperandPosition.NOT_POSSIBLE;
            case INDIRECT_REGISTER_OFFSET:
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
    
    public static boolean isHaltInstruction(int instructionWord) {
        return 0x84e0 == instructionWord;
    }
} 