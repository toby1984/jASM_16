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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.ast.OperandNode.OperandPosition;
import de.codesourcery.jasm16.disassembler.DisassembledLine;
import de.codesourcery.jasm16.disassembler.Disassembler;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.devices.IInterrupt;
import de.codesourcery.jasm16.emulator.devices.SoftwareInterrupt;
import de.codesourcery.jasm16.emulator.exceptions.DeviceErrorException;
import de.codesourcery.jasm16.emulator.exceptions.EmulationErrorException;
import de.codesourcery.jasm16.emulator.exceptions.InterruptQueueFullException;
import de.codesourcery.jasm16.emulator.exceptions.InvalidDeviceSlotNumberException;
import de.codesourcery.jasm16.emulator.exceptions.InvalidTargetOperandException;
import de.codesourcery.jasm16.emulator.exceptions.UnknownOpcodeException;
import de.codesourcery.jasm16.emulator.memory.IMemory;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion;
import de.codesourcery.jasm16.emulator.memory.IReadOnlyMemory;
import de.codesourcery.jasm16.emulator.memory.MainMemory;
import de.codesourcery.jasm16.emulator.memory.MemUtils;
import de.codesourcery.jasm16.utils.Misc;

/**
 * DCPU-16 emulator.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public final class Emulator implements IEmulator 
{
	private static final Logger LOG = Logger.getLogger(Emulator.class);
	
	private static final boolean DEBUG = false;
	
	private static final AtomicLong DEBUG_EMULATOR_ID = new AtomicLong(0);
	
	/**
	 * Maximum number of interrupts the emulator's interrupt queue may hold.
	 */
	public static final int INTERRUPT_QUEUE_SIZE = 256;

	private static final boolean DEBUG_LISTENER_PERFORMANCE = false;

	private final IdentityHashMap<IEmulationListener,Long> listenerPerformance =  new IdentityHashMap<IEmulationListener,Long>();

	private final ClockThread clockThread;

	private final long emulatorId = DEBUG_EMULATOR_ID.incrementAndGet();
	
	private final ListenerHelper listenerHelper = new ListenerHelper();

	private static final AtomicLong cmdId = new AtomicLong(0);

	/**
	 * Worker thread command types.
	 *
	 * @author tobias.gierke@code-sourcery.de
	 */
	protected enum CommandType {
		START,
		STOP,
		TERMINATE,
		SPEED_CHANGE;
	}
	
	/**
	 * Command to control the emulation's worker thread responsible
	 * for the actual DCPU-16 instruction execution.
	 *
	 * <p>Command senders may request an acknowledge message
	 * from the worker thread and block until it is received.Each
	 * command carries a unique ID so that an
	 * acknowledge message can be matched with the corresponding command.</p>
	 * 
	 * @author tobias.gierke@code-sourcery.de
	 */
	protected static final class Command {

		private final long id = cmdId.incrementAndGet();

		private final Object payload;
		private final CommandType type;		
		private final boolean requiresACK; 

		public static Command terminateClockThread() {
			return new Command(CommandType.TERMINATE , true );
		}

		public static Command stopCommandWithoutACK() {
			return new Command(CommandType.STOP,false);
		}

		public static Command stopCommand() {
			return new Command(CommandType.STOP,true);
		}

		public static Command startCommand() {
			return new Command(CommandType.START, true);
		}
		
		public static Command changeSpeedCommand(EmulationSpeed newSpeed) {
			return new Command(CommandType.SPEED_CHANGE,true,newSpeed);
		}
		
		protected Command(CommandType type, boolean requiresACK) {
			this(type,requiresACK,null);
		}
		
		protected Command(CommandType type, boolean requiresACK,Object payload) {
			this.type = type;
			this.payload = payload;
			this.requiresACK = requiresACK;
		}
		
		public Object getPayload() { return payload; }
		
		public boolean hasType(CommandType t) { return t.equals( this.type ); }

		public boolean requiresACK() { return requiresACK; }

		public boolean isTerminateCommand() { return type == CommandType.TERMINATE; }	    

		public boolean isStopCommand() { return type == CommandType.STOP|| type == CommandType.TERMINATE; }

		/**
		 * Check whether this is a command that should make the
		 * worker thread enter it's main <code>while()</code> loop.
		 * 
		 * <p>When this method returns <code>true</code> , it does <b>not</b> mean
		 * that the worker thread will actually start executing instructions , it will
		 * only wake it up.
		 * </p>
		 * @return
		 */
		public boolean isStartWorkerMainLoopCommand() { return ! isStopCommand() || isTerminateCommand(); }   	    

		public long getId() { return id; }

		@Override
		public String toString()
		{
			return type+" ( "+id+" , requires_ack="+requiresACK+" )";
		} 
	}

	/**
	 * Helper to manage IEmulationListeners. 
	 * This class manages multiple internal lists , each for a specific listener type.
	 * That way we avoid having to look-up listeners with a specific type each
	 * time a notification needs to be send.
	 * 
	 * @author tobias.gierke@code-sourcery.de
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

		public void removeAllEmulationListeners() 
		{
			synchronized (emuListeners) 
			{
				removeAllNonHardwareListeners( emuListeners );
				removeAllNonHardwareListeners( beforeCommandExecListeners );
				removeAllNonHardwareListeners( continuousModeBeforeCommandExecListeners );
				removeAllNonHardwareListeners( continuousModeAfterCommandExecListeners );
				removeAllNonHardwareListeners( afterCommandExecListeners );
			} 		    
		}

		private void removeAllNonHardwareListeners(List<IEmulationListener> list) {

			for ( Iterator<IEmulationListener> it = list.iterator() ; it.hasNext() ; ) {
				if ( ! it.next().belongsToHardwareDevice() ) {
					it.remove();
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
						LOG.error("notifyListeners(): Listener "+l+" failed",e);
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
						LOG.error("notifyListeners(): Listener "+l+" failed",e);					    
					}
				}       
			}
		}

		public void emulatorDisposed()
		{
			notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.beforeEmulatorIsDisposed( emulator );
				}
			}); 

			final List<IEmulationListener> copy;
			synchronized( emuListeners ) 
			{
				copy = new ArrayList<IEmulationListener>( emuListeners );
			}              
			for ( IEmulationListener l : copy ) {
				removeEmulationListener( l );
			}
		}
	}

	private volatile boolean ignoreAccessToUnknownDevices=false;    
	private volatile boolean checkMemoryWrites = false;

	private volatile Throwable lastEmulationError = null;
	
	private volatile EmulationSpeed emulationSpeed = EmulationSpeed.MAX_SPEED;

	// ============ BreakPoints =======

	// @GuardedBy( breakpoints )
	private final Map<Address,List<Breakpoint>> breakpoints = new HashMap<Address,List<Breakpoint>>(); 

	// ============ Memory ============

	// memory needs to be thread-safe since the emulation runs in a separate thread
	// and UI threads may access the registers concurrently    
	private final MainMemory memory = new MainMemory( 65536 , checkMemoryWrites );

	// ========= devices ===========

	// @GuardedBy( devices )
	private final List<IDevice> devices = new ArrayList<IDevice>();


	// ============ CPU =============== 

	private final Object CPU_LOCK = new Object();

	private Address lastValidInstruction = null;
	
	// @GuardedBy( CPU_LOCK )
	private final CPU cpu = new CPU(memory);
	
	// @GuardedBy( CPU_LOCK )
	private final CPU visibleCPU = new CPU(memory);    

	// a,b,c,x,y,z,i,j
	// all CPU registers needs to be thread-safe since the emulation runs in a separate thread
	// and UI threads may access the registers concurrently 

	private volatile ILogger loggerDelegate = new PrintStreamLogger( System.out );

	private final ILogger out = new ILogger() {

		public void setDebugEnabled(boolean yesNo) {
			loggerDelegate.setDebugEnabled(yesNo);
		}

		public boolean isDebugEnabled() {
			return loggerDelegate.isDebugEnabled();
		}

		public void info(String message) {
			loggerDelegate.info(message);
		}

		public void info(String message, Throwable cause) {
			loggerDelegate.info(message, cause);
		}

		public void warn(String message) {
			loggerDelegate.warn(message);
		}

		public void warn(String message, Throwable cause) {
			loggerDelegate.warn(message, cause);
		}

		public void error(String message) {
			loggerDelegate.error(message);
		}

		public void error(String message, Throwable cause) {
			loggerDelegate.error(message, cause);
		}

		public void debug(String message) {
			loggerDelegate.debug(message);
		}

		public void debug(String message, Throwable cause) {
			loggerDelegate.debug(message, cause);
		}
	};
	
	/* (non-Javadoc)
	 * @see de.codesourcery.jasm16.emulator.IEmulator#reset()
	 */
	@Override
	public void reset(boolean clearMemory)
	{
		stop(null);

		// reset memory BEFORE resetting devices
		// because if a device uses MMIO, doing the
		// resetMemory() call after resetDevices() may 
		// trigger the device again and thus pollute
		// the reset state
		resetMemory(clearMemory);		

		resetDevices();

		synchronized( CPU_LOCK ) {
		    cpu.reset();
			visibleCPU.reset();
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

	private void resetMemory(boolean clearMemory)
	{
		memory.resetWriteProtection();

		if ( clearMemory ) 
		{
			memory.clear();
		}
	}

	private void resetDevices()
	{
		for ( IDevice device : getDevices() ) {
			try {
				device.reset();
			} catch(Exception e) {
				LOG.error("reset(): Device "+device+" failed during reset",e);
			}
		}
	}

	@Override
	public boolean stop() {
		return stop( null  );
	}

	protected boolean stop(final Throwable cause) 
	{
		this.lastEmulationError = cause;

		final boolean emulationWasRunning = clockThread.stopSimulation(); 
		if ( emulationWasRunning ) 
		{
			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					final Address pc;
					synchronized( CPU_LOCK ) {
						pc = visibleCPU.pc;
					}
					listener.onStop( emulator , pc , cause );
				}
			});  
		}

		// remove all internal breakpoints
		final List<Breakpoint> internalBPs = new ArrayList<Breakpoint>();
		synchronized( breakpoints ) {
			for ( List<Breakpoint> bps : breakpoints.values() ) {
				for ( Breakpoint bp : bps ) {
					if ( bp.isOneShotBreakpoint() ) {
						internalBPs.add( bp );
					}
				}
			}
		}
		for ( Breakpoint bp : internalBPs ) {
			deleteBreakpoint( bp );
		}
		return emulationWasRunning;
	}

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

	/**
	 * Thread responsible for execution of the 
	 * actual instruction emulation.
	 * 
	 * <p>This thread's main loop supports two
	 * execution modes, either {@link EmulationSpeed#MAX_SPEED}
	 * or {@link EmulationSpeed#REAL_SPEED}.</p>
	 *
	 * @author tobias.gierke@code-sourcery.de
	 */
	public final class ClockThread extends Thread {

		// values used for emulation speed calculations
		private long lastStart = 0;
		private int cycleCountAtLastStart=0;
		private long lastStop = 0;
		private int cycleCountAtLastStop=0;
		
		private final AtomicBoolean isRunnable = new AtomicBoolean(false);

		private final BlockingQueue<Command> cmdQueue = new ArrayBlockingQueue<Command>(1);
		private final BlockingQueue<Long> ackQueue = new ArrayBlockingQueue<Long>(300);

		// execution delay loop parameters determined by calibrate() method
		private volatile double adjustmentFactor = 1.0d;
		private volatile int oneCycleDelay = -1;

		// just a dummy value used in our delay loop 
		private int dummy;		
		
		// the current emulation speed
		private EmulationSpeed currentSpeed = emulationSpeed;

		public ClockThread() 
		{
			setName("emulation-clock-thread");
			setDaemon(true);
		}

		@Override
		public void run() {

			lastEmulationError = null;

			Command cmd = waitForStartCommand();

			if ( cmd.isTerminateCommand() ) {
				out.info("Emulator thread terminated.");
				acknowledgeCommand( cmd );
				return;
			}		   

			lastStart = System.currentTimeMillis();
			cycleCountAtLastStart=cpu.currentCycle;

			acknowledgeCommand( cmd );			

			while ( true ) 
			{
				if ( isRunnable.get() == false ) 
				{
				    //  halt execution
					lastStop = System.currentTimeMillis();
					cycleCountAtLastStop = cpu.currentCycle;

					if ( DEBUG_LISTENER_PERFORMANCE ) 
					{
						for ( Map.Entry<IEmulationListener,Long> entry : listenerPerformance.entrySet() ) {
							out.debug( entry.getKey()+" = "+entry.getValue()+" millis" );	
						}
					}

					out.info("Emulator stopped.");
					out.info("Executed cycles: "+(cycleCountAtLastStop-cycleCountAtLastStart) +" ( in "+getRuntimeInSeconds()+" seconds )");
					out.info("Estimated clock rate: "+getEstimatedClockSpeed() );

					cmd = waitForStopCommand();
					
					if ( cmd.isTerminateCommand() ) {
						acknowledgeCommand( cmd );                        
						break;
					}
					acknowledgeCommand( cmd );

					cmd = waitForStartCommand();
					
					if ( cmd.isTerminateCommand() ) {
						acknowledgeCommand( cmd );                        
						break;
					}   					

					lastEmulationError = null;					
					cycleCountAtLastStart = cpu.currentCycle;
					
					lastStart = System.currentTimeMillis();  
					acknowledgeCommand(cmd);
				}

				/* ================
				 * Execute ONE instruction
				 * ================
				 */
				final int durationInCycles = internalExecuteOneInstruction();
				
				if ( currentSpeed == EmulationSpeed.REAL_SPEED ) 
				{
					// adjust execution speed every 10000 cycles
					// to account for CPU load changes / JIT / different instruction profiles
					if ( ( cpu.currentCycle % 10000 ) == 0 ) {
						final double cyclesPerSecond = (cpu.currentCycle-cycleCountAtLastStart) / ( ( System.currentTimeMillis() - lastStart ) / 1000d);
						adjustmentFactor = ( cyclesPerSecond / 100000.0d ); 
					}
					
					// delay execution, this code is exactly the same code as the one timed in
					// measureDelayLoop()
					int j = (int) (oneCycleDelay*durationInCycles*adjustmentFactor);
					for (  ; j > 0 ; j-- ) {
						dummy = ((dummy*2+j*2)/3)/(dummy*7+j);
					}					
				}
			}

			out.info("Emulator thread terminated.");
		}

		public void startSimulation() 
		{
			if ( isRunnable.compareAndSet( false , true ) ) 
			{
				sendToClockThread( Command.startCommand() );
			}
		}		

		/**
		 * Stop simulation.
		 * 
		 * @return true if the simulation was currently running and has been stopped, false if the simulation was already stopped.
		 */
		public boolean stopSimulation() 
		{
			if ( isRunnable.compareAndSet(true,false) )
			{               
				if ( Thread.currentThread() == clockThread ) { 
					sendToClockThread( Command.stopCommandWithoutACK() ); // no point in sending an ACK since the clock thread itself triggered the stop()
				} else {
					sendToClockThread( Command.stopCommand() );
				}
				return true;
			} 
			return false;
		}    		
		
		public void changeSpeed(EmulationSpeed newSpeed) {
			sendToClockThread( Command.changeSpeedCommand( newSpeed ) );
		}
		
		private final AtomicBoolean terminateCommandReceived = new AtomicBoolean(false);

		private void sendToClockThread(Command cmd) 
		{
			if ( DEBUG ) {
				System.out.println("[emulator "+emulatorId+"] Sending command to clock thread: "+cmd);
			}
			
			if ( cmd.hasType(CommandType.TERMINATE ) ) 
			{
				if ( DEBUG ) {
					new Exception("[emulator "+emulatorId+"] Received TERMINATE command").printStackTrace();
				}
				terminateCommandReceived.set( true );
			} else if ( terminateCommandReceived.get() ) {
				throw new IllegalStateException("Can't process any more commands , worker thread already terminated");
			}
			
			safePut( cmdQueue , cmd );

			if ( ! cmd.requiresACK() ) { // don't wait , we'll never receive this one anyway
				return;
			}

			if ( DEBUG ) {
				System.out.println("[emulator "+emulatorId+"] Waiting for ack to: "+cmd);
			}
			do 
			{
				final Long cmdId = ackQueue.peek();
				if ( cmdId != null && cmdId.longValue() == cmd.getId() ) 
				{
					safeTake( ackQueue );
					return;
				}
				try {
					java.lang.Thread.sleep(100);
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			} while ( true );  		    
		}
		
	    private <T> T safeTake(BlockingQueue<T> queue) 
	    {
	        while(true) 
	        {
	            try {
	                return queue.take();
	            } 
	            catch (InterruptedException e) {
	                Thread.currentThread();
	            }
	        }
	    }   		

		public double getRuntimeInSeconds() 
		{
			if ( lastStart != 0 && lastStop != 0 )
			{
				final long delta = lastStop - lastStart;
				if ( delta >= 0) {
					return delta / 1000.0d;
				}
				LOG.error("getRuntimeInSeconds(): Negative runtime ? "+delta+" ( lastStart: "+lastStart+" / lastStop: "+lastStop,new Exception());
				throw new RuntimeException("Unreachable code reached");				
			} else if ( lastStart != 0 ) {
				return ( (double) System.currentTimeMillis() - (double) lastStart) / 1000.0d;
			} else if ( lastStart == 0 && lastStop == 0 ) {
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
			if ( lastStart != 0 )
			{
				final double runtime = getRuntimeInSeconds();
				if ( runtime != 0.0d ) {
					return ((double)cycleCountAtLastStop - (double) cycleCountAtLastStart ) / getRuntimeInSeconds();
				}
			}
			return 0.0d;
		}
		
		private Command waitForStopCommand()
		{
			return waitForCommand(false);
		}
		
		private Command waitForStartCommand()
		{
			return waitForCommand(true);
		}		

		private Command waitForCommand(boolean expectingStartCommand) 
		{
			if ( DEBUG ) {
				System.out.println("[emulator "+emulatorId+"] Waiting for "+(expectingStartCommand? " START command " : "STOP command"));
			}
			
			while ( true ) 
			{
				final Command result = safeTake( cmdQueue );
				
				// note that everything that is NOT a stop/terminate command
				// is considered to be a START command so we need to adjust
				// the speed here before checking Command#isStartCommand()
				if ( result.hasType( CommandType.SPEED_CHANGE ) ) {
					currentSpeed = (EmulationSpeed) result.getPayload();
					out.info("Emulation speed changed changed to "+currentSpeed);
					acknowledgeCommand( result );
					continue;
				}
				
				if ( ( expectingStartCommand && result.isStartWorkerMainLoopCommand() ) ||
					 ( ! expectingStartCommand && result.isStopCommand() ) ) 
				{
					if ( DEBUG ) {
						System.out.println("[emulator "+emulatorId+"] Got "+(expectingStartCommand? " START command " : "STOP command"));
					}
					return result;
				}
				if ( DEBUG ) {
					System.out.println("[emulator "+emulatorId+"] Ignoring unexpected command: "+result);
				}
				acknowledgeCommand( result );
			}
		}		

		private void acknowledgeCommand(Command cmd) 
		{
			if ( cmd.requiresACK() ) {
				if ( DEBUG ) {
					System.out.println("[emulator "+emulatorId+"] Acknowledging "+cmd);
				}
				safePut( ackQueue , cmd.getId() );
			}
		}
	}

	
	public <T> T doWithEmulator(IEmulatorInvoker<T> invoker) 
	{
		synchronized( CPU_LOCK ) 
		{
			return invoker.doWithEmulator( this , cpu , memory );
		}
	}
	
	/**
	 * 
	 * @return number of DCPU-16 cycles the command execution took 
	 */
	protected int internalExecuteOneInstruction() 
	{
		beforeCommandExecution();

		int execDurationInCycles=-1;
		try 
		{
			boolean success = false;
			synchronized( CPU_LOCK ) 
			{
				try 
				{
					execDurationInCycles = cpu.executeInstruction();
	
					lastValidInstruction = visibleCPU.pc;
					
					if ( checkMemoryWrites ) {
						// note-to-self: I cannot simply do ( currentPC - previousPC ) here because 
						// the instruction might've been a JSR or ADD PC, X / SUB PC,Y or
						// a jump into an interrupt handler that skipped over non-instruction memory
						final int sizeInWords = calculateInstructionSizeInWords( visibleCPU.pc.getWordAddressValue() , memory );
						memory.writeProtect( new AddressRange( visibleCPU.pc , Size.words( sizeInWords )) );
					}
					
					cpu.currentCycle+=execDurationInCycles;
					cpu.pc = Address.wordAddress( cpu.currentInstructionPtr );
					
					cpu.maybeProcessOneInterrupt(); 
					
					success = true;
				} 
				finally 
				{
					if ( success ) {
						// TODO: Any asynchronous register changes done by hardware in the meantime get lost here ...
						visibleCPU.populateFrom( cpu );
					} else {
					    // restore CPU register state on error
					    cpu.populateFrom(visibleCPU);
					}
				}
			}
		} 
		catch(EmulationErrorException e) {
			stop( e );
			LOG.error( "internalExecuteOneInstruction(): Emulation error - "+e.getMessage()); 
			out.warn("Simulation stopped due to a program error ( at address ."+visibleCPU.pc+")",e);
			return 0;		    
		}
		catch(Exception e) {
			stop( e );
			LOG.error( "internalExecuteOneInstruction(): Internal error - "+e.getMessage(),e); 
			out.error("Simulation stopped due to an internal error ( at address ."+visibleCPU.pc+")");
			return 0;
		} 
		finally 
		{
			afterCommandExecution( execDurationInCycles , cpu );
		}
		return execDurationInCycles;
	}

	@Override
	public void executeOneInstruction() 
	{
		stop(null);
	    internalExecuteOneInstruction();
	}   

	protected void beforeCommandExecution() 
	{
		listenerHelper.invokeBeforeCommandExecutionListeners( this.clockThread.isRunnable.get() );
	}

	/**
	 * 
	 * @param executedCommandDuration duration (in cycles) of last command or -1 if execution failed with an internal emulator error
	 */
	protected void afterCommandExecution(final int executedCommandDuration,CPU hiddenCPU) 
	{
		// invoke listeners
		listenerHelper.invokeAfterCommandExecutionListeners( clockThread.isRunnable.get() , executedCommandDuration );

		// check whether we reached a breakpoint
		maybeHandleBreakpoint(hiddenCPU);
	}

	private void maybeHandleBreakpoint(CPU hiddenCPU) 
	{
		/*
		 * We can have at most 2 breakpoints at any address,
		 * one regular (user-defined) breakpoint and
		 * one internal breakpoint used by stepReturn() 
		 */
		Breakpoint regularBP = null;
		Breakpoint oneShotBP = null;

		synchronized( breakpoints ) 
		{
			final List<Breakpoint> candidates = breakpoints.get( hiddenCPU.pc ); 

			if ( candidates == null || candidates.isEmpty() ) 
			{
				return;
			}		

			for ( Breakpoint bp : candidates ) 
			{
				if ( bp.matches( this ) ) 
				{
					if ( bp.isOneShotBreakpoint() ) {
						if ( oneShotBP == null ) {
							oneShotBP = bp;
						} else {
							throw new RuntimeException("Internal error, more than one internal breakpoint at "+bp.getAddress());
						}
					} else {
						if ( regularBP == null ) {
							regularBP = bp;
						} else {
							throw new RuntimeException("Internal error, more than one regular breakpoint at "+bp.getAddress());
						}
					}
				}
			}
		}

		if ( regularBP != null || oneShotBP != null ) 
		{
			// stop() will also remove ANY one-shot breakpoints from the list
			stop(null);

			if ( regularBP != null ) // only notify client code about the regular BP, internal BP is invisible to the user
			{
				final Breakpoint finalRegularBP = regularBP; // Closures can only access stuff declared final...
				listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

					@Override
					public void invoke(IEmulator emulator, IEmulationListener listener)
					{
						listener.onBreakpoint( emulator , finalRegularBP );
					}
				});         
			} 
		}
	}

	private boolean isConditionalInstruction(int instructionWord) {

		final int opCode = (instructionWord & 0x1f);
		return ( opCode >= 0x10 && opCode <= 0x17);
	}

   @Override
    public void skipCurrentInstruction() 
    {
       synchronized(CPU_LOCK) 
       {
           int adr = cpu.pc.getWordAddressValue();
           adr += calculateInstructionSizeInWords( adr , memory );
           cpu.pc = Address.wordAddress( adr );
           visibleCPU.populateFrom( cpu );
       }
       afterCommandExecution( 0 , visibleCPU );
    }
   
    public static int calculateInstructionSizeInWords(Address address,IReadOnlyMemory memory) {
        return calculateInstructionSizeInWords(address.getWordAddressValue() , memory );
    }
    
	public static int calculateInstructionSizeInWords(int address,IReadOnlyMemory memory) {

		@SuppressWarnings("deprecation")
        final int instructionWord = memory.read( address );

		final int opCode = (instructionWord & 0x1f);

		int instructionSizeInWords=1; // +1 word for instruction itself
		switch( opCode ) 
		{
			case 0x00: // skip special opcode
				instructionSizeInWords += getOperandsSizeInWordsForSpecialInstruction( instructionWord );
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
				instructionSizeInWords += getOperandsSizeInWordsForBasicInstruction(instructionWord);
				break;
			case 0x18: // UNKNOWN
			case 0x19: // UNKNOWN;
				instructionSizeInWords += getOperandsSizeInWordsForUnknownInstruction( instructionWord );
				break;
			case 0x1a: // ADX
			case 0x1b: // SBX
				instructionSizeInWords += getOperandsSizeInWordsForBasicInstruction(instructionWord);
				break;                
			case 0x1c: // UNKNOWN
			case 0x1d: // UNKNOWN
				instructionSizeInWords += getOperandsSizeInWordsForUnknownInstruction( instructionWord );
				break;
			case 0x1e: // STI
			case 0x1f: // STD
				instructionSizeInWords += getOperandsSizeInWordsForBasicInstruction( instructionWord );
				break;
			default:
				instructionSizeInWords += getOperandsSizeInWordsForUnknownInstruction( instructionWord );
				break;
		}
		return instructionSizeInWords;
	}

	private static int getOperandsSizeInWordsForBasicInstruction(int instructionWord)
	{
		// PC is already pointing at word AFTER current instruction here !
		return getOperandSizeInWords(OperandPosition.SOURCE_OPERAND,instructionWord,false)+getOperandSizeInWords(OperandPosition.TARGET_OPERAND,instructionWord,false) ;
	}

	private static int getOperandsSizeInWordsForUnknownInstruction(int instructionWord)
	{
		// PC is already pointing at word AFTER current instruction here !
		return 0;
	}

	private static int getOperandsSizeInWordsForSpecialInstruction(int instructionWord)
	{
		// PC is already pointing at word AFTER current instruction here !        
		return  getOperandSizeInWords(OperandPosition.SOURCE_OPERAND,instructionWord,true);
	}

	private static int getOperandSizeInWords(OperandPosition position, int instructionWord,boolean isSpecialOpCode) {

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

	@Override
	public boolean canStepReturn() {
		return isJSR( memory.read( cpu.pc ) );
	}

	@Override
	public void stepReturn() 
	{
		if ( ! canStepReturn() ) {
			throw new IllegalStateException("PC is not at a JSR instruction, cannot skip return");
		}

		final int currentInstructionSizeInWords = calculateInstructionSizeInWords( cpu.pc.getWordAddressValue() , memory );
		final Address nextInstruction = cpu.pc.plus( Size.words( currentInstructionSizeInWords ) , true );
		addBreakpoint( new OneShotBreakpoint( nextInstruction ) );
		start();
	}

	protected static boolean isJSR(int instructionWord) {

		final int basicOpCode = (instructionWord & 0x1f);
		if ( basicOpCode != 0 ) {
			return false;
		}
		final int specialOpCode = ( instructionWord >>> 5 ) &0x1f;
		return specialOpCode == 0x01; // JSR
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

	protected static final class OperandDesc 
	{
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

	protected static OperandDesc operandDesc(int value) {
		return new OperandDesc( value );
	}

	protected static OperandDesc operandDesc(int value,int cycleCount) {
		return new OperandDesc( value , cycleCount );
	}

	/* (non-Javadoc)
	 * @see de.codesourcery.jasm16.emulator.IEmulator#loadMemory(de.codesourcery.jasm16.Address, byte[])
	 */
	@Override
	public void loadMemory(final Address startingOffset, final byte[] data) 
	{
		stop(null);

		if ( clockThread.isRunnable.get() ) {
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

		out.info("Measuring delay loop...");
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
		out.info("one delay-loop iteration = "+nanosPerDelayLoopExecution+" nanoseconds.");
		final double loopIterationsPerCycle = expectedNanosPerCycle / nanosPerDelayLoopExecution;

		clockThread.adjustmentFactor = 1.0d;
		clockThread.oneCycleDelay = (int) Math.round( loopIterationsPerCycle );

		out.info("one CPU cycle = "+clockThread.oneCycleDelay+" delay-loop iterations.");
	}

	@Override
	public ICPU getCPU()
	{
		synchronized( CPU_LOCK ) {
			return visibleCPU;
		}
	}

	@Override
	public IMemory getMemory()
	{
		return memory;
	}

	@Override
	public void addEmulationListener(IEmulationListener listener)
	{
		listenerHelper.addEmulationListener( listener );
	}

	public void removeAllEmulationListeners() {
		listenerHelper.removeAllEmulationListeners();
	}

	@Override
	public void removeEmulationListener(IEmulationListener listener)
	{
		listenerHelper.removeEmulationListener( listener );
	}

	private Breakpoint extractRegularBreakpoint(List<Breakpoint> bps) 
	{
		Breakpoint result = null;
		for ( Breakpoint bp : bps ) 
		{
			if ( ! bp.isOneShotBreakpoint() ) 
			{
				if ( result != null ) {
					throw new IllegalStateException("More than one " +
							" regular breakpoint at address "+bp.getAddress());
				}
				result = bp;
			}
		}
		return result;
	}	

	@Override
	public void addBreakpoint(final Breakpoint bp)
	{
		if (bp == null) {
			throw new IllegalArgumentException("breakpoint must not be NULL.");
		}

		Breakpoint replacedBreakpoint;
		synchronized( breakpoints ) 
		{
			List<Breakpoint> list = breakpoints.get( bp.getAddress() );
			if ( list == null ) {
				list = new ArrayList<Breakpoint>();
				breakpoints.put( bp.getAddress() , list );
			} 
			replacedBreakpoint = extractRegularBreakpoint( list );
			if ( replacedBreakpoint != null ) {
				list.remove( replacedBreakpoint );
			}
			list.add( bp );
		}  

		// notify listeners
		if ( replacedBreakpoint != null && ! replacedBreakpoint.isOneShotBreakpoint() ) {
			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.breakpointDeleted( emulator , bp );
				}
			});         	
		}

		if ( ! bp.isOneShotBreakpoint() ) 
		{
			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.breakpointAdded( emulator , bp );
				}
			});        
		}
	}

	@Override
	public void breakpointChanged(final Breakpoint bp) {

		Breakpoint existing;
		synchronized( breakpoints ) {
			final List<Breakpoint> list = breakpoints.get( bp.getAddress() );
			if ( list != null ) {
				existing = extractRegularBreakpoint( list );
			} else {
				existing = null;
			}
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
			final List<Breakpoint> list = breakpoints.get( bp.getAddress() );
			final int idx = list.indexOf( bp );
			if ( idx != -1 ) {
				existing = list.remove( idx );
			} else {
				existing = null;
			}
			if ( list.isEmpty() ) {
				breakpoints.remove( bp.getAddress() );
			}
		}      
		// notify listeners
		if ( existing != null && ! existing.isOneShotBreakpoint() ) {
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
		final List<Breakpoint> result = new ArrayList<Breakpoint>();
		synchronized( breakpoints ) {
			for ( List<Breakpoint> subList : breakpoints.values() ) {
				for ( Breakpoint bp : subList ) {
					if ( ! bp.isOneShotBreakpoint() ) {
						result.add( bp );
					}
				}
			}
		}
		return result;
	}

	@Override
	public Breakpoint getBreakPoint(Address address)
	{
		if (address == null) {
			throw new IllegalArgumentException("address must not be NULL.");
		}
		synchronized( breakpoints ) {
			final List<Breakpoint>  list = breakpoints.get( address );
			if ( list == null ) {
				return null;
			}			
			return extractRegularBreakpoint( list );
		}
	}

	@Override
	public void unmapRegion(IMemoryRegion region) {
		this.memory.unmapRegion( region );
		if ( out.isDebugEnabled() ) {
			out.debug("Unmapped memory region "+region);
			this.memory.dumpMemoryLayout(out);
		}
	}

	@Override
	public void mapRegion(IMemoryRegion region) 
	{
		this.memory.mapRegion( region );
		if ( out.isDebugEnabled() ) {
			out.debug("Mapped memory region "+region);
			this.memory.dumpMemoryLayout( out );
		}
	}

	@Override
	public boolean triggerInterrupt(IInterrupt interrupt) 
	{
		return cpu.triggerInterrupt( interrupt );
	}

	public int addOrReplaceDevice(IDevice device) throws DeviceErrorException 
	{
		if (device == null) {
			throw new IllegalArgumentException("device must not be null");
		}

		int existingSlot = -1;
		IDevice existingDevice = null;
		synchronized( devices ) 
		{
			existingSlot = findDeviceSlotByDescriptor(  device.getDeviceDescriptor() );
			if ( existingSlot != -1 ) {
				existingDevice = devices.get( existingSlot );
			}
		}

		final boolean requiresAdd;
		if ( existingDevice != null ) 
		{
			// call beforeRemoveDevice() outside of synchronized block
			existingDevice.beforeRemoveDevice( this );

			synchronized( devices ) 
			{
				existingSlot = devices.indexOf( existingDevice );
				if ( existingSlot == -1 ) {
					requiresAdd = true;
				} else {
					devices.remove( existingSlot );
					devices.add( existingSlot , device );
					requiresAdd = false;
				}
			}
		} else {
			requiresAdd = true;
		}

		if ( requiresAdd ) {
			return addDevice( device );        	
		} 
		// replaced
		device.afterAddDevice( this );
		return existingSlot;
	}

	public List<IDevice> getDevicesByDescriptor(DeviceDescriptor desc) 
	{
		if (desc == null) {
			throw new IllegalArgumentException("descriptor must not be null");
		}
		final List<IDevice> result = new ArrayList<>();
		synchronized( devices ) 
		{
			for ( IDevice device : devices ) 
			{
				if ( device.getDeviceDescriptor().matches( desc ) ) {
					result.add( device );
				}
			}
		}
		return result;
	}

	private int findDeviceSlotByDescriptor(DeviceDescriptor descriptor) 
	{
		int existingSlot = -1;
		synchronized( devices ) 
		{
			int i = 0;
			for ( IDevice existing : devices ) 
			{
				if ( existing.getDeviceDescriptor().matches( descriptor) ) 
				{
					if ( existingSlot != -1 ) {
						throw new IllegalStateException("Found more than one existing device with descriptor "+descriptor );            			
					}
					existingSlot = i; 
				}
				i++;
			}
		}
		return existingSlot;
	}

	@Override
	public int addDevice(IDevice device) throws DeviceErrorException
	{
		if (device == null) {
			throw new IllegalArgumentException("device must not be null");
		}
		final int slotNo;
		synchronized( devices ) 
		{
			if ( ! device.supportsMultipleInstances() &&
					findDeviceSlotByDescriptor( device.getDeviceDescriptor() ) != -1 ) 
			{
				throw new IllegalStateException("Already one instance of device "+device.getDeviceDescriptor()+" registered.");
			}

			if ( devices.size() >= 65535 ) {
				throw new IllegalStateException("Already 65535 devices registered");
			}
			slotNo = devices.size();
			devices.add( device );
			out.debug("Added device "+device);
			printDevices();
		}

		boolean success = false;
		try {
			device.afterAddDevice( this );
			success = true;
		} 
		catch(RuntimeException e) {
			if ( e instanceof DeviceErrorException ) {
				throw e;
			}
			LOG.error("Device "+device+" failed in afterAddDevice() call",e);
			throw new DeviceErrorException( "afterAddDevice() failed: "+e.getMessage() , device , e );
		}
		finally 
		{
			if ( ! success ) {
				synchronized( devices ) {
					devices.remove( device );
				}
			}
		}
		return slotNo;
	}

	private void printDevices() 
	{
		synchronized( devices ) {

			int slot = 0;
			for ( IDevice d : devices ) {
				out.debug("Slot #"+slot+":");
				out.debug( d.getDeviceDescriptor().toString("    ",true));
				slot++;
			}		
		}
	}

	@Override
	public List<IDevice> getDevices() {
		synchronized( devices ) {
			return new ArrayList<IDevice>( devices );
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
			try {
				device.beforeRemoveDevice( this );
			} 
			catch(RuntimeException e) 
			{
				if ( e instanceof DeviceErrorException) {
					throw (DeviceErrorException) e;
				}
				LOG.error("Device "+device+" failed in beforeRemoveDevice() call",e);
				throw new DeviceErrorException( "breforeRemoveDevice() failed: "+e.getMessage() , device , e );        		
			}
		} else {
			return;
		}

		synchronized( devices ) {
			devices.remove( device );
		}

		if ( isRegistered ) {
			out.debug("Removed device "+device);
			printDevices();
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
		
		final boolean wasRunning = stop();
		
		clockThread.changeSpeed( newSpeed );
		this.emulationSpeed = newSpeed;  		

		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.onEmulationSpeedChange( oldSpeed , newSpeed );
			}
		});           
		
		if ( wasRunning ) {
			start();
		}
	}

	@Override
	public boolean isCalibrated() {
		return clockThread.oneCycleDelay != -1;
	}

	@Override
	public Throwable getLastEmulationError()
	{
		return lastEmulationError;
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

	@Override
	public synchronized void dispose()
	{
		out.info("Disposing Emulator ...");
		stop();

		// terminate clock thread
		clockThread.sendToClockThread( Command.terminateClockThread() );

		// notify listeners and remove them afterwards
		listenerHelper.emulatorDisposed();

		// remove all devices
		for ( IDevice d : getDevices() ) 
		{
			try {
				removeDevice( d );
			} 
			catch(Exception e) {
				LOG.error("dispose(): Failed to remove "+d);
			}
		}
		out.info("Emulator disposed.");
	}

	@Override
	public void setOutput(ILogger out)
	{
		if (out == null) {
			throw new IllegalArgumentException("out must not be null");
		}
		this.loggerDelegate = out;
	}

	@Override
	public ILogger getOutput()
	{
		return out;
	}

	@Override
	public void setMemoryProtectionEnabled(boolean enabled)
	{
		checkMemoryWrites = enabled;
		memory.setCheckWriteAccess( enabled );
	}

	@Override
	public boolean isMemoryProtectionEnabled()
	{
		return checkMemoryWrites;
	}

	@Override
	public void setIgnoreAccessToUnknownDevices(boolean yesNo)
	{
		this.ignoreAccessToUnknownDevices = yesNo;
	}
	
    protected final class CPU implements ICPU {

        // transient, only used inside of executeOneInstruction() code path
        public int currentInstructionPtr;
        
        public final MainMemory memory;
        
        /* Register A = Index 0
         * Register B = Index 1
         * Register C = Index 2
         * Register X = Index 3
         * Register Y = Index 4
         * Register Z = Index 5
         * Register I = Index 6
         * Register J = Index 7      
         */
        public final int[] commonRegisters = new int[ 8 ];

        public int ex;

        public Address pc;
        public Address sp;
        public Address interruptAddress;

        public boolean queueInterrupts;

        public IInterrupt currentInterrupt;
        public final List<IInterrupt> interruptQueue= new ArrayList<IInterrupt>();

        public int currentCycle;

        public CPU(MainMemory memory) 
        {
            pc = sp = interruptAddress = WordAddress.ZERO;
            this.memory = memory;
        }

        public void populateFrom(CPU other) 
        {
            System.arraycopy( other.commonRegisters , 0 , this.commonRegisters , 0 , 8 );
            this.ex = other.ex;
            this.pc = other.pc;
            this.sp = other.sp;
            this.interruptAddress = other.interruptAddress;
            this.queueInterrupts = other.queueInterrupts;
            this.currentInterrupt = other.currentInterrupt;
            this.interruptQueue.addAll(other.interruptQueue);
            this.currentCycle = other.currentCycle;
        }           

        public boolean triggerInterrupt(IInterrupt interrupt) 
        {
            if ( ! interruptsEnabled() ) {
                return false;
            }

            synchronized ( interruptQueue ) 
            {
                if ( currentInterrupt == null && ! isQueueInterrupts() ) {
                    currentInterrupt  = interrupt;
                } 
                else 
                { // there's either already an IRQ waiting to be processed or the CPU is currently told to queue interrupts
                    if ( interruptQueue.size() >= INTERRUPT_QUEUE_SIZE ) 
                    {
                        throw new InterruptQueueFullException("Interrupt queue full ("+interruptQueue.size()+" entries already) , can't store "+interrupt);
                    }
                    setQueueInterrupts( true );
                    interruptQueue.add( interrupt );
                } 
            }
            return true;
        }        

        public IInterrupt getNextProcessableInterrupt(boolean removeFromQueue) 
        {
            synchronized( interruptQueue ) 
            {
                if ( currentInterrupt != null ) 
                {
                    if ( ! removeFromQueue ) {
                        return currentInterrupt;
                    }

                    final IInterrupt irq = currentInterrupt;
                    currentInterrupt = null;
                    return irq;
                } 

                if ( ! interruptQueue.isEmpty() && ! isQueueInterrupts() ) 
                {
                    if ( removeFromQueue ) {
                        return interruptQueue.remove(0);
                    } 
                    return interruptQueue.get(0);
                }
            }
            return null;
        }        

        public void reset()
        {
            currentCycle = 0;
            queueInterrupts = false;
            interruptQueue.clear();
            sp = pc = interruptAddress = WordAddress.ZERO;
            ex = 0;
            for ( int i = 0 ; i < commonRegisters.length ; i++ ) {
                commonRegisters[i]=0;
            }
        }        

        @Override
        public Address getPC() {
            return pc;
        }

        @Override
        public Address getSP() {
            return sp;
        }

        @Override
        public int getEX() {
            return ex;
        }

        @Override
        public Address getInterruptAddress() {
            return interruptAddress;
        }

        @Override
        public int getCurrentCycleCount() {
            return currentCycle;
        }
        
        @Override
        public void setRegisterValue(Register reg, int value) 
        {
            switch( reg ) 
            {
                case A:
                    commonRegisters[0] = value & 0xffff;
                    break;
                case B:
                    commonRegisters[1] = value & 0xffff;
                    break;
                case C:
                    commonRegisters[2] = value & 0xffff;
                    break;
                case EX:
                    ex = value & 0xffff;
                    break;
                case I:
                    commonRegisters[6] = value & 0xffff;
                    break;
                case J:
                    commonRegisters[7] = value & 0xffff;
                    break;
                case PC:
                    pc = Address.wordAddress( value );
                    break;
                case SP:
                    sp = Address.wordAddress( value );
                    break;
                case X:
                    commonRegisters[3] = value & 0xffff;
                    break;
                case Y:
                    commonRegisters[4] = value & 0xffff;
                    break;
                case Z:
                    commonRegisters[5] = value & 0xffff;
                    break;
                default:
                    throw new RuntimeException("Unreachable code reached: "+reg);

            }
        }

        @Override
        public int getRegisterValue(Register reg) {
            switch( reg ) {
                case A:
                    return commonRegisters[0];
                case B:
                    return commonRegisters[1];
                case C:
                    return commonRegisters[2];
                case EX:
                    return ex;
                case I:
                    return commonRegisters[6];
                case J:
                    return commonRegisters[7];
                case PC:
                    return pc.getWordAddressValue();
                case SP:
                    return sp.getWordAddressValue();
                case X:
                    return commonRegisters[3];
                case Y:
                    return commonRegisters[4];
                case Z:
                    return commonRegisters[5];
                default:
                    throw new RuntimeException("Unreachable code reached: "+reg);                   
            }
        }

        @Override
        public void setQueueInterrupts(boolean yesNo) {
            this.queueInterrupts = yesNo;
        }

        @Override
        public boolean isQueueInterrupts() {
            return queueInterrupts;
        }

        @Override
        public boolean interruptsEnabled() {
            return interruptAddress != null && interruptAddress.getValue() != 0;
        }

        @Override
        public List<IInterrupt> getInterruptQueue() {
            return interruptQueue;
        }

        // ==============
        
        public int executeInstruction() 
        {
            currentInstructionPtr = pc.getValue();
            
            final int instructionWord = readNextWordAndAdvance();
            
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
            final int cycles = 2+storeTargetOperand( instructionWord , source.value )+source.cycleCount;
            int address = --commonRegisters[6]; // registers[6]-=1; <<< I
            if ( address < 0 ) {
                commonRegisters[6] = (int) WordAddress.MAX_ADDRESS;
            }
            address = --commonRegisters[7]; // registers[7]-=1; <<< J
            if ( address < 0 ) {
                commonRegisters[7]= (int) WordAddress.MAX_ADDRESS;
            }        
            return cycles;
        }

        private int handleSTI(int instructionWord) {
            // sets b to a, then increases I and J by 1
            // a,b,c,x,y,z,i,j
            final OperandDesc source = loadSourceOperand( instructionWord );

            final int cycles = 2+storeTargetOperand( instructionWord , source.value )+source.cycleCount;

            int newWordAddress = ++commonRegisters[6]; // registers[6]+=1; <<< I
            if ( newWordAddress > WordAddress.MAX_ADDRESS ) {
                commonRegisters[6] = 0;
            }
            newWordAddress = ++commonRegisters[7]; // registers[7]+=1; <<< J      
            if ( newWordAddress > WordAddress.MAX_ADDRESS ) {
                commonRegisters[7] = 0;
            }        
            return cycles;
        }

        private int handleSBX(int instructionWord) 
        {
            // sets b to b-a+EX, sets EX to 0xFFFF if there is an under-flow, 0x0001 if there's an overflow, 0x0 otherwise

            OperandDesc source = loadSourceOperand( instructionWord );
            OperandDesc target = loadTargetOperand( instructionWord , false , false );

            boolean underflow = false;
            boolean overflow = false;

            final int b = target.value;
            final int a = source.value;

            final int step1 = b - a;
            if ( step1 < 0 ) {
                underflow = true;
            }
            final int step2 = step1 + ex;
            if ( step2 > 65535 ) {
                overflow = true;
            }

            if ( underflow ) {
                ex = 0xffff;
            } else if ( overflow ) {
                ex = 0x0001;
            } else {
                ex = 0;
            }
            return 3+storeTargetOperand( instructionWord , step2 )+source.cycleCount;       
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
            final int instructionCount = Address.calcDistanceInBytes( Address.wordAddress( 0 ) , pc ).toSizeInWords().getValue();
            List<DisassembledLine> lines = dis.disassemble( memory , Address.wordAddress( 0 ) , instructionCount , true );
            for (DisassembledLine line : lines) {
                out.info( Misc.toHexString( line.getAddress() )+": "+line.getContents());
            }
            
            Address lastValid = lastValidInstruction;
            if ( lastValid == null ) {
                lastValid = pc;
            }
            final String msg = "Unknown opcode 0x"+Misc.toHexString( instructionWord )+
                    " at address "+"0x"+Misc.toHexString( pc )+
                    " (last valid PC: "+Misc.toHexString( lastValid )+")";
            out.warn(msg );
            throw new UnknownOpcodeException( msg , instructionWord & 0xffff );
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
            final boolean skippedInstructionIsConditional = isConditionalInstruction( memory.read( currentInstructionPtr ) );

            currentInstructionPtr += calculateInstructionSizeInWords( currentInstructionPtr , memory );

            if ( skippedInstructionIsConditional ) 
            {
                currentInstructionPtr += calculateInstructionSizeInWords( currentInstructionPtr , memory );
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

        private int handleASR(int instructionWord) { // ASR b,a
            // sets b to b>>a, sets EX to ((b<<16)>>>a)&0xffff (arithmetic shift) (treats b as signed)
            OperandDesc source = loadSourceOperand( instructionWord );      
            OperandDesc target = loadTargetOperand( instructionWord , false , false );

            final int acc = signed( target.value ) >> source.value;
            ex = (( signed( target.value ) << 16) >>> source.value ) & 0xffff;
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

            final int a = signed( target.value );
            final int b = signed( source.value );

            final int acc = a * b;
            ex = ((a * b) >> 16 ) & 0xffff;       
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
            ex = ((target.value * source.value) >> 16 ) & 0xffff;
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
                    // HCF was removed in spec 1.7
                    //          return handleHCF( instructionWord ); 
                    return handleUnknownOpCode( instructionWord );
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

            final int cyclesConsumed;
            if ( device == null ) 
            {
                if ( ! ignoreAccessToUnknownDevices ) {
                    LOG.error("handleHWI(): No device at slot #"+hardwareSlot);
                    out.warn("No device at slot #"+hardwareSlot);
                    throw new InvalidDeviceSlotNumberException("No device at slot #"+hardwareSlot);
                }
                cyclesConsumed = 0;
            } 
            else 
            {
                try {
                    cyclesConsumed = device.handleInterrupt( Emulator.this );
                } 
                catch(RuntimeException e) {
                    if ( e instanceof DeviceErrorException) {
                        throw e;
                    }
                    LOG.error("handleHWI(): Device "+device+" failed in handleInterrupt(): "+e.getMessage() , e );
                    throw new DeviceErrorException("Device "+device+" failed in handleInterrupt(): "+e.getMessage() , device , e );
                }
            }
            return 4+operand.cycleCount+cyclesConsumed;
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
                commonRegisters[ operandBits ] = value & 0xffff;
                return 0;
            }
            if ( operandBits <= 0x0f ) {
                memory.write( commonRegisters[ operandBits - 0x08 ] , value);
                return 1;
            }
            if ( operandBits <= 0x17 ) {
                final int nextWord = readNextWordAndAdvance();
                writeMemoryWithOffsetAndWrapAround( commonRegisters[ operandBits - 0x10 ] , nextWord , value);
                return 1;
            }
            switch( operandBits ) {
                case 0x18: // (PUSH / [--SP]) if in b, or (POP / [SP++]) if in a
                    push( value );
                    return 0;
                case 0x19: // PEEK/[SP]
                    memory.write( sp , value );
                    return 0;
                case 0x1a:
                    int nextWord = readNextWordAndAdvance();
                    Address dst = sp.plus( Address.wordAddress( nextWord ) , true);
                    memory.write( dst , value );
                    return 1;
                case 0x1b:
                    sp = Address.wordAddress( value );
                    return 0;
                case 0x1c:
                    currentInstructionPtr = value;
                    return 0;
                case 0x1d:
                    ex = value;
                    return 0;
                case 0x1e:
                    nextWord = readNextWordAndAdvance();
                    memory.write( nextWord , value);
                    return 1;
                default:
                    return handleIllegalTargetOperand(instructionWord); // assignment to literal value
            }
        }
        
        private int readNextWordAndAdvance() {
            final int word = memory.read( currentInstructionPtr );
            currentInstructionPtr = (currentInstructionPtr+1) & 0xffff;
            return word;
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
                return operandDesc( commonRegisters[ operandBits ] );
            }
            if ( operandBits <= 0x0f ) {
                return operandDesc( memory.read( commonRegisters[ operandBits - 0x08 ] ) , 1 );
            }
            if ( operandBits <= 0x17 ) {
                final int nextWord = readNextWordAndAdvance();
                return operandDesc( 
                        readMemoryWithOffsetAndWrapAround( 
                                commonRegisters[ operandBits - 0x10 ] , nextWord ) ,1 );
            }
            switch( operandBits ) {
                case 0x18: // (PUSH / [--SP]) if in b, or (POP / [SP++]) if in a
                    final OperandDesc tmp = operandDesc( memory.read( sp ) );
                    sp = sp.incrementByOne(true);                
                    return tmp;
                case 0x19:
                    return operandDesc( memory.read( sp ) , 1 );
                case 0x1a:
                    int nextWord = readNextWordAndAdvance();
                    final Address dst = sp.plus( Address.wordAddress( nextWord ) , true );
                    return operandDesc( memory.read( dst ) , 1 );
                case 0x1b:
                    return operandDesc( sp.getValue() );
                case 0x1c:
                    return operandDesc( pc.getValue() );
                case 0x1d:
                    return operandDesc( ex );
                case 0x1e:
                    nextWord = readNextWordAndAdvance();
                    return operandDesc( memory.read( nextWord ) ,1 );
                case 0x1f:
                    return operandDesc( readNextWordAndAdvance() , 1 );
            }

            // literal value: -1...30 ( 0x20 - 0x3f )
            return operandDesc( operandBits - 0x21 , 0 ); 
        }   

        private int readMemoryWithOffsetAndWrapAround(int address,int offset) {
            int realAddress =(int) ( ( address + offset ) % (WordAddress.MAX_ADDRESS +1 ) );
            return memory.read( realAddress );
        }

        private void writeMemoryWithOffsetAndWrapAround(int address,int offset,int value) {
            int realAddress =(int) ( ( address + offset ) % (WordAddress.MAX_ADDRESS +1 ) );
            memory.write( realAddress , value );
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
                return operandDesc( commonRegisters[ operandBits ] );
            }
            if ( operandBits <= 0x0f ) {
                return operandDesc( memory.read( commonRegisters[ operandBits - 0x08 ] ) , 1 );
            }
            if ( operandBits <= 0x17 ) 
            {
                final int nextWord;
                if ( performIncrementDecrement ) {
                    nextWord = readNextWordAndAdvance();
                } else {
                    nextWord = memory.read( currentInstructionPtr );                
                }
                return operandDesc( readMemoryWithOffsetAndWrapAround(  commonRegisters[ operandBits - 0x10 ] , nextWord ) ,1 );
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
                        nextWord = readNextWordAndAdvance();
                    } else {
                        nextWord = memory.read( currentInstructionPtr );                    
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
                        nextWord = readNextWordAndAdvance();
                    } else {
                        nextWord = memory.read( currentInstructionPtr );
                    }
                    return operandDesc( memory.read( nextWord ) ,1 );
                case 0x1f:
                    if ( performIncrementDecrement ) {
                        nextWord = readNextWordAndAdvance();
                    } else {
                        nextWord = memory.read( currentInstructionPtr );                    
                    }
                    return operandDesc( nextWord , 1 );
            }

            // literal value: -1...30 ( 0x20 - 0x3f )
            return operandDesc( operandBits - 0x21 , 0 ); 
        }
        
        private int handleHWQ(int instructionWord) {

            // Sets A, B, C, X, Y registers to information about hardware a.

            final OperandDesc operand = loadSourceOperand(instructionWord);
            final int hardwareSlot = operand.value;

            final IDevice device = getDeviceForSlot( hardwareSlot );
            if ( device == null ) 
            {
                if ( ! ignoreAccessToUnknownDevices ) {
                    LOG.error("handleHWQ(): No device at slot #"+hardwareSlot);
                    out.warn("No device at slot #"+hardwareSlot);
                    throw new InvalidDeviceSlotNumberException("No device at slot #"+hardwareSlot);
                }

                commonRegisters[0] = 0xffff;
                commonRegisters[1] = 0xffff;
                commonRegisters[2] = 0xffff;
                commonRegisters[3] = 0xffff;
                commonRegisters[4] = 0xffff;
            } 
            else {

                /* sets A, B, C, X, Y registers to information about hardware a
                 * 
                 * A+(B<<16) is a 32 bit word identifying the hardware id
                 * C is the hardware version
                 * X+(Y<<16) is a 32 bit word identifying the manufacturer
                 */

                /* A+(B<<16) is a 32 bit word identifying the hardware id
                 * 
                 * A = LSB hardware ID (16 bit)
                 * B = MSB hardware ID (16 bit)
                 * C = hardware version
                 * X = LSB manufacturer ID (16 bit)
                 * Y = MSB manufacturer ID (16 bit)
                 */
                final DeviceDescriptor descriptor = device.getDeviceDescriptor();

                commonRegisters[0] = (int) descriptor.getID() & 0xffff;
                commonRegisters[1] = (int) ( ( descriptor.getID() >>> 16 ) & 0xffff );
                commonRegisters[2] = descriptor.getVersion() & 0xffff;
                commonRegisters[3] = (int) descriptor.getManufacturer() & 0xffff;
                commonRegisters[4] = (int) ( ( descriptor.getManufacturer() >>> 16 ) & 0xffff );  
            }

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
            setQueueInterrupts( operand.value != 0 );
            return 2+operand.cycleCount;
        }

        private int handleRFI(int instructionWord) 
        {
            /*
             *  3 | 0x0b | RFI a | disables interrupt queueing, pops A from the stack, then 
       |      |       | pops PC from the stack
             */
            setQueueInterrupts( false );
            commonRegisters[0] = pop(); // pop A from stack
            currentInstructionPtr = pop(); // pop PC from stack
            return 3;
        }   
        
        private int handleIllegalTargetOperand(int instructionWord) 
        {
            final String msg = "Illegal target operand in instruction word 0x"+
                    Misc.toHexString( instructionWord )+" at address 0x"+Misc.toHexString( pc );
            LOG.error("handleIllegalTargetOperand(): "+msg);
            out.warn( msg);
            throw new InvalidTargetOperandException( msg );
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
            triggerInterrupt(new SoftwareInterrupt( operand.value ));
            return 4+operand.cycleCount;
        }

        private int handleJSR(int instructionWord) 
        {
            // pushes the address of the next instruction to the stack, then sets PC to a
            OperandDesc source= loadSourceOperand( instructionWord );
            push( currentInstructionPtr );
            currentInstructionPtr = source.value;
            return 3+source.cycleCount;
        }        
 
        private int pop() 
        {
            // SET a, [SP++]
            final int result = memory.read( sp );
            sp= sp.incrementByOne(true);
            return result;
        }

        private void push(int value) 
        {
            // SET [--SP] , blubb
            sp = sp.decrementByOne();
            memory.write( sp , value & 0xffff );
        }           
        
        public boolean maybeProcessOneInterrupt() 
        {
            if ( interruptsEnabled() ) 
            { 
                final IInterrupt irq = getNextProcessableInterrupt( true );
                if ( irq != null ) {
                    processInterrupt( irq );
                    return true;
                }
            }
            return false;
        }     

        public void processInterrupt(IInterrupt irq) 
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
            push( commonRegisters[0] );

            pc = interruptAddress;
            commonRegisters[0] = irq.getMessage() & 0xffff;
        }        
    } // end of class: CPU	
}