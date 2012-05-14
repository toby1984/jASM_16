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
    public void addBreakpoint(Breakpoint bp);
    
    public void addDevice(IDevice device);   
    
    public void addEmulationListener(IEmulationListener listener);

    public void breakpointChanged(Breakpoint breakpoint);

    public void calibrate();
    
    public boolean isCalibrating();

    public void deleteBreakpoint(Breakpoint bp);
    
    public void executeOneInstruction();
    
    public Breakpoint getBreakPoint(Address address);

    public List<Breakpoint> getBreakPoints();
    
    public ICPU getCPU();
    
    public void setRunAtRealSpeed(boolean yesNo);
    
    public boolean isRunAtRealSpeed();
    
    public List<IDevice> getDevices();
    
    public IReadOnlyMemory getMemory();
    
    public void loadMemory(Address startingOffset, byte[] data);
    
    /**
     * Maps main memory to a specific region.
     * 
     * @param region
     * @see #unmapRegion(IMemoryRegion)
     * @see MainMemory#mapRegion(IMemoryRegion)
     */
    public void mapRegion(IMemoryRegion region);
    
    public void removeDevice(IDevice device);
    
    public void removeEmulationListener(IEmulationListener listener);     
    
    // emulator control
    public void reset(boolean clearMemory);
    
    public void skipCurrentInstruction();
    
    public void start();

    public void stop();
    
    /**
     * Triggers an interrupt.
     * 
     * <p>If the interrupt queue is empty, the interrupt will be handled
     * before the next regular instruction execution.If the interrupt queue
     * is not yet full ( less than 256 interrupts queued), the interrupt
     * will be added to the queue. If interrupts are currently disabled,
     * this method will return <code>false</code>.</p>
     * @param interrupt
     * @return <code>false</code> if interrupts are disabled (IA is set to 0),
     * otherwise <code>true</code>
     */
    public boolean triggerInterrupt(IInterrupt interrupt);      
    
    /**
     * Replaces a mapped memory region with plain (unmapped) main-memory.
     * 
     * @param region
     * @see MainMemory#unmapRegion(IMemoryRegion)
     */
    public void unmapRegion(IMemoryRegion region);

	public boolean isCalibrated();    
}