package de.codesourcery.jasm16.emulator.exceptions;


public final class UnknownOpcodeException extends EmulationErrorException {

    private final int instructionWord;
    
    public UnknownOpcodeException(String message,int instructionWord)
    {
        super(message);
        this.instructionWord = instructionWord;
    }
    
    public int getInstructionWord()
    {
        return instructionWord;
    }
} 