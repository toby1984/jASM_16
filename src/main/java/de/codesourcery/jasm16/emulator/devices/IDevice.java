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
package de.codesourcery.jasm16.emulator.devices;

import de.codesourcery.jasm16.emulator.ICPU;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.memory.IMemory;

/**
 * A hardware device.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IDevice {

	/**
	 * Invoked after this device has been added to an emulator.
	 * @param emulator
	 */
	public void afterAddDevice(IEmulator emulator);
	
	/**
	 * Reset this device to it's initial state.
	 */
	public void reset();
	
	/**
	 * Returns whether more than once instance of this device
	 * may be added to an emulator.
	 * @return
	 */
	public boolean supportsMultipleInstances();
	
	/**
	 * Invoked before this device is removed from
	 * an emulator.
	 * 
	 * @param emulator
	 */
	public void beforeRemoveDevice(IEmulator emulator);
	
	/**
	 * Returns the device descriptor for this device.
	 * @return
	 */
	public DeviceDescriptor getDeviceDescriptor();
	
	/**
	 * Handle a hardware interrupt triggered by the application.
	 * @param emulator the emulator invoking this method. Do not use {@link IEmulator#getCPU()} or
	 * {@link IEmulator#getMemory()} to access the emulator's CPU/memory, use the <code>ICPU</code> / <code>IMemory</code>
	 * instances passed to this method call instead
	 * @param cpu 
	 * @param memory 
	 * @return number of CPU cycles the device consumed to handle the interrupt
	 */
	public int handleInterrupt(IEmulator emulator, ICPU cpu, IMemory memory);
}
