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
 * Listener that gets called by the {@link IEmulator} whenever important events happen.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IEmulationListener
{
    public void beforeEmulatorIsDisposed(IEmulator emulator);
    
    public void beforeCalibration(IEmulator emulator);
    
    public void afterCalibration(IEmulator emulator);
    
    public void onEmulationSpeedChange(EmulationSpeed oldSpeed, EmulationSpeed newSpeed);
    
    /**
     * Indicates whether this emulation listener belongs to a hardware device.
     * 
     * <p>Listeners registered by hardware devices will never be removed when
     * {@link IEmulator#removeAllEmulationListeners()} is executed.</p>.
     * 
     * @return <code>true</code> if this listener was registered by a hardware device.
     * @see IDevice
     */
    public boolean belongsToHardwareDevice();
    
	/**
	 * Invoked before the emulation starts running
	 * at full speed.
	 * 
	 * <p>When invoked, listeners should try to minimize their performance
	 * impact by disabling any actions that would not make sense
	 * while running in full-speed mode.</p>
	 * @param emulator TODO
	 * @see IEmulator#start()
	 */
	public void beforeContinuousExecution(IEmulator emulator);
	
	/**
	 * Invoked after the emulation stopped.
	 * 
	 * <p>When invoked, listeners may re-enable actions suitable for
	 * single-step execution that were disabled by the last call
	 * to {@link #beforeContinuousExecution(IEmulator)}.</p>
	 * @param emulator TODO
	 * 	 
	 * @see IEmulator#stop()
	 */
	public void onStop(IEmulator emulator,Address previousPC , Throwable emulationError);
	
    public void afterReset(IEmulator emulator);
    
    public void afterMemoryLoad(IEmulator emulator, Address startAddress,int lengthInBytes);
    
    public void breakpointAdded(IEmulator emulator,Breakpoint breakpoint);
    
    public void breakpointDeleted(IEmulator emulator,Breakpoint breakpoint);
    
    public void breakpointChanged(IEmulator emulator,Breakpoint breakpoint);    
    
    public void onBreakpoint(IEmulator emulator,Breakpoint breakpoint);
    
    /**
     * Invoked before executing the next command.
     * 
     * <p>Note that this method will only ever be invoked if the listener also returns
     * <code>true</code> from {@link #isInvokeBeforeCommandExecution()}.</p>
     *      
     * @param emulator
     * @see #isInvokeBeforeCommandExecution()
     */
    public void beforeCommandExecution(IEmulator emulator);
    
    /**
     * Check whether this listener wants to be invoked before
     * each command execution.
     * 
     * <p>
     * This method is used by the emulator to determine up-front whether a listener needs
     * to be invoked on every command execution.</p>
     * <p>The emulator will only check this flag <b>ONCE</b> when
     * the listener is first registered.</p>
     * @return
     */
    public boolean isInvokeBeforeCommandExecution();
    
    /**
     * Check whether this listener wants to be invoked
     * after each command execution.
     * 
     * <p>
     * This method is used by the emulator to determine up-front whether a listener needs
     * to be invoked on every command execution.</p>
     *      
     * <p>The emulator will only check this flag <b>ONCE</b> when
     * the listener is first registered.</p>     
     * @return
     */
    public boolean isInvokeAfterCommandExecution();
    
    /**
     * Check whether this listener's {@link #beforeCommandExecution(IEmulator)} and
     * {@link #afterCommandExecution(IEmulator, int)} methods should be invoked
     * when the emulator is running in continuous (non-single-step) mode.
     * 
     * @return
     */
    public boolean isInvokeAfterAndBeforeCommandExecutionInContinuousMode();
    
    /**
     * Invoked after executing a command.
     * 
     * <p>Note that this method will only ever be invoked if the listener also returns
     * <code>true</code> from {@link #isInvokeAfterCommandExecution()}.</p>
     * 
     * @param emulator
     * @param commandDuration duration (in cycles) of the last command or -1 on internal errors during command execution.
     * @see #isInvokeAfterCommandExecution()
     */
    public void afterCommandExecution(IEmulator emulator,int commandDuration);
}
