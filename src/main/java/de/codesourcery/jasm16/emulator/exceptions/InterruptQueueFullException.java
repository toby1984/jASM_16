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

/**
 * Thrown when the emulator's internal interrupt queue overflows
 * because interrupts have been disabled for too long and piled up.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class InterruptQueueFullException extends EmulationErrorException
{
    public InterruptQueueFullException(String message)
    {
        super(message);
    }
}
