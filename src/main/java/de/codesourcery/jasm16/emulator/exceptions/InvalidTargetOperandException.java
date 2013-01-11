package de.codesourcery.jasm16.emulator.exceptions;


public final class InvalidTargetOperandException extends EmulationErrorException {

    public InvalidTargetOperandException(String message)
    {
        super(message);
    }
}  