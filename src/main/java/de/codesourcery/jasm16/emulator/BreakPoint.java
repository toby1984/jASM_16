package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

/**
 * Debugger breakpoint.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class BreakPoint
{
    // Address MUST be immutable !!!
    private final Address address;
    
    public BreakPoint(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("address must not be NULL.");
        }
        this.address = address;
    }
    
    public Address getAddress()
    {
        return address;
    }
    
}
