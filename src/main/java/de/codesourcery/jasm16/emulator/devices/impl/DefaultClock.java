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
	
    private final DeviceDescriptor DESC = new DeviceDescriptor("generic clock","jASM16 default clock" , 0x12d0b402,1, Constants.JASM16_MANUFACTURER );

    private volatile IEmulator emulator;
    
    private final ClockThread clockThread = new ClockThread();
    
    protected final class ClockThread extends Thread {

        private volatile boolean isRunnable = false;
        
        private volatile int tickCounter = 0;
        private volatile boolean terminate = false;
        private volatile int sleepDelay=(int) Math.round( 1000.0 / 60.0d); // 60 HZ

        private volatile boolean irqEnabled = false;
        private volatile int irqMessage = 0;
        
        private final Object SLEEP_LOCK = new Object();

        public ClockThread() 
        {
            setDaemon(true);
            setName("hw-clock-thread");
        }

        @Override
        public void run()
        {
            System.out.println("Clock thread started.");
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
            System.out.println("Default clock shutdown.");
        }
        
        public void terminate() {
            terminate=true;
            synchronized (SLEEP_LOCK) {
                SLEEP_LOCK.notifyAll();
            }
            
            while ( clockThread.isAlive() ) 
            {
                System.out.println("Waiting for clock thread to terminate...");
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
        if ( clockThread == null || ! clockThread.isAlive() ) {
            return;
        }
        clockThread.stopClock();
    }  
    
    @Override
    public void afterAddDevice(IEmulator emulator)
    {
        if ( this.emulator != null && this.emulator != emulator ) {
            throw new IllegalStateException("Clock "+this+" already associated with emulator "+emulator+" ?");
        }
        this.emulator = emulator;
    }

    @Override
    public void beforeRemoveDevice(IEmulator emulator)
    {
        clockThread.terminate();
        this.emulator = null;
    }

    @Override
    public DeviceDescriptor getDeviceDescriptor()
    {
        return DESC;
    }

    @Override
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
                    System.out.println("Clock IRQs enabled with message "+Misc.toHexString( b ));
                }
                break;
            default:
                LOG.warn("handleInterrupt(): Clock received unknown interrupt msg "+Misc.toHexString( a ));
        }
        return 0;
    }

}
