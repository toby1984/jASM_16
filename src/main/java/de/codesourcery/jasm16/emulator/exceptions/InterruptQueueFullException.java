package de.codesourcery.jasm16.emulator.exceptions;

/**
 * Thrown when the emulator's internal interrupt queue overflows
 * because interrupts have been disabled for too long and piled up.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class InterruptQueueFullException extends EmulationErrorException
{
    public InterruptQueueFullException(String message)
    {
        super(message);
    }
}
