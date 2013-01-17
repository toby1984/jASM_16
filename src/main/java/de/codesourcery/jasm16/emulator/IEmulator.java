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
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.devices.IInterrupt;
import de.codesourcery.jasm16.emulator.exceptions.DeviceErrorException;
import de.codesourcery.jasm16.emulator.exceptions.MemoryProtectionFaultException;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion;
import de.codesourcery.jasm16.emulator.memory.IReadOnlyMemory;
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
    
    /**
     * Adds a device to this emulator.
     * 
     * <p> This method will invoke {@link IDevice#afterAddDevice(IEmulator)} after
     * the device has been registered with the emulator. If this call fails,
     * the device will be removed from the emulator again before propagating
     * the exception.</p>.
     * 
     * @param device
     * @return the hardware slot number where the device has been added
     * @throws DeviceErrorException if the {@link IDevice#afterAddDevice(IEmulator)} call failed
     */
    public int addDevice(IDevice device) throws DeviceErrorException;
    
    /**
     * Invokes a callback , passing mutable instances of this emulator's CPU and memory.
     * 
     * <p>The executing callback has exclusive access to the emulator's CPU and memory , 
     * meaning all updates are atomic and the CPU and memory state cannot be changed concurrently
     * by other threads.</p>
     * 
     * @param invoker
     * @return
     */
    public <T> T doWithEmulator(IEmulatorInvoker<T> invoker);
    
    /**
     * Returns a list of devices with matching hardware IDs , versions and manufacturer IDs.
     * 
     * @param desc
     * @return
     * @see DeviceDescriptor#matches(DeviceDescriptor)
     */
    public List<IDevice> getDevicesByDescriptor(DeviceDescriptor desc);
    
    /**
     * Adds a new device, replacing any existing device with the same
     * device descriptor.
     *
     *  <p>Devices are replaced if their hardware ID , manufacturer ID and version match.</p>
     *  
     * @param device
     * @return
     * @throws DeviceErrorException
     * @throws IllegalStateException if more than one device with matching device descriptor are already registered,
     */
    public int addOrReplaceDevice(IDevice device) throws DeviceErrorException; 
    
    public void addEmulationListener(IEmulationListener listener);

    public void breakpointChanged(Breakpoint breakpoint);

    public void calibrate();
    
    /**
     * Enable/disable instruction memory write protection.
     * 
     * <p>When enabled, the emulator will keep track of all memory
     * regions that contain executable code and will stop
     * emulation with {@link MemoryProtectionFaultException} 
     * whenever the program tries to write to one of these locations</p>.
     *
     * <p>It's obviously not a good idea to enable this feature when running self-modifying code.</p>
     * 
     * @param enabled
     * @see MemoryProtectionFaultException
     * @see #isMemoryProtectionEnabled()
     */
    public void setMemoryProtectionEnabled(boolean enabled);
    
    /**
     * Check whether the emulation is currently running with memory-protection enabled.
     *  
     * @return
     * @see #setMemoryProtectionEnabled(boolean)
     */
    public boolean isMemoryProtectionEnabled();
    
    /**
     * Disposes this emulator.
     * 
     * <p>This method terminates the emulator and releases all associated allocated resources. After this method returns,
     * the emulator is no longer in a usable state.</p>
     * <p>Calling this method more than once does no harm.</p>
     */
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
    
    /**
     * Returns a read-only instance of this emulator's CPU.
     * 
     * <p>If you want to change the CPU's state, you need to use {@link #doWithEmulator(IEmulatorInvoker)}</p>.
     * 
     * @return
     */
    public IReadOnlyCPU getCPU();
    
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
    
    /**
     * Returns a read-only instance of this emulator's memory.
     * 
     * <p>If you want to write to memory, you need to use {@link #doWithEmulator(IEmulatorInvoker)}</p>.
     * 
     * @return
     */    
    public IReadOnlyMemory getMemory();
    
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
    
    /**
     * Remove a registered device.
     * 
     * <p>If the device is not registered , does nothing.</p>
     * 
     * @param device
     * @throws DeviceErrorException if the {@link IDevice#beforeRemoveDevice(IEmulator)} call failed     
     */
    public void removeDevice(IDevice device) throws DeviceErrorException;
    
    public void removeEmulationListener(IEmulationListener listener);     
    
    /**
     * Removes all registered emulation listeners <b>except</b> listeners
     * that belong to hardware devices.
     *  
     * @see IEmulationListener#belongsToHardwareDevice()  
     */
    public void removeAllEmulationListeners();
    
    // emulator control
    /**
     * Reset the emulator,optionally clearing the memory as well.
     * 
     * <p>This method stops the emulator and resets CPU,all devices and (optionally) the memory. Note that calling this method will also delete all one-shot breakpoints.</p>     
     * 
     * @param clearMemory whether the memory should be cleared
     */
    public void reset(boolean clearMemory);
    
    public void skipCurrentInstruction();
    
    public void start();

    /**
     * Stop the emulation.
     * 
     * <p>Note that calling this method will also delete all one-shot breakpoints.</p>
     * @return <code>true</code> if the emulation was currently running and has been stopped.
     */
    public boolean stop();
    
    /**
     * Triggers an interrupt.
     * 
     * <p>If the interrupt queue is empty, the interrupt will be handled
     * <b>after</b> the next regular instruction execution.If the interrupt queue
     * is not yet full ( less than 256 interrupts queued), the interrupt
     * will be added to the queue. If interrupts are currently disabled,
     * this method will return <code>false</code> and do nothing.</p>
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
	
	public void setOutput(ILogger logger);
	
	public ILogger getOutput();
	
	public void setIgnoreAccessToUnknownDevices(boolean yesNo);
}