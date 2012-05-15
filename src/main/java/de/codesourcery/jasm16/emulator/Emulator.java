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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.ast.OperandNode.OperandPosition;
import de.codesourcery.jasm16.disassembler.DisassembledLine;
import de.codesourcery.jasm16.disassembler.Disassembler;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.devices.IInterrupt;
import de.codesourcery.jasm16.emulator.devices.SoftwareInterrupt;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion;
import de.codesourcery.jasm16.emulator.memory.IReadOnlyMemory;
import de.codesourcery.jasm16.emulator.memory.MainMemory;
import de.codesourcery.jasm16.emulator.memory.MemUtils;
import de.codesourcery.jasm16.utils.Misc;

/**
 * DCPU-16 emulator.
 * 
 * @author tobias.gierke@voipfuture.com
 */
public class Emulator implements IEmulator {

	private static final Logger LOG = Logger.getLogger(Emulator.class);

	private static final boolean DEBUG_LISTENER_PERFORMANCE = false;

	private final IdentityHashMap<IEmulationListener,Long> listenerPerformance = 
			new IdentityHashMap<IEmulationListener,Long>();

	private final ClockThread clockThread;

	private final ListenerHelper listenerHelper = new ListenerHelper();

	private static final AtomicLong cmdId = new AtomicLong(0);
	
	protected static final class Command {
	
	    private final long id = cmdId.incrementAndGet();
	    
	    private final boolean isStopCommand;
	    
	    private final boolean requiresACK; 
	    
        public static Command stopCommandWithoutACK() {
            return new Command(true,false);
        }
        
	    public static Command stopCommand() {
	        return new Command(true,true);
	    }
	    
        public static Command startCommand() {
            return new Command(false,true);
        }	    
	    
	    protected Command(boolean isStopCommand,boolean requiresACK) {
	        this.isStopCommand=isStopCommand;
	        this.requiresACK = requiresACK;
	    }
	    
	    public boolean requiresACK() {
	        return requiresACK;
	    }
	    
	    public boolean isStopCommand()
        {
            return isStopCommand;
        }
	    
        public boolean isStartCommand()
        {
            return ! isStopCommand();
        }   	    
	    
	    public long getId()
        {
            return id;
        }
	    
	    @Override
	    public String toString()
	    {
	        if ( isStopCommand ) {
	            return "STOP( "+id+" )";
	        } 
	        return "START( "+id+" )";
	    } 
	    
	    
	}
	
	/**
	 * Helper to manage IEmulationListeners. 
	 */
	protected final class ListenerHelper {

		// @GuardedBy( emuListeners )
		private final List<IEmulationListener> emuListeners = new ArrayList<IEmulationListener>();

		// @GuardedBy( emuListeners )
		private final List<IEmulationListener> beforeCommandExecListeners = new ArrayList<IEmulationListener>();

		// @GuardedBy( emuListeners )
		private final List<IEmulationListener> continuousModeBeforeCommandExecListeners = new ArrayList<IEmulationListener>();


		// @GuardedBy( emuListeners )
		private final List<IEmulationListener> afterCommandExecListeners = new ArrayList<IEmulationListener>();    

		// @GuardedBy( emuListeners )
		private final List<IEmulationListener> continuousModeAfterCommandExecListeners = new ArrayList<IEmulationListener>();    

		private final IEmulationListenerInvoker BEFORE_COMMAND_INVOKER = new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.beforeCommandExecution( emulator );
			}
		}; 

		public void addEmulationListener(IEmulationListener listener)
		{
			if (listener == null) {
				throw new IllegalArgumentException("listener must not be NULL.");
			}
			synchronized (emuListeners) 
			{
				emuListeners.add( listener );
				if ( listener.isInvokeBeforeCommandExecution() ) {
					beforeCommandExecListeners.add( listener );
					if ( listener.isInvokeAfterAndBeforeCommandExecutionInContinuousMode() ) {
						continuousModeBeforeCommandExecListeners.add( listener );
					}
				}
				if ( listener.isInvokeAfterCommandExecution() ) 
				{
					afterCommandExecListeners.add( listener );
					if ( listener.isInvokeAfterAndBeforeCommandExecutionInContinuousMode() ) {
						continuousModeAfterCommandExecListeners.add( listener );
					}
				}
			}
		}        

		public void removeEmulationListener(IEmulationListener listener)
		{
			if (listener == null) {
				throw new IllegalArgumentException("listener must not be NULL.");
			}
			synchronized (emuListeners) {
				emuListeners.remove( listener );
				beforeCommandExecListeners.remove( listener );
				continuousModeBeforeCommandExecListeners.remove( listener );
				continuousModeAfterCommandExecListeners.remove( listener );
				afterCommandExecListeners.remove( listener );
			}        
		}

		public void notifyListeners(IEmulationListenerInvoker invoker) 
		{
			notifyListeners( invoker , emuListeners );
		}

		public void invokeAfterCommandExecutionListeners(boolean continousMode,final int executedCommandDuration) 
		{
			final IEmulationListenerInvoker invoker = new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.afterCommandExecution( emulator , executedCommandDuration );
				}
			};

			if ( continousMode ) {
				notifyListeners( invoker , continuousModeAfterCommandExecListeners );
			} else {
				notifyListeners( invoker , afterCommandExecListeners );
			}
		}

		public void invokeBeforeCommandExecutionListeners(boolean continousMode) 
		{
			if ( continousMode ) {
				notifyListeners( BEFORE_COMMAND_INVOKER , continuousModeBeforeCommandExecListeners );
			} else {        	
				notifyListeners( BEFORE_COMMAND_INVOKER , beforeCommandExecListeners );
			}
		}        

		public void notifyListeners(IEmulationListenerInvoker invoker,List<IEmulationListener> listeners) 
		{
			final List<IEmulationListener> copy;
			synchronized( emuListeners ) 
			{
				if ( listeners.isEmpty() ) {
					return;
				}
				copy = new ArrayList<IEmulationListener>( listeners );
			}    	
			if ( DEBUG_LISTENER_PERFORMANCE ) 
			{
				for ( IEmulationListener l : copy) 
				{
					long execTime = -System.currentTimeMillis();
					try {
						invoker.invoke( Emulator.this , l );
					}
					catch(Exception e) {
						e.printStackTrace();
					} finally {
						execTime += System.currentTimeMillis();
					}
					final Long existing = listenerPerformance.get( l );
					if ( existing == null ) {
						listenerPerformance.put( l , execTime );
					} else {
						listenerPerformance.put( l , existing.longValue() + execTime );
					}
				}         	
			} else {
				for ( IEmulationListener l : copy) 
				{
					try {
						invoker.invoke( Emulator.this , l );
					}
					catch(Exception e) {
						e.printStackTrace();
					}
				}       
			}
		}        
	}

	private final AtomicLong lastStart = new AtomicLong(0);
	
	private volatile int cycleCountAtLastStart=0;
	private final AtomicLong lastStop = new AtomicLong(0);
	private volatile int cycleCountAtLastStop=0;

	// ============ BreakPoints =======

	// @GuardedBy( breakpoints )
	private final Map<Address,Breakpoint> breakpoints = new HashMap<Address,Breakpoint>(); 

	// ============ Memory ============

	// memory needs to be thread-safe since the emulation runs in a separate thread
	// and UI threads may access the registers concurrently    
	private final MainMemory memory = new MainMemory( 65536 );
	
	// ========= devices ===========
	
	// @GuardedBy( devices )
	private final List<IDevice> devices = new ArrayList<IDevice>();

	// ============ CPU =============== 

	// a,b,c,x,y,z,i,j
	// all CPU registers needs to be thread-safe since the emulation runs in a separate thread
	// and UI threads may access the registers concurrently 
	
	private static final int REGISTER_A = 0;
	private static final int REGISTER_B = 1;
	private static final int REGISTER_C = 2;
	private static final int REGISTER_X = 3;
	private static final int REGISTER_Y = 4;
	private static final int REGISTER_Z = 5;
	private static final int REGISTER_I = 6;
	private static final int REGISTER_J = 7;
	
	private final AtomicIntegerArray registers=new AtomicIntegerArray(8);

	private volatile Address pc = Address.wordAddress( 0 );
	
	// used to print a backtrace when the CPU reached an unknown upcode
	private volatile Address previousPC = Address.wordAddress( 0 );
	
	private volatile Address sp = Address.wordAddress( 0 );
	private volatile int ex;

	private volatile Address interruptAddress;

	private volatile boolean stoppedBecauseOfError = false;
	private volatile EmulationSpeed emulationSpeed = EmulationSpeed.MAX_SPEED;
	private volatile boolean queueInterrupts = false;
	
	// @GuardedBy( interruptQueue )
	private volatile IInterrupt currentInterrupt = null;
	private final List<IInterrupt> interruptQueue = new ArrayList<IInterrupt>();

	private volatile int currentCycle = 0;

	private final ICPU cpuAdaptor = new ICPU() {

		@Override
		public int[] getCommonRegisters()
		{
			final int len = registers.length();
			final int[] result = new int[ len ];
			// TODO: Not quite thread-safe , would require a global lock on the whole array...
			for ( int i = 0 ; i < len ; i++ ) {
				result[i] = registers.get(i);
			}
			return result;
		}
		
		@Override
		public List<IInterrupt> getInterruptQueue() {
			synchronized( interruptQueue ) {
				return new ArrayList<IInterrupt>( interruptQueue );
			}
		}
		
		public void setRegisterValue(Register reg, int value) 
		{
			switch( reg ) 
			{
				case A:
					registers.set(REGISTER_A , value );
					return;
				case B:
					registers.set(REGISTER_B , value );
					return;				
				case C:
					registers.set(REGISTER_C , value );
					return;				
				case X:
					registers.set(REGISTER_X , value );
					return;				
				case Y:
					registers.set(REGISTER_Y , value );
					return;				
				case Z:
					registers.set(REGISTER_Z , value );
					return;				
				case I:
					registers.set(REGISTER_I , value );
					return;				
				case J:
					registers.set(REGISTER_J , value );
					return;				
			}
			throw new IllegalArgumentException("Unsupported register "+reg);			
		}

		@Override
		public int getRegisterValue(Register reg) 
		{
			// a,b,c,x,y,z,i,j
			switch( reg ) 
			{
			case A:
				return registers.get(REGISTER_A);
			case B:
				return registers.get(REGISTER_B);
			case C:
				return registers.get(REGISTER_C);
			case X:
				return registers.get(REGISTER_X);
			case Y:
				return registers.get(REGISTER_Y);
			case Z:
				return registers.get(REGISTER_Z);
			case I:
				return registers.get(REGISTER_I);
			case J:
				return registers.get(REGISTER_J);				
			case EX:
				return ex;
			case PC:
				return pc.getValue();
			case SP:
				return sp.getValue();
			default:
				throw new RuntimeException("Internal error, unhandled register "+reg);
			}
		}        

		@Override
		public Address getPC()
		{
			return pc;
		}

		@Override
		public Address getSP()
		{
			return sp;
		}

		@Override
		public int getEX()
		{
			return ex;
		}

		@Override
		public Address getInterruptAddress()
		{
			return interruptAddress;
		}

		@Override
		public int getCurrentCycleCount()
		{
			return currentCycle;
		}

		public void setQueueInterrupts(boolean yesNo) {
			queueInterrupts = yesNo;
		}
		
		public boolean isQueueInterrupts() {
			return queueInterrupts;
		}
		
		@Override
		public boolean interruptsEnabled() {
			return interruptAddress != null && interruptAddress.getValue() != 0;
		}

	};

	/* (non-Javadoc)
	 * @see de.codesourcery.jasm16.emulator.IEmulator#reset()
	 */
	@Override
	public void reset(boolean clearMemory)
	{
		stop(false);
		
		currentCycle = 0;
		queueInterrupts = false;
		interruptAddress = Address.wordAddress( 0 );
		pc = Address.wordAddress( 0 );
		sp = Address.wordAddress( 0 );
		ex = 0;

		final int regCount = registers.length();
		for ( int i = 0 ; i < regCount ; i++ ) {
			registers.set( i, 0 );
		}
		if ( clearMemory ) 
		{
			memory.clear();
		}

		// notify listeners
		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.afterReset( emulator );                
			}
		});
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.jasm16.emulator.IEmulator#stop()
	 */
    @Override
    public void stop() {
        stop( false );
    }
    
	protected void stop(boolean stopBecauseOfError) 
	{
	    this.stoppedBecauseOfError = stopBecauseOfError;
	    
		clockThread.stopSimulation();
		
		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.afterContinuousExecution( emulator );
			}
		});         
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.jasm16.emulator.IEmulator#start()
	 */
	@Override
	public void start() 
	{
		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.beforeContinuousExecution( emulator );
			}
		});      	
		clockThread.startSimulation();	
	}

	public Emulator() {
		clockThread = new ClockThread();
		clockThread.start();
	}

	public class ClockThread extends Thread {

		private volatile boolean isRunnable = false;

		private final BlockingQueue<Command> cmdQueue = new ArrayBlockingQueue<Command>(1);
		private final BlockingQueue<Long> ackQueue = new ArrayBlockingQueue<Long>(1);

		private volatile double adjustmentFactor = 1.0d;
		private volatile int oneCycleDelay = -1;
		
		private int dummy;		

		public ClockThread() 
		{
			setName("emulation-clock-thread");
			setDaemon(true);
		}

		public synchronized void startSimulation() 
		{
			if ( isRunnable == false ) 
			{
			    isRunnable = true;
			    sendToClockThread( Command.startCommand() );
			}
		}		
		
        public synchronized void stopSimulation() 
        {
            if ( isRunnable == true ) 
            {               
                isRunnable = false;
                if ( Thread.currentThread() == clockThread ) { 
                    sendToClockThread( Command.stopCommandWithoutACK() ); // no point in sending an ACK since the clock thread itself triggered the stop()
                } else {
                    sendToClockThread( Command.stopCommand() );
                }
            }
        }    		
		
		private void sendToClockThread(Command cmd) 
		{
		    safePut( cmdQueue , cmd );
		    if ( ! cmd.requiresACK() ) { // don't wait , we'll never receive this one anyway
		        return;
		    }
            do 
            {
                final Long cmdId = safeTake( ackQueue );
                if ( cmdId.longValue() == cmd.getId() ) 
                {
                    return;
                }
                safePut( ackQueue , cmdId );                
            } while ( true );  		    
		}
		
		public double getRuntimeInSeconds() 
		{
		    final long start = lastStart.get();
		    final long stop = lastStop.get();
		    
			if ( start != 0 && stop != 0 )
			{
				final long delta = stop - start;
				if ( delta >= 0) {
					return delta / 1000.0d;
				}
				LOG.error("getRuntimeInSeconds(): Negative runtime ? "+delta+" ( lastStart: "+start+" / lastStop: "+stop,new Exception());
				throw new RuntimeException("Unreachable code reached");				
			} else if ( start != 0 ) {
				return ( (double) System.currentTimeMillis() - (double) start) / 1000.0d;
			} else if ( start == 0 && stop == 0 ) {
				return 0;
			}
			LOG.error("getRuntimeInSeconds(): Unreachable code reached");
			throw new RuntimeException("Unreachable code reached");
		}     		

		protected final double measureDelayLoopInNanos() 
		{
			double averages = 0.0d;
			int count = 0;
			for ( int i = 0 ; i < 10 ; i++ ) {
				averages += measureDelayLoop();
				count++;
			}
			return (averages / count);
		}

		protected final double measureDelayLoop() 
		{
			final int oldValue = clockThread.oneCycleDelay;

			final int LOOP_COUNT = 1000000;
			oneCycleDelay = LOOP_COUNT;

			final long nanoStart = System.nanoTime();

			for ( int j = oneCycleDelay ; j > 0 ; j-- ) {
				dummy = ((dummy*2+j*2)/3)/(dummy*7+j);
			}

			long durationNanos = ( System.nanoTime() - nanoStart );
			if ( durationNanos < 0) {
				durationNanos = -durationNanos;
			}
			oneCycleDelay = oldValue;
			return ( (double) durationNanos / (double) LOOP_COUNT);
		}	    

		public String getEstimatedClockSpeed() 
		{
			final double clockRate = getCyclesPerSecond();

			final double delta = clockRate-100000.0;
			final double deviationPercentage = 100.0d*( delta / 100000.0 );

			final String sign = deviationPercentage > 0 ? "+" : "";
			final String deviation = " ( "+sign+deviationPercentage+" % )";

			if ( clockRate == -1.0d ) {
				return "<cannot calculate clock rate>";
			}
			if ( clockRate < 1000 ) {
				return clockRate+" Hz"+deviation;
			} else if ( clockRate < 100000) {
				return (clockRate/1000)+" kHz"+deviation;
			} else if ( clockRate < 1000000000 ) {
				return (clockRate/1000000)+" MHz"+deviation;
			}
			return (clockRate/1000000000)+" GHz"+deviation;         
		} 	    

		public double getCyclesPerSecond() 
		{
			if ( lastStart.get() != 0 )
			{
			    final double runtime = getRuntimeInSeconds();
			    if ( runtime != 0.0d ) {
			        return ((double)cycleCountAtLastStop - (double) cycleCountAtLastStart ) / getRuntimeInSeconds();
			    }
			}
			return 0.0d;
		}
		
        private Command waitForCommand(boolean expectingStartCommand) 
        {
            while ( true ) 
            {
                final Command result = safeTake( cmdQueue );
                
                if ( ( expectingStartCommand && result.isStartCommand() ) ||
                     ( ! expectingStartCommand && result.isStopCommand() ) ) 
                {
                    return result;
                }
                acknowledgeCommand( result );
            }
        }		

		private void acknowledgeCommand(Command cmd) 
		{
		    if ( cmd.requiresACK() ) {
		        safePut( ackQueue , cmd.getId() );
		    }
		}
		
        private Command waitForStopCommand()
        {
           return waitForCommand(false);
        }		

        private Command waitForStartCommand()
        {
           return waitForCommand(true);
        }
        
		@Override
		public void run() {

		    stoppedBecauseOfError = false;
		    
		    Command cmd = waitForStartCommand();

			lastStart.set( System.currentTimeMillis() );
			cycleCountAtLastStart=currentCycle;
            
			acknowledgeCommand( cmd );			

			while ( true ) 
			{
				if ( isRunnable == false ) 
				{
					lastStop.set( System.currentTimeMillis() );
					cycleCountAtLastStop=currentCycle;

					if ( DEBUG_LISTENER_PERFORMANCE ) 
					{
						for ( Map.Entry<IEmulationListener,Long> entry : listenerPerformance.entrySet() ) {
							System.out.println( entry.getKey()+" = "+entry.getValue()+" millis" );	
						}
					}
					
					System.out.println("Emulator stopped.");
					System.out.println( "Executed cycles: "+(cycleCountAtLastStop-cycleCountAtLastStart) +" ( in "+getRuntimeInSeconds()+" seconds )");
					System.out.println("Estimated clock rate: "+getEstimatedClockSpeed() );

					acknowledgeCommand( waitForStopCommand() );

					cmd = waitForStartCommand();
					
					stoppedBecauseOfError = false;					
					lastStart.set( System.currentTimeMillis() );   
					cycleCountAtLastStart=currentCycle;
					
					acknowledgeCommand(cmd);
				}
				
				final int durationInCycles = internalExecuteOneInstruction();
				if ( emulationSpeed == EmulationSpeed.REAL_SPEED ) 
				{
				    if ( ( currentCycle % 10000 ) == 0 ) {
				        final double cyclesPerSecond = (currentCycle-cycleCountAtLastStart) / ( ( System.currentTimeMillis() - lastStart.get() ) / 1000d);
    				    // NOTE: 0.1 is a magic number determined on my i7 with JDK1.7.3 (32-bit) that accounts for the overhead of the if () condition and 
				        //       calculation of the actual delay loop iteration count
				        adjustmentFactor = ( cyclesPerSecond / 100000.0d ) - 0.1d; ; 
				    }
				    int j = (int) (oneCycleDelay*durationInCycles*adjustmentFactor);
					for (  ; j > 0 ; j-- ) {
						dummy = ((dummy*2+j*2)/3)/(dummy*7+j);
					}					
				}
			}
		} // END: ClockThread

	}

	/**
	 * 
	 * @return number of DCPU-16 cycles the command execution took 
	 */
	protected int internalExecuteOneInstruction() 
	{
		if ( getCPU().interruptsEnabled() ) 
		{ 
			final IInterrupt irq;
			synchronized( interruptQueue ) 
			{
				if ( currentInterrupt != null ) {
					irq = currentInterrupt;
					currentInterrupt = null;
				} 
				else  if ( ! interruptQueue.isEmpty() && ! getCPU().isQueueInterrupts() ) 
				{
					irq = interruptQueue.remove(0);
				} else {
					irq = null;
				}
			}

			if ( irq != null ) {
				handleInterrupt( irq );
			}
		}

		beforeCommandExecution();

		int execDurationInCycles=-1; 
		try 
		{
            final Address previousPC = pc;		    
			execDurationInCycles = executeInstruction();
			this.previousPC = previousPC;
			currentCycle+=execDurationInCycles;
		} 
		catch(Exception e) {
			stop(true);
			e.printStackTrace();
			System.err.println("\n\nERROR: Simulation stopped due to error.");
			return 0;
		} 
		finally 
		{
			afterCommandExecution( execDurationInCycles );
		}
		return execDurationInCycles;
	}     

	private void handleInterrupt(IInterrupt irq) 
	{
		/* When IA is set to something other than 0, interrupts triggered on the DCPU-16
		 * will 
		 * 
		 * - push PC to the stack, 
		 * - push A to the stack
		 * - set the PC to IA
		 * - set A to the interrupt message
		 * 
		 * A well formed interrupt handler must 
		 * - pop A from the stack 
		 * - popping PC from the stack
		 * 
		 * when returning.     	 
		 */
		
		// push PC to stack
		// SET [ --SP ] , PC
		push( pc.getValue() );
		
		// push A to stack
		push( registers.get(REGISTER_A));
		
		pc = interruptAddress;
		registers.set( REGISTER_A, irq.getMessage() );
	}

	@Override
	public void executeOneInstruction() 
	{
		stop(false);
		internalExecuteOneInstruction();
	}   

	protected void beforeCommandExecution() 
	{
		listenerHelper.invokeBeforeCommandExecutionListeners( this.clockThread.isRunnable );
	}
	
	/**
	 * 
	 * @param executedCommandDuration duration (in cycles) of last command or -1 if execution failed with an internal emulator error
	 */
	protected void afterCommandExecution(final int executedCommandDuration) 
	{
		// invoke listeners
		listenerHelper.invokeAfterCommandExecutionListeners( clockThread.isRunnable , executedCommandDuration );

		// check whether we reached a breakpoint
		final Breakpoint breakpoint;
		synchronized( breakpoints ) 
		{
			breakpoint = breakpoints.get( pc ); 
		}
		
		if ( breakpoint != null && breakpoint.matches( this ) ) 
		{
		    stop(false);

			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.onBreakpoint( emulator , breakpoint );
				}
			});             
		}
	}

	private boolean isConditionalInstruction(int instructionWord) {

		final int opCode = (instructionWord & 0x1f);
		return ( opCode >= 0x10 && opCode <= 0x17);
	}

	@Override
	public void skipCurrentInstruction() 
	{
		final int instructionWord = memory.read( pc );

		pc = pc.incrementByOne(true);

		final int opCode = (instructionWord & 0x1f);

		final int operandsSizeInWords;
		switch( opCode ) 
		{
		case 0x00: // skip special opcode
			operandsSizeInWords= getOperandsSizeInWordsForSpecialInstruction( instructionWord );
			break;
		case 0x01: // SET
		case 0x02: // ADD
		case 0x03: // SUB
		case 0x04: // MUL
		case 0x05: // MLI
		case 0x06: // DIV
		case 0x07: // DVI
		case 0x08: // MOD
		case 0x09: // MDI
		case 0x0a: // AND
		case 0x0b: // BOR
		case 0x0c: // XOR
		case 0x0d: // SHR
		case 0x0e: // ASR
		case 0x0f: // SHL
		case 0x10: // IFB
		case 0x11: // IFC
		case 0x12: // IFE
		case 0x13: // IFN
		case 0x14: // IFG
		case 0x15: // IFA
		case 0x16: // IFL
		case 0x17: // IFU
			operandsSizeInWords= getOperandsSizeInWordsForBasicInstruction(instructionWord);
			break;
		case 0x18: // UNKNOWN
		case 0x19: // UNKNOWN;
			operandsSizeInWords = getOperandsSizeInWordsForUnknownInstruction( instructionWord );
			break;
		case 0x1a: // ADX
		case 0x1b: // SBX
			operandsSizeInWords= getOperandsSizeInWordsForBasicInstruction(instructionWord);
			break;                
		case 0x1c: // UNKNOWN
		case 0x1d: // UNKNOWN
			operandsSizeInWords =getOperandsSizeInWordsForUnknownInstruction( instructionWord );
			break;
		case 0x1e: // STI
		case 0x1f: // STD
			operandsSizeInWords = getOperandsSizeInWordsForBasicInstruction( instructionWord );
			break;
		default:
			operandsSizeInWords = getOperandsSizeInWordsForUnknownInstruction( instructionWord );
			break;
		}
		pc = pc.plus( Address.wordAddress( operandsSizeInWords ) , true );
	}

	private int getOperandsSizeInWordsForBasicInstruction(int instructionWord)
	{
		// PC is already pointing at word AFTER current instruction here !
		return getOperandSizeInWords(OperandPosition.SOURCE_OPERAND,instructionWord,false)+getOperandSizeInWords(OperandPosition.TARGET_OPERAND,instructionWord,false) ;
	}

	private int getOperandsSizeInWordsForUnknownInstruction(int instructionWord)
	{
		// PC is already pointing at word AFTER current instruction here !
		return 0;
	}

	private int getOperandsSizeInWordsForSpecialInstruction(int instructionWord)
	{
		// PC is already pointing at word AFTER current instruction here !        
		return  getOperandSizeInWords(OperandPosition.SOURCE_OPERAND,instructionWord,true);
	}

	private int getOperandSizeInWords(OperandPosition position, int instructionWord,boolean isSpecialOpCode) {


		/* SET b,a
		 * 
		 * b is always handled by the processor after a, and is the lower five bits.
		 * In bits (in LSB-0 format), a basic instruction has the format: 
		 * 
		 *    aaaaaabbbbbooooo
		 * 
		 * b = TARGET operand
		 * a = SOURCE operand
		 * 
		 * Special opcodes always have their lower five bits unset, have one value and a
		 * five bit opcode. In binary, they have the format: 
		 * 
		 * aaaaaaooooo00000
		 * 
		 * The value (a) is in the same six bit format as defined earlier.
		 */
		final int operandBits;
		if ( position == OperandPosition.SOURCE_OPERAND || isSpecialOpCode ) { // SET b , a ==> a
			operandBits = (instructionWord >>> 10) & ( 1+2+4+8+16+32); // SET b,a ==> b            
		} else { 
			operandBits = (instructionWord >>> 5) & ( 1+2+4+8+16); // SET b,a ==> b
		}
		if ( operandBits <= 0x07 ) {
			return 0; // operandDesc( registers[ operandBits ] );
		}
		if ( operandBits <= 0x0f ) {
			return 0; // operandDesc( memory[ registers[ operandBits - 0x08 ] ] , 1 );
		}
		if ( operandBits <= 0x17 ) {
			return 1; // operandDesc( memory[ registers[ operandBits - 0x10 ]+nextWord ] ,1 );
		}

		switch( operandBits ) {
		case 0x18:
			// POP / [SP++]
			return 0 ; // operandDesc( memory[ sp ] , 1 );
		case 0x19:
			return 0; // operandDesc( memory[ sp] , 1 );
		case 0x1a:
			return 1; // operandDesc( memory[ sp + nextWord ] , 1 );
		case 0x1b:
			return 0; // operandDesc( sp );
		case 0x1c:
			return 0; // operandDesc( pc );
		case 0x1d:
			return 0; // operandDesc( ex );
		case 0x1e:
			return 1; // operandDesc( memory[ nextWord ] ,1 );
		case 0x1f:
			return 1; // operandDesc( memory[ pc++ ] , 1 );
		}

		// literal value: -1...30 ( 0x20 - 0x3f )
		return 0; // operandDesc( operandBits - 0x21 , 0 ); 
	}

	private int executeInstruction() 
	{
		final int instructionWord = memory.read( pc );
		pc = pc.incrementByOne(true);

		final int opCode = (instructionWord & 0x1f);

		/*
		 *   |--- Basic opcodes (5 bits) ----------------------------------------------------
		 *   |C | VAL  | NAME     | DESCRIPTION
		 *   +---+------+----------+---------------------------------------------------------
		 *   |- | 0x00 | n/a      | special instruction - see below
		 *   |1 | 0x01 | SET b, a | sets b to a
		 *   |2 | 0x02 | ADD b, a | sets b to b+a, sets EX to 0x0001 if there's an overflow, 0x0 otherwise
		 *   |2 | 0x03 | SUB b, a | sets b to b-a, sets EX to 0xffff if there's an underflow, 0x0 otherwise
		 *   |2 | 0x04 | MUL b, a | sets b to b*a, sets EX to ((b*a)>>16)&0xffff (treats b, a as unsigned)
		 *   |2 | 0x05 | MLI b, a | like MUL, but treat b, a as signed
		 *   |3 | 0x06 | DIV b, a | sets b to b/a, sets EX to ((b<<16)/a)&0xffff. if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 *   |3 | 0x07 | DVI b, a | like DIV, but treat b, a as signed. Rounds towards 0
		 *   |3 | 0x08 | MOD b, a | sets b to b%a. if a==0, sets b to 0 instead.
		 *   |3 | 0x09 | MDI b, a | like MOD, but treat b, a as signed. Rounds towards 0
		 *   |1 | 0x0a | AND b, a | sets b to b&a
		 *   |1 | 0x0b | BOR b, a | sets b to b|a
		 *   |1 | 0x0c | XOR b, a | sets b to b^a
		 *   |2 | 0x0d | SHR b, a | sets b to b>>>a, sets EX to ((b<<16)>>a)&0xffff  (logical shift)
		 *   |2 | 0x0e | ASR b, a | sets b to b>>a, sets EX to ((b<<16)>>>a)&0xffff (arithmetic shift) (treats b as signed)
		 *   |2 | 0x0f | SHL b, a | sets b to b<<a, sets EX to ((b<<a)>>16)&0xffff
		 *   |
		 *   |2+| 0x10 | IFB b, a | performs next instruction only if (b&a)!=0
		 *   |2+| 0x11 | IFC b, a | performs next instruction only if (b&a)==0
		 *   |2+| 0x12 | IFE b, a | performs next instruction only if b==a 
		 *   |2+| 0x13 | IFN b, a | performs next instruction only if b!=a 
		 *   |2+| 0x14 | IFG b, a | performs next instruction only if b>a 
		 *   |2+| 0x15 | IFA b, a | performs next instruction only if b>a (signed)
		 *   |2+| 0x16 | IFL b, a | performs next instruction only if b<a 
		 *   |2+| 0x17 | IFU b, a | performs next instruction only if b<a (signed)
		 *   |- | 0x18 | -        |
		 *   |- | 0x19 | -        |
		 *   |3 | 0x1a | ADX b, a | sets b to b+a+EX, sets EX to 0x0001 if there is an over-flow, 0x0 otherwise
		 *   |3 | 0x1b | SBX b, a | sets b to b-a+EX, sets EX to 0xFFFF if there is an under-flow, 0x0 otherwise
		 *   |- | 0x1c | -        | 
		 *   |- | 0x1d | -        |
		 *   |2 | 0x1e | STI b, a | sets b to a, then increases I and J by 1
		 *   |2 | 0x1f | STD b, a | sets b to a, then decreases I and J by 1
		 *   +---+------+----------+----------------------------------------------------------			 
		 */

		switch( opCode ) {
		case 0x00:
			return handleSpecialOpCode( instructionWord );
		case 0x01:
			return handleSET( instructionWord );
		case 0x02:
			return handleADD( instructionWord );
		case 0x03:
			return handleSUB( instructionWord );
		case 0x04:
			return handleMUL( instructionWord );
		case 0x05:
			return handleMLI( instructionWord );
		case 0x06:
			return handleDIV( instructionWord );
		case 0x07:
			return handleDVI( instructionWord );
		case 0x08:
			return handleMOD( instructionWord );
		case 0x09:
			return handleMDI( instructionWord );
		case 0x0a:
			return handleAND( instructionWord );
		case 0x0b:
			return handleBOR( instructionWord );
		case 0x0c:
			return handleXOR( instructionWord );
		case 0x0d:
			return handleSHR( instructionWord );
		case 0x0e:
			return handleASR( instructionWord );
		case 0x0f:
			return handleSHL( instructionWord );
		case 0x10:
			return handleIFB( instructionWord );
		case 0x11:
			return handleIFC( instructionWord );
		case 0x12:
			return handleIFE( instructionWord );
		case 0x13:
			return handleIFN( instructionWord );
		case 0x14:
			return handleIFG( instructionWord );
		case 0x15:
			return handleIFA( instructionWord );
		case 0x16:
			return handleIFL( instructionWord );
		case 0x17:
			return handleIFU( instructionWord );
		case 0x18:
		case 0x19:
			return handleUnknownOpCode( instructionWord );
		case 0x1a:
			return handleADX( instructionWord );
		case 0x1b:
			return handleSBX( instructionWord );
		case 0x1c:
		case 0x1d:
			return handleUnknownOpCode( instructionWord );
		case 0x1e:
			return handleSTI( instructionWord );
		case 0x1f:
			return handleSTD( instructionWord );
		default:
			return handleUnknownOpCode( instructionWord );
		}
	}

	private int handleSTD(int instructionWord) {
		// sets b to a, then decreases I and J by 1
		// a,b,c,x,y,z,i,j
		final OperandDesc source = loadSourceOperand( instructionWord );

		registers.decrementAndGet( 6 ); // registers[6]-=1; <<< I
		registers.decrementAndGet( 7 ); // registers[7]-=1; <<< J
		return 2+storeTargetOperand( instructionWord , source.value )+source.cycleCount;			
	}

	private int handleSTI(int instructionWord) {
		// sets b to a, then increases I and J by 1
		// a,b,c,x,y,z,i,j
		final OperandDesc source = loadSourceOperand( instructionWord );

		registers.incrementAndGet( 6 ); // registers[6]+=1; <<< I
		registers.incrementAndGet( 7 ); // registers[7]+=1; <<< J
		return 2+storeTargetOperand( instructionWord , source.value )+source.cycleCount;			
	}

	private int handleSBX(int instructionWord) 
	{
		// sets b to b-a+EX, sets EX to 0xFFFF if there is an under-flow, 0x0 otherwise
		OperandDesc source = loadSourceOperand( instructionWord );
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value - source.value + ex;
		if ( acc < 0 ) {
			ex = 0xFFFF;
		} else {
			ex = 0;
		}
		return 3+storeTargetOperand( instructionWord , acc)+source.cycleCount;		
	}

	private int handleADX(int instructionWord) {
		// sets b to b+a+EX, sets EX to 0x0001 if there is an over-flow, 0x0 otherwise
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value + source.value + ex;
		if ( acc > 0xffff) {
			ex = 0x0001;
		} else {
			ex = 0;
		}
		return 3+storeTargetOperand( instructionWord , acc)+source.cycleCount;
	}

	private int handleUnknownOpCode(int instructionWord) 
	{
	    final Disassembler dis = new Disassembler();
	    
	    // assume worst-case , each instruction only is one word
	    final int instructionCount = Address.calcDistanceInBytes( Address.wordAddress( 0 ) , previousPC ).toSizeInWords().getValue();
	    List<DisassembledLine> lines = dis.disassemble( getMemory() , Address.wordAddress( 0 ) , instructionCount , true );
	    for (DisassembledLine line : lines) {
            System.out.println( Misc.toHexString( line.getAddress() )+": "+line.getContents());
        }
		System.err.println("Unknown opcode 0x"+Misc.toHexString( instructionWord )+" at address "+"0x"+Misc.toHexString( pc.decrementByOne() ) );
		System.err.println("Previously executed instruction was at "+Misc.toHexString( previousPC ) );
		throw new RuntimeException("Unknown opcode");
	}

	private int handleIFU(int instructionWord) {
		// performs next instruction only if b<a (signed)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , true );

		int penalty = 0;
		if ( signed( target.value ) >= signed( source.value ) ) 
		{
			penalty = handleConditionFailure();

		}
		return 2+target.cycleCount+source.cycleCount+penalty;			
	}

	private int handleConditionFailure() 
	{
		final boolean isSkippingConditional = isConditionalInstruction( memory.read( pc ) );

		skipCurrentInstruction();

		if ( isSkippingConditional ) 
		{
			skipCurrentInstruction();		    
			return 2;
		}
		return 1;
	}

	private int handleIFL(int instructionWord) {
		// performs next instruction only if b<a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , true );

		int penalty = 0;
		if ( target.value >= source.value ) {
			penalty = handleConditionFailure();
		}
		return 2+target.cycleCount+source.cycleCount+penalty;		
	}

	private int handleIFA(int instructionWord) {
		// performs next instruction only if b>a (signed)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , true );

		int penalty = 0;
		if ( signed( target.value ) <= signed( source.value ) ) {
			penalty = handleConditionFailure();
		}
		return 2+target.cycleCount+source.cycleCount+penalty;		
	}

	private int handleIFG(int instructionWord) {
		// performs next instruction only if b>a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , true );

		int penalty=0;
		if ( target.value <= source.value ) {
			penalty = handleConditionFailure();
		}
		return 2+target.cycleCount+source.cycleCount+penalty;			
	}

	private int handleIFN(int instructionWord) {
		// performs next instruction only if b!=a
		OperandDesc source = loadSourceOperand( instructionWord );
		OperandDesc target = loadTargetOperand( instructionWord , false , true );

		int penalty = 0;
		if ( target.value == source.value ) {
			penalty = handleConditionFailure();
		}
		return 2+target.cycleCount+source.cycleCount+penalty;			
	}

	private int handleIFE(int instructionWord) {
		// performs next instruction only if b==a
		OperandDesc source = loadSourceOperand( instructionWord );
		OperandDesc target = loadTargetOperand( instructionWord , false , true );

		int penalty=0;
		if ( target.value != source.value ) {
			penalty = handleConditionFailure();
		}
		return 2+target.cycleCount+source.cycleCount+penalty;		
	}

	private int handleIFC(int instructionWord) {
		// performs next instruction only if (b&a)==0
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , true );

		int penalty = 0;
		if ( (target.value & source.value) != 0 ) {
			penalty = handleConditionFailure();
		}
		return 2+target.cycleCount+source.cycleCount+penalty;
	}

	private int handleIFB(int instructionWord) {
		// performs next instruction only if (b&a)!=0
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , true );

		int penalty=0;
		if ( (target.value & source.value) == 0 ) {
			penalty = handleConditionFailure();
		}
		return 2+target.cycleCount+source.cycleCount+penalty;
	}

	private int handleSHL(int instructionWord) {
		// sets b to b<<a, sets EX to ((b<<a)>>16)&0xffff
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value << source.value;
		ex = (( target.value << source.value)>>16 ) & 0xffff;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleASR(int instructionWord) {
		// sets b to b>>a, sets EX to ((b<<16)>>>a)&0xffff (arithmetic shift) (treats b as signed)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value >> source.value;
		ex = (( target.value << 16)>>>source.value ) & 0xffff;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleSHR(int instructionWord) {
		//  sets b to b>>>a, sets EX to ((b<<16)>>a)&0xffff  (logical shift)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value >>> source.value;
		ex = (( target.value << 16)>>source.value ) & 0xffff;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleXOR(int instructionWord) 
	{
		//  sets b to b^a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value ^ source.value;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleBOR(int instructionWord) 
	{
		//  sets b to b|a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value | source.value;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;		
	}

	private int handleAND(int instructionWord) {
		// sets b to b&a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value & source.value;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleMDI(int instructionWord) {
		// like MOD, but treat b, a as signed. Rounds towards 0
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc;
		if ( source.value == 0 ) {
			acc=0;
		} else {
			acc = signed( target.value ) % signed( source.value );
		}
		return 3+storeTargetOperand( instructionWord , acc )+source.cycleCount;		
	}

	private int handleMOD(int instructionWord) {
		// sets b to b%a. if a==0, sets b to 0 instead.
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc;
		if ( source.value == 0 ) {
			acc=0;
		} else {
			acc = target.value % source.value;
		}
		return 3+storeTargetOperand( instructionWord , acc )+source.cycleCount;		
	}

	private int handleDVI(int instructionWord) {
		// e DIV, but treat b, a as signed. Rounds towards 0
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc;
		if ( source.value == 0 ) {
			ex = 0;
			acc=0;
		} else {
			acc = signed( target.value ) / signed( source.value );
			ex = (( signed( target.value ) << 16) / signed( source.value) )& 0xffff;
		}
		return 3+storeTargetOperand( instructionWord , acc )+source.cycleCount; 	
	}

	private int handleDIV(int instructionWord) 
	{
		/* set b (TARGET) ,a (SOURCE) 
		 * sets b to b/a, sets EX to ((b<<16)/a)&0xffff. if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 */
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc;
		if ( source.value == 0 ) {
			ex = 0;
			acc=0;
		} else {
			acc = target.value / source.value;
			ex = (( target.value << 16) / source.value) & 0xffff;
		}
		return 3+storeTargetOperand( instructionWord , acc )+source.cycleCount; 		
	}

	private int handleMLI(int instructionWord) {
		//  like MUL, but treat b, a as signed
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = signed( target.value ) * signed( source.value );
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount; 		
	}

	private int signed( int value) 
	{
		if ( ( value & ( 1 << 15 ) ) != 0 ) { // MSB set => negative value
			return value | 0xffff0000;
		}
		return value;
	}

	private int handleMUL(int instructionWord) 
	{
		// sets b to b*a, sets EX to ((b*a)>>16)&0xffff (treats b, a as unsigned)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value * source.value;
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount; 
	}

	private int handleSUB(int instructionWord) {
		// sets b to b-a, sets EX to 0xffff if there's an underflow, 0x0 otherwise
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value - source.value;
		if ( acc < 0 ) {
			ex = 0xffff;
		} else {
			ex = 0;
		}
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount; 		
	}

	private int handleADD(int instructionWord) 
	{
		// sets b to b+a, sets EX to 0x0001 if there's an overflow, 0x0 otherwise
		OperandDesc source = loadSourceOperand( instructionWord );
		OperandDesc target = loadTargetOperand( instructionWord , false , false );

		final int acc = target.value + source.value;
		if ( acc > 0xffff) {
			ex = 0x0001;
		} else {
			ex = 0;
		}
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount; 
	}

	private int handleSET(int instructionWord) 
	{
		final OperandDesc desc = loadSourceOperand( instructionWord );
		return 1+storeTargetOperand( instructionWord , desc.value ) + desc.cycleCount;
	}

	private int handleSpecialOpCode(int instructionWord) {

		final int opCode = ( instructionWord >>> 5 ) &0x1f;

		/*
		 *  |--- Special opcodes: (5 bits) --------------------------------------------------
		 *  | C | VAL  | NAME  | DESCRIPTION
		 *  |---+------+-------+-------------------------------------------------------------
		 *  | - | 0x00 | n/a   | reserved for future expansion
		 *  | 3 | 0x01 | JSR a | pushes the address of the next instruction to the stack, then sets PC to a
		 *  | - | 0x02 | -     |
		 *  | - | 0x03 | -     |
		 *  | - | 0x04 | -     |
		 *  | - | 0x05 | -     |
		 *  | - | 0x06 | -     |
		 *  | 9 | 0x07 | HCF a | use sparingly
		 *  | 4 | 0x08 | INT a | triggers a software interrupt with message a
		 *  | 1 | 0x09 | IAG a | sets a to IA 
		 *  | 1 | 0x0a | IAS a | sets IA to a
		 *  | 3 | 0x0b | IAP a | if IA is 0, does nothing, otherwise pushes IA to the stack,
		 *  |   |      |       | then sets IA to a
		 *  | 2 | 0x0c | IAQ a | if a is nonzero, interrupts will be added to the queue
		 *  |   |      |       | instead of triggered. if a is zero, interrupts will be
		 *  |   |      |       | triggered as normal again
		 *  | - | 0x0d | -     |
		 *  | - | 0x0e | -     |
		 *  | - | 0x0f | -     |
		 *  | 2 | 0x10 | HWN a | sets a to number of connected hardware devices
		 *  | 4 | 0x11 | HWQ a | sets A, B, C, X, Y registers to information about hardware a
		 *  |   |      |       | A+(B<<16) is a 32 bit word identifying the hardware id
		 *  |   |      |       | C is the hardware version
		 *  |   |      |       | X+(Y<<16) is a 32 bit word identifying the manufacturer
		 *  | 4+| 0x12 | HWI a | sends an interrupt to hardware a
		 *  | - | 0x13 | -     |
		 *  | - | 0x14 | -     |
		 *  | - | 0x15 | -     |
		 *  | - | 0x16 | -     |
		 *  | - | 0x17 | -     |
		 *  | - | 0x18 | -     |
		 *  | - | 0x19 | -     |
		 *  | - | 0x1a | -     |
		 *  | - | 0x1b | -     |
		 *  | - | 0x1c | -     |
		 *  | - | 0x1d | -     |
		 *  | - | 0x1e | -     |
		 *  | - | 0x1f | -     |
		 *  |---+------+-------+-------------------------------------------------------------		 
		 */

		switch( opCode ) {
		case 0x00:
			return handleUnknownOpCode( instructionWord );
		case 0x01:
			return handleJSR( instructionWord );
		case 0x02:
		case 0x03:
		case 0x04:
		case 0x05:
		case 0x06:
			return handleUnknownOpCode( instructionWord );
		case 0x07:
			return handleHCF( instructionWord );
		case 0x08:
			return handleINT( instructionWord );
		case 0x09:
			return handleIAG( instructionWord );
		case 0x0a:
			return handleIAS( instructionWord );
		case 0x0b:
			return handleRFI( instructionWord );
		case 0x0c:
			return handleIAQ( instructionWord );
		case 0x0d:
		case 0x0e:
		case 0x0f:
			return handleUnknownOpCode( instructionWord );
		case 0x10:
			return handleHWN( instructionWord );
		case 0x11:
			return handleHWQ( instructionWord );
		case 0x12:
			return handleHWI( instructionWord );
		case 0x14:
		case 0x15:
		case 0x16:
		case 0x17:
		case 0x18:
		case 0x19:
		case 0x1a:
		case 0x1b:
		case 0x1c:
		case 0x1d:
		case 0x1e:
		case 0x1f:
		default:
			return handleUnknownOpCode( instructionWord );
		}
	}

	private int handleHWI(int instructionWord) 
	{
		final OperandDesc operand = loadSourceOperand(instructionWord);
		final int hardwareSlot = operand.value;
		
		final IDevice device = getDeviceForSlot( hardwareSlot );
		if ( device == null ) 
		{
			System.err.println("ERROR: Unknown hardware slot #"+hardwareSlot);
			stop(true);
			return 4+operand.cycleCount;
		}
		
		device.handleInterrupt( this );
		
		return 4+operand.value;
	}
	
	private IDevice getDeviceForSlot(int hardwareSlot) 
	{
		synchronized( devices ) {
			if ( hardwareSlot>=0 && hardwareSlot<devices.size()) {
				return devices.get(hardwareSlot);
			}
		}
		return null;
	}

	private int handleHWQ(int instructionWord) {
		
		// Sets A, B, C, X, Y registers to information about hardware a.
		
		final OperandDesc operand = loadSourceOperand(instructionWord);
		final int hardwareSlot = operand.value;
		
		final IDevice device = getDeviceForSlot( hardwareSlot );
		if ( device == null ) 
		{
			System.err.println("ERROR: Unknown hardware slot #"+hardwareSlot);
			stop(true);
			return 4+operand.cycleCount;
		}
		
		/* A+(B<<16) is a 32 bit word identifying the hardware id
		 * 
		 * A = LSB hardware ID (16 bit)
		 * B = MSB hardware ID (16 bit)
         * C = hardware version
         * X = LSB manufacturer ID (16 bit)
         * Y = MSB manufacturer ID (16 bit)
		 */
		final DeviceDescriptor descriptor = device.getDeviceDescriptor();
		
		registers.set( REGISTER_A , (int) descriptor.getID() & 0xffff );
		registers.set( REGISTER_B , (int) ( ( descriptor.getID() >>> 16 ) & 0xffff ) );
		registers.set( REGISTER_C , descriptor.getVersion() & 0xffff );
		
		registers.set( REGISTER_X , (int) descriptor.getManufacturer() & 0xffff );
		registers.set( REGISTER_Y , (int) ( ( descriptor.getManufacturer() >>> 16 ) & 0xffff ) );		
		
		return 4+operand.cycleCount;
	}

	private int handleHWN(int instructionWord) 
	{
		// sets a to number of connected hardware devices
		final int deviceCount;
		synchronized( devices ) {
			deviceCount=devices.size();
		}
		return 2 + storeTargetOperand( instructionWord , deviceCount , true );
	}

	private int handleIAQ(int instructionWord) {
		
		/* if a is nonzero, interrupts will be added to the queue
		 * instead of triggered. if a is zero, interrupts will be
		 * triggered as normal again
		 */
		final OperandDesc operand = loadSourceOperand( instructionWord );
		getCPU().setQueueInterrupts( operand.value == 0 );
		return 2+operand.cycleCount;
	}

	private int handleRFI(int instructionWord) 
	{
		/*
		 *  3 | 0x0b | RFI a | disables interrupt queueing, pops A from the stack, then 
   |      |       | pops PC from the stack
		 */
		getCPU().setQueueInterrupts( false );
		
		registers.set(REGISTER_A , pop() ); // pop a from stack
		pc = Address.wordAddress( pop() ); // pop PC from stack
		return 3;
	}
	
	private int pop() 
	{
		// SET a, [SP++]
		final int result = memory.read( sp ) & 0xffff;
		sp=sp.incrementByOne(true);
		return result;
	}
	
	private void push(int value) 
	{
		// SET [--SP] , blubb
		sp = sp.decrementByOne();
		memory.write( sp , value & 0xffff );
	}	

	private int handleIAS(int instructionWord) 
	{
		// IAS a => sets IA to a
		final OperandDesc operand = loadSourceOperand( instructionWord );
		interruptAddress = Address.wordAddress( operand.value );
		return 1+operand.cycleCount;
	}

	private int handleIAG(int instructionWord) {
		// IAG a => sets A to IA
		return 1+storeTargetOperand( instructionWord , interruptAddress.getValue() , true );
	}

	private int handleINT(int instructionWord) 
	{
		final OperandDesc operand = loadSourceOperand( instructionWord );
		triggerInterrupt( new SoftwareInterrupt( operand.value ) );
		return 4+operand.cycleCount;
	}

	private int handleHCF(int instructionWord) 
	{
		final OperandDesc operand = loadSourceOperand( instructionWord );
		stop(false);
		return 1+operand.cycleCount;
	}

	private int handleJSR(int instructionWord) 
	{
		// pushes the address of the next instruction to the stack, then sets PC to a
		OperandDesc source= loadSourceOperand( instructionWord );
		push( pc.getValue() );
		pc = Address.wordAddress( source.value );
		return 3+source.cycleCount;
	}

	/*
	 * 
	 * b is always handled by the processor after a, and is the lower five bits.
	 * In bits (in LSB-0 format), a basic instruction has the format: 
	 * 
	 *    aaaaaabbbbbooooo
	 *    
	 * SET b,a
	 * 
	 * b = TARGET operand
	 * a = SOURCE operand
	 * 
	 * --- Values: (5/6 bits) ---------------------------------------------------------
	 * 
	 * | C | VALUE     | DESCRIPTION
	 * +---+-----------+----------------------------------------------------------------
	 * | 0 | 0x00-0x07 | register (A, B, C, X, Y, Z, I or J, in that order)
	 * | 0 | 0x08-0x0f | [register]
	 * | 1 | 0x10-0x17 | [register + next word]
	 * | 0 |      0x18 | (PUSH / [--SP]) if in TARGET, or (POP / [SP++]) if in SOURCE
	 * | 0 |      0x19 | [SP] / PEEK
	 * | 1 |      0x1a | [SP + next word] / PICK n
	 * | 0 |      0x1b | SP
	 * | 0 |      0x1c | PC
	 * | 0 |      0x1d | EX
	 * | 1 |      0x1e | [next word]
	 * | 1 |      0x1f | next word (literal)
	 * | 0 | 0x20-0x3f | literal value 0xffff-0x1e (-1..30) (literal) (only for SOURCE)
	 * +---+-----------+----------------------------------------------------------------	 
	 */
	private int storeTargetOperand(int instructionWord,int value) 
	{
		return storeTargetOperand( instructionWord , value , false );
	}
	private int storeTargetOperand(int instructionWord,int value,boolean isSpecialOpcode) 
	{
		final int operandBits;
		/*
		 * Special opcodes always have their lower five bits unset, have one value and a
         * five bit opcode. 
         * 
         * In binary, they have the format:    aaaaaaooooo00000
		 */
		if ( isSpecialOpcode ) {
			operandBits = (instructionWord >>> 10) & ( 1+2+4+8+16+32);
		} else {
			operandBits = (instructionWord >>> 5) & ( 1+2+4+8+16);			
		}
		
		if ( operandBits <= 07 ) {
			registers.set( operandBits ,  value & 0xffff );
			return 0;
		}
		if ( operandBits <= 0x0f ) {
			memory.write( registers.get( operandBits - 0x08 ) , value);
			return 1;
		}
		if ( operandBits <= 0x17 ) {
			final int nextWord = memory.read( pc );
			pc = pc.incrementByOne(true);
			memory.write( registers.get( operandBits - 0x10 )+nextWord , value);
			return 1;
		}
		switch( operandBits ) {
		case 0x18: // (PUSH / [--SP]) if in b, or (POP / [SP++]) if in a
			push( value );
			return 1;
		case 0x19:
			return handleIllegalTargetOperand(instructionWord);
		case 0x1a:
			int nextWord = memory.read( pc );
			pc = pc.incrementByOne(true);
			Address dst = sp.plus( Address.wordAddress( nextWord ) , true);
			memory.write( dst , value );
			return 1;
		case 0x1b:
			sp = Address.wordAddress( value );
			return 0;
		case 0x1c:
			pc = Address.wordAddress( value );
			return 0;
		case 0x1d:
			ex = value;
			return 0;
		case 0x1e:
			nextWord = memory.read( pc );
			pc = pc.incrementByOne(true);
			memory.write( nextWord , value);
			return 1;
		default:
			return handleIllegalTargetOperand(instructionWord); // assignment to literal value
		}
	}

	private int handleIllegalTargetOperand(int instructionWord) {
		throw new RuntimeException("Illegal target operand in instruction word 0x"+
				Misc.toHexString( instructionWord )+" at address 0x"+Misc.toHexString( pc.decrementByOne() ) );
	}

	private OperandDesc loadSourceOperand(int instructionWord) {

		/* SET b,a
		 * 
		 * b = TARGET operand
		 * a = SOURCE operand
		 * 
		 * Special opcodes always have their lower five bits unset, have one value and a
		 * five bit opcode. In binary, they have the format: aaaaaaooooo00000
		 * The value (a) is in the same six bit format as defined earlier.
		 */

		final int operandBits= (instructionWord >>> 10) & ( 1+2+4+8+16+32);
		if ( operandBits <= 0x07 ) {
			return operandDesc( registers.get( operandBits ) );
		}
		if ( operandBits <= 0x0f ) {
			return operandDesc( memory.read( registers.get( operandBits - 0x08 ) ) , 1 );
		}
		if ( operandBits <= 0x17 ) {
			final int nextWord = memory.read( pc );
			pc = pc.incrementByOne(true);
			return operandDesc( memory.read( registers.get( operandBits - 0x10 )+nextWord ) ,1 );
		}

		switch( operandBits ) {
		case 0x18: // (PUSH / [--SP]) if in b, or (POP / [SP++]) if in a
			final OperandDesc tmp = operandDesc( memory.read( sp ) , 1 );
			sp = sp.incrementByOne(true);                
			return tmp;
		case 0x19:
			return operandDesc( memory.read( sp ) , 1 );
		case 0x1a:
			int nextWord = memory.read( pc );
			pc = pc.incrementByOne(true);
			final Address dst = sp.plus( Address.wordAddress( nextWord ) , true );
			return operandDesc( memory.read( dst ) , 1 );
		case 0x1b:
			return operandDesc( sp.getValue() );
		case 0x1c:
			return operandDesc( pc.getValue() );
		case 0x1d:
			return operandDesc( ex );
		case 0x1e:
			nextWord = memory.read( pc );
			pc = pc.incrementByOne(true);
			return operandDesc( memory.read( nextWord ) ,1 );
		case 0x1f:
			final OperandDesc result = operandDesc( memory.read( pc ) , 1 );
			pc = pc.incrementByOne(true);
			return result;
		}

		// literal value: -1...30 ( 0x20 - 0x3f )
		return operandDesc( operandBits - 0x21 , 0 ); 
	}	

	/**
	 * 
	 * @param instructionWord
	 * @param specialInstruction
	 * @param performIncrementDecrement Whether this invocation should also increment/decrement registers as necessary or 
	 * whether this is handled by the caller (because a subsequent STORE will be performed )
	 * @return
	 */
	protected OperandDesc loadTargetOperand(int instructionWord,boolean specialInstruction,boolean performIncrementDecrement) {
		/* 
		 * SET b,a
		 * 
		 * b = TARGET operand
		 * a = SOURCE operand
		 * 
		 * SOURCE is always handled by the processor BEFORE TARGET, and is the lower five bits.
		 * In bits (in LSB-0 format), a basic instruction has the format: 
		 * 
		 * aaaaaabbbbbooooo		 
		 * 
		 * SPECIAL opcodes always have their lower five bits unset, have one value and a
		 * five bit opcode. In binary, they have the format: 
		 * 
		 * aaaaaaooooo00000
		 * 
		 * The value (a) is in the same six bit format as defined earlier.
		 */

		final int operandBits;

		if ( specialInstruction ) {
			operandBits= (instructionWord >>> 10) & ( 1+2+4+8+16+32);
		} else {
			operandBits= (instructionWord >>> 5) & ( 1+2+4+8+16);			
		}

		if ( operandBits <= 0x07 ) {
			return operandDesc( registers.get( operandBits ) );
		}
		if ( operandBits <= 0x0f ) {
			return operandDesc( memory.read( registers.get( operandBits - 0x08 ) ) , 1 );
		}
		if ( operandBits <= 0x17 ) 
		{
			final int nextWord;
			if ( performIncrementDecrement ) {
				nextWord = memory.read( pc );
				pc = pc.incrementByOne(true);
			} else {
				nextWord = memory.read( pc );                
			}
			return operandDesc( memory.read( registers.get( operandBits - 0x10 )+nextWord ) ,1 );
		}

		switch( operandBits ) {
		case 0x18: // (POP / [SP++]) if in a
			final OperandDesc tmp = operandDesc( memory.read( sp ) , 1 );
			sp = sp.incrementByOne(true);
			return tmp;
		case 0x19:
			return operandDesc( memory.read( sp ) , 1 );
		case 0x1a:
			int nextWord = 0;
			if ( performIncrementDecrement ) {
				nextWord = memory.read( pc );
				pc = pc.incrementByOne(true);
			} else {
				nextWord = memory.read( pc );                    
			}
			final Address dst = sp.plus( Address.wordAddress( nextWord ) , true );
			return operandDesc( memory.read( dst ) , 1 );
		case 0x1b:
			return operandDesc( sp.getValue() );
		case 0x1c:
			return operandDesc( pc.getValue() );
		case 0x1d:
			return operandDesc( ex );
		case 0x1e:
			if ( performIncrementDecrement ) {
				nextWord = memory.read( pc );
				pc = pc.incrementByOne(true);
			} else {
				nextWord = memory.read( pc );
			}
			return operandDesc( memory.read( nextWord ) ,1 );
		case 0x1f:
			if ( performIncrementDecrement ) {
				nextWord = memory.read( pc );
				pc = pc.incrementByOne(true);
			} else {
				nextWord = memory.read( pc );                    
			}
			return operandDesc( nextWord , 1 );
		}

		// literal value: -1...30 ( 0x20 - 0x3f )
		return operandDesc( operandBits - 0x21 , 0 ); 

	}

	protected static final class OperandDesc {
		public final int value;
		public final int cycleCount; // how long it takes to perform the operation

		public OperandDesc(int value) {
			this.value = value;
			this.cycleCount = 0;
		}

		public OperandDesc(int value,int cycleCount) {
			this.value = value;
			this.cycleCount = cycleCount;
		}
	}

	private OperandDesc operandDesc(int value) {
		return new OperandDesc( value );
	}

	private OperandDesc operandDesc(int value,int cycleCount) {
		return new OperandDesc( value , cycleCount );
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.jasm16.emulator.IEmulator#loadMemory(de.codesourcery.jasm16.Address, byte[])
	 */
	@Override
	public void loadMemory(final Address startingOffset, final byte[] data) 
	{
		stop(false);

		if ( clockThread.isRunnable ) {
			throw new IllegalStateException("Emulation not stopped?");
		}
		memory.clear();
		MemUtils.bulkLoad( memory , startingOffset , data );

		// notify listeners
		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.afterMemoryLoad( emulator , startingOffset , data.length );                
			}
		});
	}

	protected interface IEmulationListenerInvoker {
		public void invoke(IEmulator emulator, IEmulationListener listener);
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.jasm16.emulator.IEmulator#calibrate()
	 */
	@Override
	public synchronized void calibrate() 
	{
		final double EXPECTED_CYCLES_PER_SECOND = 100000; // 100 kHz       
		final double expectedNanosPerCycle = (1000.0d * 1000000.0d) / EXPECTED_CYCLES_PER_SECOND;       

		System.out.print("Measuring delay loop...");
		/*
		 * Warm-up JVM / JIT.
		 */
		double sum =0.0d;
		for ( int i = 0 ; i < 5 ; i++ ) {
			final double tmp = clockThread.measureDelayLoopInNanos();
			sum+= tmp;
		}

		/*
		 * Repeatedly measure the execution time
		 * for a single delay-loop iteration.
		 */
		final int LOOP_COUNT=5;
		sum =0.0d;

		for ( int i = 0 ; i < LOOP_COUNT ; i++ ) {
			final double tmp = clockThread.measureDelayLoopInNanos();
			sum+= tmp;
		}

		final double nanosPerDelayLoopExecution = sum / LOOP_COUNT;
		System.out.println(" one iteration = "+nanosPerDelayLoopExecution+" nanoseconds.");
		final double loopIterationsPerCycle = expectedNanosPerCycle / nanosPerDelayLoopExecution;
		
		clockThread.adjustmentFactor = 1.0d;
		clockThread.oneCycleDelay = (int) Math.round( loopIterationsPerCycle );
		
		System.out.println(" one cycle = "+clockThread.oneCycleDelay+" loop iterations.");
	}

	@Override
	public ICPU getCPU()
	{
		return cpuAdaptor;
	}

	@Override
	public IReadOnlyMemory getMemory()
	{
		return memory;
	}

	@Override
	public void addEmulationListener(IEmulationListener listener)
	{
		listenerHelper.addEmulationListener( listener );
	}

	@Override
	public void removeEmulationListener(IEmulationListener listener)
	{
		listenerHelper.removeEmulationListener( listener );
	}

	@Override
	public void addBreakpoint(final Breakpoint bp)
	{
		if (bp == null) {
			throw new IllegalArgumentException("breakpoint must not be NULL.");
		}
		Breakpoint existing;
		synchronized( breakpoints ) {
			existing = breakpoints.put( bp.getAddress() , bp );
		}  

		// notify listeners
		if ( existing != null ) {
			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.breakpointDeleted( emulator , bp );
				}
			});         	
		}

		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.breakpointAdded( emulator , bp );
			}
		});        
	}

	@Override
	public void breakpointChanged(final Breakpoint bp) {

		Breakpoint existing;
		synchronized( breakpoints ) {
			existing = breakpoints.get( bp.getAddress() );
		}     	

		if ( existing == null ) {
			LOG.warn("breakpointChanged(): Unknown breakpoint "+bp);
			return;
		}

		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.breakpointChanged( emulator , bp );
			}
		});         
	}

	@Override
	public void deleteBreakpoint(final Breakpoint bp)
	{
		if (bp == null) {
			throw new IllegalArgumentException("breakpoint must not be NULL.");
		}
		Breakpoint existing;
		synchronized( breakpoints ) {
			existing = breakpoints.remove( bp.getAddress() );
		}      
		// notify listeners
		if ( existing != null ) {
			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.breakpointDeleted( emulator , bp );
				}
			});         
		}
	}

	@Override
	public List<Breakpoint> getBreakPoints()
	{
		synchronized( breakpoints ) {
			return new ArrayList<Breakpoint>( breakpoints.values() );
		}
	}

	@Override
	public Breakpoint getBreakPoint(Address address)
	{
		if (address == null) {
			throw new IllegalArgumentException("address must not be NULL.");
		}
		synchronized( breakpoints ) {
			return breakpoints.get( address );
		}
	}

	@Override
	public void unmapRegion(IMemoryRegion region) {
		this.memory.unmapRegion( region );
		this.memory.dumpMemoryLayout();
	}

	@Override
	public void mapRegion(IMemoryRegion region) {
		this.memory.mapRegion( region );
		this.memory.dumpMemoryLayout();
	}

	@Override
	public boolean triggerInterrupt(IInterrupt interrupt) 
	{
		if ( ! getCPU().interruptsEnabled() ) {
			return false;
		}
		
		synchronized ( interruptQueue ) 
		{
			if ( currentInterrupt == null && ! getCPU().isQueueInterrupts() ) {
				currentInterrupt  = interrupt;
			} else {
				getCPU().setQueueInterrupts( true );
				interruptQueue.add( interrupt );
			} 
		}
		return true;
	}

	@Override
	public void addDevice(IDevice device) 
	{
		if (device == null) {
			throw new IllegalArgumentException("device must not be null");
		}
		synchronized( devices ) {
			if ( devices.size() >= 65535 ) {
				throw new IllegalStateException("Already 65535 devices registered");
			}
			devices.add( device );
			// TODO: remove debug output
			System.out.println("Device added - configuration is now:\n");
			printDevices();
		}
		device.afterAddDevice( this );
	}
	
	private void printDevices() 
	{
		synchronized( devices ) {

			int slot = 0;
			for ( IDevice d : devices ) {
				System.out.println("Slot #"+slot+":");
				System.out.println( d.getDeviceDescriptor().toString("    ",true));
			}		
		}
	}

	@Override
	public List<IDevice> getDevices() {
		synchronized( devices ) {
			return new ArrayList<IDevice>();
		}
	}

	@Override
	public void removeDevice(IDevice device) 
	{
		if (device == null) {
			throw new IllegalArgumentException("device must not be null");
		}
		
		final boolean isRegistered;
		synchronized( devices ) {
			isRegistered = devices.contains( device );
		}
		
		if ( isRegistered ) {
			// TODO: remove debug output
			System.out.println("Device removed - configuration is now:\n");
			printDevices();			
			device.beforeRemoveDevice( this );
		} else {
			return;
		}
		
		synchronized( devices ) {
			devices.remove( device );
		}
	}
	
	@Override
	public EmulationSpeed getEmulationSpeed() {
		return emulationSpeed;
	}
	
	@Override
	public void setEmulationSpeed(final EmulationSpeed newSpeed) 
	{
	    if (newSpeed == null) {
            throw new IllegalArgumentException("speed must not be NULL.");
        }
	    
	    if ( this.emulationSpeed == newSpeed ) {
	        return;
	    }
	    
		if ( newSpeed == EmulationSpeed.REAL_SPEED && ! isCalibrated() ) {
			calibrate();
		}
		
		final EmulationSpeed oldSpeed = this.emulationSpeed;
        this.emulationSpeed = newSpeed;  		
        
        listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

            @Override
            public void invoke(IEmulator emulator, IEmulationListener listener)
            {
                listener.onEmulationSpeedChange( oldSpeed , newSpeed );
            }
        });           
	}
	
	@Override
	public boolean isCalibrated() {
		return clockThread.oneCycleDelay != -1;
	}
	
    @Override
    public boolean isStoppedBecauseOfError()
    {
        return stoppedBecauseOfError;
    }
    
    private static <T> T safeTake(BlockingQueue<T> queue) {
        while(true) 
        {
            try {
                return queue.take();
            } catch (InterruptedException e) {}
        }
    }     
    
    private static <T> void safePut(BlockingQueue<T> queue, T value) {
        while(true) 
        {
            try {
                queue.put(value);
                return;
            } catch (InterruptedException e) {}
        }
    }    
}