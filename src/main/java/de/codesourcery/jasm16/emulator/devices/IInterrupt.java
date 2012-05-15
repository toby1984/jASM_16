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

/**
 * A hardware or software interrupt.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see HardwareInterrupt 
 * @see SoftwareInterrupt
 */
public interface IInterrupt {

    /**
     * Returns this interrupt's message.
     * 
     * @return interrupt message (only the lower 16 bit are used)  
     */
	public int getMessage();
	
	/**
	 * Returns whether this interrupt was triggered by software.
	 * 
	 * @return
	 */
	public boolean isSoftwareInterrupt();
	
    /**
     * Returns whether this interrupt was triggered by hardware.
     * 
     * @return
     */	
	public boolean isHardwareInterrupt();
}
