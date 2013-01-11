package de.codesourcery.jasm16.emulator.exceptions;


public final class InvalidDeviceSlotNumberException extends EmulationErrorException {

    public InvalidDeviceSlotNumberException(String message)
    {
        super(message);
    }
}