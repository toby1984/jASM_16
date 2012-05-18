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

import java.io.PrintStream;
import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.devices.IInterrupt;
import de.codesourcery.jasm16.emulator.memory.IMemory;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion;
import de.codesourcery.jasm16.emulator.memory.MainMemory;

/**
 * DCPU-16 emulator abstraction.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IEmulator
{
    public enum EmulationSpeed 
    {
        /**
         * Run the emulation as fast as possible.
         */        
        MAX_SPEED,
        /**
         * Try to run emulation at the DCPU-16s native speed (~100 kHz).
         */
        REAL_SPEED;
    }
    
    public void addBreakpoint(Breakpoint bp);
    
    public void addDevice(IDevice device);   
    
    public void addEmulationListener(IEmulationListener listener);

    public void breakpointChanged(Breakpoint breakpoint);

    public void calibrate();
    
    public void dispose();
    
    public void deleteBreakpoint(Breakpoint bp);
    
    public void executeOneInstruction();
    
    /**
     * Check it's ok to invoke {@link #stepReturn()} because
     * the PC is currently pointing at a JSR instruction.
     *  
     * @return
     */
	public boolean canStepReturn();
	
	/**
	 * Executes a subroutine and stop at the next instruction after returning.
	 *   
	 * <p>Use {@link #canStepReturn()} to check whether the PC is currently pointing
	 * at a JSR instruction.</p>
	 * 
	 * @throws IllegalStateException if the PC is not currently at a JSR instruction
	 * @see #canStepReturn()
	 */
	public void stepReturn() throws IllegalStateException;
    
    public Breakpoint getBreakPoint(Address address);

    public List<Breakpoint> getBreakPoints();
    
    public ICPU getCPU();
    
    /**
     * Returns a textual description of the last
     * emulation error.
     * 
     * <p>Note that this error message gets cleared every time 
     * {@link #start()} , {@link #stop()} ,{@link #reset(boolean)} or {@link #loadMemory(Address, byte[])}
     * are called.
     * </p>
     * @return error message or <code>null</code>
     */
    public Throwable getLastEmulationError();
    
    public void setEmulationSpeed(EmulationSpeed speed);
    
    public EmulationSpeed getEmulationSpeed();
    
    public List<IDevice> getDevices();
    
    public IMemory getMemory();
    
    /**
     * Clears the memory and populates it with data from a byte array.
     * 
     * <p>This method will {@link #stop()} the emulator before updating the memory.</p>
     * 
     * <p>Note that since this method calls {@link #stop()} it 
     * will also delete all one-shot breakpoints.</p>
     *      
     * @param startingOffset
     * @param data
     */
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
    /**
     * Reset the emulator,optionally clearing the memory as well.
     * 
     * <p>Note that calling this method will also delete all one-shot breakpoints.</p>     
     * 
     * @param clearMemory
     */
    public void reset(boolean clearMemory);
    
    public void skipCurrentInstruction();
    
    public void start();

    /**
     * Stop the emulation.
     * 
     * <p>Note that calling this method will also delete all one-shot breakpoints.</p>
     */
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
	
	public void setOutput(PrintStream out);
	
	public PrintStream getOutput();
}