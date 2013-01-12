package de.codesourcery.jasm16.emulator.exceptions;

public class EmulationErrorException extends RuntimeException {

    public EmulationErrorException(String message)
    {
        this(message,null);
    }
    
    public EmulationErrorException(String message,Throwable t)
    {
        super(message,t);
    }    
}  