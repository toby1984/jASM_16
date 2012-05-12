package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

/**
 * Abstract base-class for implementing {@link IEmulationListener}s.
 * 
 * <p>Beware, this classes {@link #isInvokeBeforeCommandExecution()} implementation returns
 * <code>false</code> by default (=do <b>NOT</b> invoke {@link #beforeCommandExecution(IEmulator)}.</p>
 * 
 * <p>Beware, this classes {@link #isInvokeAfterAndBeforeCommandExecutionInContinuousMode()} implementation returns
 * <code>false</code> by default.
 *  
 * @author tobias.gierke@code-sourcery.de
 */
public class EmulationListener implements IEmulationListener {

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

	public void afterContinuousExecutionHook() {
	}
	
	@Override
	public void afterReset(IEmulator emulator) {
	}

	@Override
	public void afterMemoryLoad(IEmulator emulator, Address startAddress,int lengthInBytes) 
	{
	}

	@Override
	public void onBreakpoint(IEmulator emulator, Breakpoint breakpoint) 
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

	@Override
	public void breakpointAdded(IEmulator emulator, Breakpoint breakpoint) {
	}

	@Override
	public void breakpointDeleted(IEmulator emulator, Breakpoint breakpoint) {
	}

	@Override
	public void breakpointChanged(IEmulator emulator, Breakpoint breakpoint) {
	}

	@Override
	public boolean isInvokeBeforeCommandExecution() {
		return false;
	}

	@Override
	public boolean isInvokeAfterCommandExecution() {
		return true;
	}
	
	@Override
	public boolean isInvokeAfterAndBeforeCommandExecutionInContinuousMode() {
		return false;
	}
}
