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

import de.codesourcery.jasm16.emulator.devices.IDevice;

public final class DeviceErrorException extends EmulationErrorException {

    private final IDevice device;
    
    public DeviceErrorException(String message,IDevice device)
    {
    	this(message,device,null);
    }
    
    public DeviceErrorException(String message,IDevice device,Throwable t)
    {
        super(message,t);
        this.device = device;
    }    

    public IDevice getDevice() {
        return device;
    }
}   