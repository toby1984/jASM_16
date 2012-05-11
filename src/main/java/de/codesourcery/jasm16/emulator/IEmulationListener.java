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

/**
 * 
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IEmulationListener
{
    public void afterReset(IEmulator emulator);
    
    public void afterMemoryLoad(IEmulator emulator, Address startAddress,int lengthInBytes);
    
    public void onBreakpoint(IEmulator emulator,BreakPoint breakpoint);
    
    /**
     * Invoked before executing the next command.
     * 
     * @param emulator
     */
    public void beforeExecution(IEmulator emulator);
    
    /**
     * Invoked after executing a command.
     * 
     * @param emulator
     * @param commandDuration duration (in cycles) of the last command or -1 on internal errors during command execution.
     */
    public void afterExecution(IEmulator emulator,int commandDuration);
}
