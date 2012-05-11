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

import java.util.List;

import de.codesourcery.jasm16.Address;

public interface IEmulator
{
    public abstract void reset(boolean clearMemory);

    public abstract void stop();

    public abstract void start();

    public abstract void loadMemory(Address startingOffset, byte[] data);

    public abstract void calibrate();
    
    public ICPU getCPU();
    
    public IReadOnlyMemory getMemory();
    
    public void addEmulationListener(IEmulationListener listener);
    
    public void removeEmulationListener(IEmulationListener listener);    
    
    public void executeOneInstruction();
    
    public void skipCurrentInstruction();
    
    public void addBreakpoint(BreakPoint bp);
    
    public BreakPoint getBreakPoint(Address address);
    
    public void deleteBreakpoint(BreakPoint bp);

    public List<BreakPoint> getBreakPoints();
}