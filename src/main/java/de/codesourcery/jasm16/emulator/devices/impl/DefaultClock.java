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
package de.codesourcery.jasm16.emulator.devices.impl;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.HardwareInterrupt;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.utils.Misc;

public class DefaultClock implements IDevice
{
	private static final Logger LOG = Logger.getLogger(DefaultClock.class);
	
    private static final int INITIAL_TICKS_PER_SECOND = (int) Math.round( 1000.0 / 60.0d);	
	
    private final DeviceDescriptor DESC = new DeviceDescriptor("generic clock","jASM16 default clock" , 0x12d0b402,1, Constants.JASM16_MANUFACTURER );

    private volatile IEmulator emulator;
    
    private final ClockThread clockThread = new ClockThread();
    
    
    public void reset()
    {
        stopClock();
        clockThread.sleepDelay = INITIAL_TICKS_PER_SECOND;
        clockThread.irqEnabled = false;
        clockThread.irqMessage = 0;
        clockThread.tickCounter = 0;
    }
    
    protected final class ClockThread extends Thread {

        private volatile boolean isRunnable = false;
        
        private volatile int tickCounter = 0;
        private volatile boolean terminate = false;
        private volatile int sleepDelay=INITIAL_TICKS_PER_SECOND; // 60 HZ

        private volatile boolean irqEnabled = false;
        private volatile int irqMessage = 0;
        
        private final Object SLEEP_LOCK = new Object();

        public ClockThread() 
        {
            setDaemon(true);
            setName("hw-clock-thread");
        }

        
        public void run()
        {
            emulator.getOutput().println("Clock thread started.");
            while ( ! terminate ) 
            {
                while( ! isRunnable ) 
                {
                    if ( terminate ) {
                        break;
                    }                    
                    synchronized (SLEEP_LOCK) 
                    {
                        try {
                            SLEEP_LOCK.wait();
                            if ( terminate ) {
                                break;
                            }                            
                        } catch (InterruptedException e) { }
                    }
                }
                tickCounter = ( tickCounter+1 ) % 0x10000;
                if ( irqEnabled && emulator != null ) {
                    emulator.triggerInterrupt( new HardwareInterrupt( DefaultClock.this , irqMessage ) );
                }
                try {
                    Thread.sleep( sleepDelay );
                } catch (InterruptedException e) {
                }
            }
            emulator.getOutput().println("Default clock shutdown.");
        }
        
        public void terminate() {
            terminate=true;
            synchronized (SLEEP_LOCK) {
                SLEEP_LOCK.notifyAll();
            }
            
            while ( clockThread.isAlive() ) 
            {
                emulator.getOutput().println("Waiting for clock thread to terminate...");
                try {
                    Thread.sleep( 250 );
                } catch (InterruptedException e) { }
            }
        }
        
        public void setTicksPerSecond(int ticksPerSecond) 
        {
            stopClock();
            tickCounter = 0;
            sleepDelay = (int) Math.round( 1000.0 / ticksPerSecond ); 
        }
        
        public void startClock() {
            if ( ! isRunnable ) {
                isRunnable = true;
                synchronized (SLEEP_LOCK) 
                {
                    SLEEP_LOCK.notifyAll();
                }
            }
        }
        
        public void stopClock() {
            if ( isRunnable ) {
                isRunnable = false;
            }
        }        
    }

    protected synchronized void startClock() 
    {
        if ( ! clockThread.isAlive() ) {
            clockThread.start();
        } 
        clockThread.startClock();
    }
    
    protected synchronized void stopClock() 
    {
        if ( ! clockThread.isAlive() ) {
            return;
        }
        clockThread.stopClock();
    }  
    
    
    public void afterAddDevice(IEmulator emulator)
    {
        if ( this.emulator != null && this.emulator != emulator ) {
            throw new IllegalStateException("Clock "+this+" already associated with emulator "+emulator+" ?");
        }
        this.emulator = emulator;
    }

    
    public void beforeRemoveDevice(IEmulator emulator)
    {
        clockThread.terminate();
        this.emulator = null;
    }

    
    public DeviceDescriptor getDeviceDescriptor()
    {
        return DESC;
    }

    
    public int handleInterrupt(IEmulator emulator)
    {
        /*
         * Name: Generic Clock (compatible)
         * ID: 0x12d0b402
         * Version: 1
         * 
         * Interrupts do different things depending on contents of the A register:
         * 
         *  A | BEHAVIOR
         * ---+----------------------------------------------------------------------------
         *  0 | The B register is read, and the clock will tick 60/B times per second.
         *    | If B is 0, the clock is turned off.
         *  1 | Store number of ticks elapsed since last call to 0 in C register
         *  2 | If register B is non-zero, turn on interrupts with message B. If B is zero,
         *    | disable interrupts
         * ---+----------------------------------------------------------------------------
         * 
         * When interrupts are enabled, the clock will trigger an interrupt whenever it
         * ticks.     
         */      
        final int a = emulator.getCPU().getRegisterValue(Register.A);
        switch( a ) 
        {
            case 0: // The B register is read, and the clock will tick 60/B times per second. If B is 0, the clock is turned off.
                int b = emulator.getCPU().getRegisterValue(Register.B) & 0xffff;
                if ( b == 0 ) {
                    stopClock();
                    return 0;
                } 
                if ( b < 0 ) {
                    clockThread.setTicksPerSecond( 60 );                    
                } else if ( b > 60 ) {
                    clockThread.setTicksPerSecond( 1 );                    
                } else {
                    clockThread.setTicksPerSecond( (int) Math.round( 60.0 / b ) );
                }
                startClock();
                break;
            case 1:
                // Store number of ticks elapsed since last call to 0 in C register
                emulator.getCPU().setRegisterValue( Register.C , clockThread.tickCounter & 0xffff );
                break;
            case 2: 
                // If register B is non-zero, turn on interrupts with message B. If B is zero, disable interrupts.
                b = emulator.getCPU().getRegisterValue(Register.B) & 0xffff;
                if ( b == 0 ) {
                    clockThread.irqEnabled=false;
                } else {
                    clockThread.irqMessage = b;
                    clockThread.irqEnabled = true;
                    emulator.getOutput().println("Clock IRQs enabled with message "+Misc.toHexString( b ));
                }
                break;
            default:
                LOG.warn("handleInterrupt(): Clock received unknown interrupt msg "+Misc.toHexString( a ));
        }
        return 0;
    }

}
