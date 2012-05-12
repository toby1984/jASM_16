package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

/**
 * Abstract base-class for implementing {@link IEmulationListener}s.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class EmulationListener implements IEmulationListener {

	private volatile boolean fullSpeedMode = false;
	
	@Override
	public final void beforeContinuousExecution(IEmulator emulator) {
		fullSpeedMode = true;
		beforeContinuousExecutionHook();
	}
	
	protected void beforeContinuousExecutionHook() {
	}
	
	protected final boolean isFullSpeedMode() {
		return fullSpeedMode;
	}

	@Override
	public final void afterContinuousExecution(IEmulator emulator) {
		fullSpeedMode = false;
		afterContinuousExecutionHook();
	}

	public abstract void afterContinuousExecutionHook();
	
	@Override
	public void afterReset(IEmulator emulator) {
	}

	@Override
	public void afterMemoryLoad(IEmulator emulator, Address startAddress,int lengthInBytes) 
	{
	}

	@Override
	public void onBreakpoint(IEmulator emulator, BreakPoint breakpoint) 
	{
	}

	@Override
	public void beforeCommandExecution(IEmulator emulator) 
	{
	}

	@Override
	public void afterCommandExecution(IEmulator emulator, int commandDuration) 
	{
	}

}
