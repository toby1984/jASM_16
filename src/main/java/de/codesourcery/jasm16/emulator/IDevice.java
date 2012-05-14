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

public interface IDevice {

	/**
	 * Invoked after this device has been added to an emulator.
	 * @param emulator
	 */
	public void afterAddDevice(IEmulator emulator);
	
	/**
	 * Invoked before this device is removed from
	 * an emulator.
	 * 
	 * @param emulator
	 */
	public void beforeRemoveDevice(IEmulator emulator);
	
	/**
	 * Returns this devices hardware ID.
	 * 
	 * @return 32-bit value (only the lower 32 bits are used) 
	 */
	public long getHardwareID();
	
	/**
	 * Returns this devices hardware version.
	 * 
	 * @return 16-bit value (only the lower 16 bits are used)
	 */
	public int getHardwareVersion();
	
	/**
	 * Returns this devices manufacturer ID.
	 * 
	 * @return 32-bit value (only the lower 32 bits are used) 
	 */
	public long getManufacturer();
	
	/**
	 * Handle a software interrupt triggered by the application.
	 * @param emulator
	 */
	public void handleInterrupt(IEmulator emulator);
}
