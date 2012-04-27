package de.codesourcery.jasm16.emulator;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.OperandNode.OperandPosition;
import de.codesourcery.jasm16.utils.Misc;

public class Disassembler
{
    private interface IMemoryIterator {

        public Address currentAddress();
        
        public int nextWord();

        public boolean hasNext();
    }
    
    public static final class DisassembledLine 
    {
        private final Address address;
        private final String contents;
        
        public DisassembledLine(Address address, String contents)
        {
            if ( address== null ) {
                throw new IllegalArgumentException("address  must not be NULL.");
            }
            if ( contents == null ) {
                throw new IllegalArgumentException("contents must not be NULL.");
            }            
            this.address = address;
            this.contents = contents;
        }
        
        public Address getAddress()
        {
            return address;
        }
        
        public String getContents()
        {
            return contents;
        }
    }
    
    public List<DisassembledLine> disassemble(final Address startingAddress, final byte[] data, final int instructionCountToDisassemble,boolean printHexDump) 
    {
        final IMemory mem = new IMemory() {
            
            @Override
            public int readWord(Address address)
            {
                int offset = address.getValue()*2;
                if ( offset >= data.length ) {
                    return 0;
                }
                int hi = data[offset++];
                if ( hi < 0 ) {
                    hi+=256;
                }
                if ( offset >= data.length ) {
                    return ( hi & 0x00ff);
                }                
                int lo = data[offset];
                if ( lo < 0 ) {
                    lo += 256;
                }
                final int result= (( hi << 8 ) | lo) & 0xffff;
                return result;
            }
            
            @Override
            public byte[] getBytes(Address startAddress, int lengthInBytes)
            {
                if ( startAddress.getValue() < 0 || startAddress.getValue() > Address.MAX_ADDRESS ) {
                    throw new IllegalArgumentException("Invalid address: "+startAddress);
                }
                
                final int startOffset =  startAddress.getValue() << 1; // *2 => byte offset
                final int endOffset = (int) ( ( ( startOffset + lengthInBytes ) % Address.MAX_ADDRESS ) & 0xffff ); 
                if ( startOffset <= endOffset ) {
                    return ArrayUtils.subarray( data , startOffset , endOffset-startOffset );
                }
                // need to properly wrap-around for displaying stack frames: getBytes( 65535 , 2 ) ;
                final byte[] firstArray = ArrayUtils.subarray( data , startOffset , 65536 - startOffset );
                final byte[] secondArray = ArrayUtils.subarray( data , 0 , endOffset );
                final byte[] result = new byte[ firstArray.length + secondArray.length ];
                System.arraycopy( firstArray , 0 , result , 0 , firstArray.length );
                System.arraycopy( secondArray , 0 , result , firstArray.length , secondArray.length );
                return result;
            }
            
            @Override
            public void bulkLoad(Address startingOffset, byte[] data)
            {
                throw new UnsupportedOperationException();
            }
        };
        return disassemble( mem , startingAddress , instructionCountToDisassemble , printHexDump );
    }

    public List<DisassembledLine> disassemble(final IMemory memory,final Address startingAddress, final int instructionCountToDisassemble,boolean printHexDump) 
    {
        final int[] instructionsLeft = { instructionCountToDisassemble };

        final IMemoryIterator iterator = new IMemoryIterator() {

            private int current = startingAddress.getValue();

            @Override
            public int nextWord()
            {
                return memory.readWord( Address.valueOf( current++ ) );
            }

            @Override
            public boolean hasNext()
            {
                return current < 65536;
            }

            @Override
            public Address currentAddress()
            {
                return Address.valueOf( current );
            }
        };

        final List<DisassembledLine> lines = new ArrayList<DisassembledLine>();
        while( instructionsLeft[0] > 0 && iterator.hasNext() ) 
        {
            final Address current = iterator.currentAddress();
            String contents = dissassembleInstruction( iterator );
            
            if ( printHexDump ) 
            {
                int lengthInBytes = 2*(iterator.currentAddress().getValue() - current.getValue());
                byte[] data = memory.getBytes( current , lengthInBytes );
                final String hex = " ; "+Misc.toHexDumpWithoutAddresses( 0 , data , data.length , 8 ).replaceAll("\n","");
                contents = contents + hex;
            }
            
            lines.add( new DisassembledLine( current , contents ) );
            instructionsLeft[0]--;
        }
        return lines;
    }

    private String dissassembleInstruction(IMemoryIterator iterator)
    {
        final int instructionWord = iterator.nextWord();
        
        final int basicOpCode = (instructionWord & 0x1f); // 1+2+4+8+16

        switch( basicOpCode ) {
            case 0x00:
                return handleSpecialOpCode( iterator , instructionWord );
            case 0x01:
                return handleSET( iterator , instructionWord );
            case 0x02:
                return handleADD( iterator , instructionWord );
            case 0x03:
                return handleSUB( iterator , instructionWord );
            case 0x04:
                return handleMUL( iterator , instructionWord );
            case 0x05:
                return handleMLI( iterator , instructionWord );
            case 0x06:
                return handleDIV( iterator , instructionWord );
            case 0x07:
                return handleDVI( iterator , instructionWord );
            case 0x08:
                return handleMOD( iterator , instructionWord );
            case 0x09:
                return handleMDI( iterator , instructionWord );
            case 0x0a:
                return handleAND( iterator , instructionWord );
            case 0x0b:
                return handleBOR( iterator , instructionWord );
            case 0x0c:
                return handleXOR( iterator , instructionWord );
            case 0x0d:
                return handleSHR( iterator , instructionWord );
            case 0x0e:
                return handleASR( iterator , instructionWord );
            case 0x0f:
                return handleSHL( iterator , instructionWord );
            case 0x10:
                return handleIFB( iterator , instructionWord );
            case 0x11:
                return handleIFC( iterator , instructionWord );
            case 0x12:
                return handleIFE( iterator , instructionWord );
            case 0x13:
                return handleIFN( iterator , instructionWord );
            case 0x14:
                return handleIFG( iterator , instructionWord );
            case 0x15:
                return handleIFA( iterator , instructionWord );
            case 0x16:
                return handleIFL( iterator , instructionWord );
            case 0x17:
                return handleIFU( iterator , instructionWord );
            case 0x18:
            case 0x19:
                return handleUnknownOpCode( iterator , instructionWord );
            case 0x1a:
                return handleADX( iterator , instructionWord );
            case 0x1b:
                return handleSBX( iterator , instructionWord );
            case 0x1c:
            case 0x1d:
                return handleUnknownOpCode( iterator , instructionWord );
            case 0x1e:
                return handleSTI( iterator , instructionWord );
            case 0x1f:
                return handleSTD( iterator , instructionWord );
            default:
                return handleUnknownOpCode( iterator , instructionWord );
        }        

    }

    private String handleSTD(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "STD" , it , instructionWord );
    }
    
    private String disassembleBasicInstruction(String opCode,IMemoryIterator it , int instructionWord) {
        return opCode+" "+disassembleOperand( it , OperandPosition.TARGET_OPERAND , instructionWord )+" , "
                +disassembleOperand( it , OperandPosition.SOURCE_OPERAND , instructionWord );
    }
    
    private String disassembleSpecialInstruction(String opCode,IMemoryIterator it , int instructionWord) {
        return opCode+" "+disassembleOperand( it , OperandPosition.SOURCE_OPERAND , instructionWord );
    }    

    private String handleSTI(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "STI" , it , instructionWord );
    }

    private String handleSBX(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "SBX" , it , instructionWord );        
    }

    private String handleADX(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "ADX" , it , instructionWord );
    }

    private String handleUnknownOpCode(IMemoryIterator it , int instructionWord)
    {
        return "< unknown opcode: "+Misc.toBinaryString( instructionWord, 16 )+" >";
    }

    private String handleIFU(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "IFU" , it , instructionWord );        
    }

    private String handleIFL(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "IFL" , it , instructionWord );
    }

    private String handleIFA(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "IFA" , it , instructionWord );
    }

    private String handleIFG(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "IFG" , it , instructionWord );
    }

    private String handleIFN(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "IFN" , it , instructionWord );
    }

    private String handleIFE(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "IFE" , it , instructionWord );
    }

    private String handleIFC(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "IFC" , it , instructionWord );
    }

    private String handleIFB(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "IFB" , it , instructionWord );
    }

    private String handleSHL(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "SHL" , it , instructionWord );
    }

    private String handleASR(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "ASR" , it , instructionWord );
    }

    private String handleSHR(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "SHR" , it , instructionWord );
    }

    private String handleXOR(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "XOR" , it , instructionWord );
    }

    private String handleBOR(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "BOR" , it , instructionWord );
    }

    private String handleAND(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "AND" , it , instructionWord );
    }

    private String handleMDI(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "MDI" , it , instructionWord );
    }

    private String handleMOD(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "MOD" , it , instructionWord );
    }

    private String handleDVI(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "DVI" , it , instructionWord );
    }

    private String handleDIV(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "DIV" , it , instructionWord );
    }

    private String handleMLI(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "MLI" , it , instructionWord );
    }

    private String handleMUL(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "MUL" , it , instructionWord );
    }

    private String handleSUB(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "SUB" , it , instructionWord );
    }

    private String handleADD(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "ADD" , it , instructionWord );
    }

    private String handleSET(IMemoryIterator it , int instructionWord)
    {
        return disassembleBasicInstruction( "SET" , it , instructionWord );
    }

    private String handleSpecialOpCode(IMemoryIterator it, int instructionWord)
    {

        final int opCode = ( instructionWord >> 5 ) &0x1f;
        switch( opCode ) {
            case 0x00:
                return handleUnknownOpCode( it , instructionWord );
            case 0x01:
                return handleJSR( it , instructionWord );
            case 0x02:
            case 0x03:
            case 0x04:
            case 0x05:
            case 0x06:
                return handleUnknownOpCode( it, instructionWord );
            case 0x07:
                return handleHCF( it , instructionWord );
            case 0x08:
                return handleINT( it , instructionWord );
            case 0x09:
                return handleIAG( it , instructionWord );
            case 0x0a:
                return handleIAS( it , instructionWord );
            case 0x0b:
                return handleIAP( it , instructionWord );
            case 0x0c:
                return handleIAQ( it , instructionWord );
            case 0x0d:
            case 0x0e:
            case 0x0f:
                return handleUnknownOpCode( it , instructionWord );
            case 0x10:
                return handleHWN( it , instructionWord );
            case 0x11:
                return handleHWQ( it , instructionWord );
            case 0x12:
                return handleHWI( it , instructionWord );
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
            case 0x19:
            case 0x1a:
            case 0x1b:
            case 0x1c:
            case 0x1d:
            case 0x1e:
            case 0x1f:
            default:
                return handleUnknownOpCode( it , instructionWord );
        }
    }

    private String handleHWI(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("HWI",it,instructionWord);
    }

    private String handleHWQ(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("HWQ",it,instructionWord);
    }

    private String handleHWN(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("HWN",it,instructionWord);
    }

    private String handleIAQ(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("IAQ",it,instructionWord);
    }

    private String handleIAP(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("IAP",it,instructionWord);
    }

    private String handleIAS(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("IAS",it,instructionWord);
    }

    private String handleIAG(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("IAG",it,instructionWord);
    }

    private String handleINT(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("INT",it,instructionWord);
    }

    private String handleHCF(IMemoryIterator it, int instructionWord)
    {
        return disassembleSpecialInstruction("HCF",it,instructionWord);
    }

    private String handleJSR(IMemoryIterator it , int instructionWord)
    {
        return disassembleSpecialInstruction("JSR",it,instructionWord);
    }

    protected String disassembleOperand(IMemoryIterator it , OperandPosition position , int instructionWord) {

        final boolean specialInstruction = (instructionWord & 0x1f) == 0;
        
        /*
         * SET b,a
         * 
         * b = TARGET operand
         * a = SOURCE operand
         * 
         * SOURCE is always handled by the processor BEFORE TARGET, and is the lower five bits.
         * In bits (in LSB-0 format), a basic instruction has the format: 
         * 
         * aaaaaabbbbbooooo      
         * 
         * SPECIAL opcodes always have their lower five bits unset, have one value and a
         * five bit opcode. In binary, they have the format: 
         * 
         * aaaaaaooooo00000
         * 
         * The value (a) is in the same six bit format as defined earlier.    
         * 
         * --- Values: (5/6 bits) ---------------------------------------------------------
         * 
         * | C | VALUE     | DESCRIPTION
         * +---+-----------+----------------------------------------------------------------
         * | 0 | 0x00-0x07 | register (A, B, C, X, Y, Z, I or J, in that order)
         * | 0 | 0x08-0x0f | [register]
         * | 1 | 0x10-0x17 | [register + next word]
         * | 0 |      0x18 | (PUSH / [--SP]) if in TARGET, or (POP / [SP++]) if in SOURCE
         * | 0 |      0x19 | [SP] / PEEK
         * | 1 |      0x1a | [SP + next word] / PICK n
         * | 0 |      0x1b | SP
         * | 0 |      0x1c | PC
         * | 0 |      0x1d | EX
         * | 1 |      0x1e | [next word]
         * | 1 |      0x1f | next word (literal)
         * | 0 | 0x20-0x3f | literal value 0xffff-0x1e (-1..30) (literal) (only for SOURCE)
         * +---+-----------+----------------------------------------------------------------             
         */

        final int operandBits;
        if ( specialInstruction || position == OperandPosition.SOURCE_OPERAND ) {
            // aaaaaa bbbbb ooooo
//            System.out.println("Instruction: "+Misc.toHexString( instructionWord )+" => "+Misc.toBinaryString( instructionWord , 16 , 4, 9 )+" ( special: "+specialInstruction+")");            
            operandBits = (instructionWord >> 10) & ( 1+2+4+8+16+32 ); // 6 operand bit              
        } else { 
            // aaaaaa bbbbb ooooo
//            System.out.println("Instruction: "+Misc.toHexString( instructionWord )+" => "+Misc.toBinaryString( instructionWord , 16 , 4, 9 )+" ( special: "+specialInstruction+")");            
            operandBits = (instructionWord >> 5) & ( 1+2+4+8+16); // 5 operand bits   
        }
        if ( operandBits < 0 ) {
            throw new RuntimeException("Internal error");
        }
        if ( operandBits <= 0x07 ) {
            return ICPU.COMMON_REGISTER_NAMES[ operandBits ];
        }
        if ( operandBits <= 0x0f ) {
            return "["+ICPU.COMMON_REGISTER_NAMES[ ( operandBits - 0x08 ) ]+"]";
        }
        if ( operandBits <= 0x17 ) 
        {
            final int nextWord = it.nextWord();
            return "["+ICPU.COMMON_REGISTER_NAMES[ operandBits - 0x10 ]+" + 0x"+Misc.toHexString( nextWord)+"]";            
        }

        switch( operandBits ) {
            case 0x18:
                return "[SP++]";
            case 0x19:
                return "[SP]";
            case 0x1a:
                int nextWord = it.nextWord();                
                return "[SP + 0x"+Misc.toHexString( nextWord)+"]";
            case 0x1b:
                return "SP";
            case 0x1c:
                return "PC";
            case 0x1d:
                return "EX";
            case 0x1e:
                nextWord = it.nextWord();
                return "[ 0x"+Misc.toHexString( nextWord )+" ]";
            case 0x1f:
                nextWord = it.nextWord();
                return "0x"+Misc.toHexString( nextWord );
        }

        if ( position == OperandPosition.SOURCE_OPERAND || specialInstruction ) {
            final int literalValue = operandBits - 0x21;
            return "0x"+Misc.toHexString( literalValue );
        }
        return "<illegal operand bits: "+Misc.toBinaryString( operandBits , 5 )+">";

    }
}