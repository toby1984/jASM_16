package de.codesourcery.jasm16.emulator.exceptions;

public class EmulationErrorException extends RuntimeException {

    public EmulationErrorException(String message)
    {
        super(message);
    }
}  