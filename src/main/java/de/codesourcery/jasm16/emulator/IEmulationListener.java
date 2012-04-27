package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

/**
 * 
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IEmulationListener
{

    public void onReset(Emulator emulator);
    
    public void onMemoryLoad(Emulator emulator, Address startAddress,int lengthInBytes);
    
    /**
     * Invoked before executing the next command.
     * 
     * @param emulator
     */
    public void beforeExecution(Emulator emulator);
    
    /**
     * Invoked after executing a command.
     * 
     * @param emulator
     * @param commandDuration duration (in cycles) of the last command or -1 on internal errors during command execution.
     */
    public void afterExecution(Emulator emulator,int commandDuration);
}
