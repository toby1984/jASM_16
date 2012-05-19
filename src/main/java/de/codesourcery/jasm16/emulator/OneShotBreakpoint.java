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
 * A special "invisible" breakpoint that automatically gets deleted when
 * {@link IEmulator#stop()} is executed.
 * 
 * <p>This breakpoint does <b>NOT</b> trigger <b>ANY</b> 
 * breakpoint-related methods of {@link IEmulationListener}.
 *
 * <p>At any address, at most one regular breakpoint and one one-shot breakpoint may be set.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class OneShotBreakpoint extends Breakpoint {

	public OneShotBreakpoint(Address address) {
		super(address);
	}

    public boolean isOneShotBreakpoint() {
    	return true;
    }	
}