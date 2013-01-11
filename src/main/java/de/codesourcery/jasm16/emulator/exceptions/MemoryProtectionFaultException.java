package de.codesourcery.jasm16.emulator.exceptions;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.Emulator;

/**
 * Thrown when the emulator is running with {@link Emulator#checkMemoryWrites} and
 * the program tries to write memory that was already part of the execution path (and 
 * thus contains program code and not data).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class MemoryProtectionFaultException extends EmulationErrorException {

    private final Address offendingWriteAddress;

    public MemoryProtectionFaultException(String message,Address offendingWriteAddress)
    {
        super(message);
        this.offendingWriteAddress = offendingWriteAddress;
    }

    /**
     * Returns the address the program was trying to write to.
     * @return
     */
    public Address getOffendingWriteAddress()
    {
        return offendingWriteAddress;
    }
}