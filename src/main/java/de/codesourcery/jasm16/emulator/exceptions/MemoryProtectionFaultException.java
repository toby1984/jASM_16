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
package de.codesourcery.jasm16.emulator.exceptions;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.Emulator;

/**
 * Thrown when the emulator is running with {@link Emulator#checkMemoryWrites} and
 * the program tries to write memory that was already part of the execution path (and 
 * thus contains program code and not data).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class MemoryProtectionFaultException extends EmulationErrorException {

    private final Address offendingWriteAddress;

    public MemoryProtectionFaultException(String message,Address offendingWriteAddress)
    {
        super(message);
        this.offendingWriteAddress = offendingWriteAddress;
    }

    /**
     * Returns the address the program was trying to write to.
     * @return
     */
    public Address getOffendingWriteAddress()
    {
        return offendingWriteAddress;
    }
}