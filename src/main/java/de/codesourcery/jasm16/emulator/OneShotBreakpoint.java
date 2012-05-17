package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

/**
 * A special "invisible" breakpoint that automatically gets deleted when
 * {@link IEmulator#stop()} is executed.
 * 
 * <p>This breakpoint does <b>NOT</b> trigger <b>ANY</b> 
 * breakpoint-related methods of {@link IEmulationListener}.
 *
 * <p>At any address, at most one regular breakpoint and one one-shot breakpoint may be set.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class OneShotBreakpoint extends Breakpoint {

	public OneShotBreakpoint(Address address) {
		super(address);
	}

    public boolean isOneShotBreakpoint() {
    	return true;
    }	
}