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
package de.codesourcery.jasm16.ide;

import java.util.List;

import de.codesourcery.jasm16.emulator.EmulationOptions;
import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultKeyboard;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultScreen;

public class EmulatorFactory
{
	private EmulationOptions options = new EmulationOptions();
	
	public EmulatorFactory() {
	}
	
	public EmulationOptions getOptions() {
		return options;
	}
	
    public Emulator createEmulator() 
    {
    	return options.createEmulator();
    }        
    
    public DefaultScreen getScreen(IEmulator emulator) {
    	List<IDevice> result = emulator.getDevicesByDescriptor( DefaultScreen.DESC );
    	if ( result.isEmpty() ) {
    		throw new RuntimeException("Internal error, found no default screen?");
    	}
    	if ( result.size() > 1 ) {
    		throw new RuntimeException("Internal error, found more than one default screen?");
    	}
    	return (DefaultScreen) result.get(0);
    }
    
    public DefaultKeyboard getKeyboard(IEmulator emulator)
    {
    	List<IDevice> result = emulator.getDevicesByDescriptor( DefaultKeyboard.DESC );
    	if ( result.isEmpty() ) {
    		throw new RuntimeException("Internal error, found no default keyboard ?");
    	}
    	if ( result.size() > 1 ) {
    		throw new RuntimeException("Internal error, found more than one default keyboard ?");
    	}
    	return (DefaultKeyboard) result.get(0);    	
    }    
}