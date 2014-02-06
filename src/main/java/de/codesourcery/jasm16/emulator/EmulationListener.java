/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.IEmulator.EmulationSpeed;

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
	
	@Override
    public void beforeCalibration(IEmulator emulator) {
    }
    
    @Override
    public void afterCalibration(IEmulator emulator) {
    }
    	
	@Override
	public boolean belongsToHardwareDevice()
	{
	    return false;
	}
	
	protected void beforeContinuousExecutionHook() {
	}
	
    public void onEmulationSpeedChange(EmulationSpeed oldSpeed, EmulationSpeed newSpeed) {
    }
	
	protected final boolean isFullSpeedMode() {
		return fullSpeedMode;
	}
	
	@Override
	public final void onStop(IEmulator emulator,Address previousPC , Throwable emulationError) {
		fullSpeedMode = false;
		onStopHook(emulator, previousPC, emulationError);
	}

	public void onStopHook(IEmulator emulator, Address previousPC, Throwable emulationError) {
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

    @Override
    public void beforeEmulatorIsDisposed(IEmulator emulator)
    {
    }

    @Override
    public void allBreakpointsDeleted(IEmulator emulator) {
    }
}
