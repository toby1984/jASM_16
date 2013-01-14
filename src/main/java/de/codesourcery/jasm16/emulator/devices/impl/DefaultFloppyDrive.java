package de.codesourcery.jasm16.emulator.devices.impl;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.emulator.ICPU;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.HardwareInterrupt;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.exceptions.DeviceErrorException;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Floppy disk drive.
 * 
 * <p>The Mackapar 3.5" Floppy Drive is compatible with all standard 3.5" 1440 KB
 * floppy disks. The floppies need to be formatted in 16 bit mode, for a total of
 * 737,280 words of storage. Data is saved on 80 tracks with 18 sectors per track,
 * for a total of 1440 sectors containing 512 words each.
 * The M35FD works is asynchronous, and has a raw read/write speed of 30.7kw/s.
 * Track seeking time is about 2.4 ms per track.</p>
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class DefaultFloppyDrive implements IDevice {

	/**
	 * Emulation read speed.
	 */
	public static final int SECTORS_PER_SECOND = 60;

	/**
	 * Emulation seek speed.
	 */
	public static final float SEEK_TIME_IN_MS_PER_TRACK = 2.4f;

	/**
	 * Name: Mackapar 3.5" Floppy Drive (M35FD) 
	 * ID: 0x4fd524c5, version: 0x000b
	 * Manufacturer: 0x1eb37e91 (MACKAPAR)
	 */
	public static final DeviceDescriptor DESC = new DeviceDescriptor("Mackapar 3.5\" Floppy Drive (M35FD)" , "Floppy disk drive",
			0x4fd524c5,0x000b,0x1eb37e91);	

	private volatile boolean interruptsEnabled = false;
	private volatile int interruptMessage = 0;

	private final Object WORKER_THREAD_LOCK = new Object();

	// @GuardedBy( WORKER_THREAD_LOCK )
	private WorkerThread workerThread;

	// @GuardedBy( WORKER_THREAD_LOCK )
	private TimerThread timerThread;	

	private final Object DISK_LOCK = new Object();

	// @GuardedBy( DISK_LOCK )
	private FloppyDisk disk = null;

	// @GuardedBy( DISK_LOCK )
	private StatusCode status = StatusCode.NO_MEDIA;

	// @GuardedBy( DISK_LOCK )	
	private ErrorCode error = ErrorCode.NONE;

	private volatile IEmulator emulator;

	private volatile boolean runAtMaxSpeed = false;

	private final AtomicInteger sectorsLeftThisSecond = new AtomicInteger(SECTORS_PER_SECOND);

	protected static enum CommandType {
		TERMINATE,
		READ,
		WRITE, DISK_CHANGED;
	}

	protected static abstract class DriveCommand {

		private final CommandType type;

		protected DriveCommand(CommandType type) {
			this.type = type;
		}

		public final CommandType getType() { return type; }
		public final boolean hasType(CommandType t) { return t.equals( type ); }
	}

	protected static final class ReadCommand extends DriveCommand {

		private final int sector;
		private final Address targetMemoryAddress;

		protected ReadCommand(int sector,Address targetMemoryAddress) {
			super(CommandType.READ);
			this.sector=sector;
			this.targetMemoryAddress = targetMemoryAddress;
		}

		public int getSector() {
			return sector;
		}

		public Address getTargetMemoryAddress() {
			return targetMemoryAddress;
		}

		@Override
		public String toString() {
			return "READ_SECTOR[ sector = "+sector+" , target memory = "+Misc.toHexString( targetMemoryAddress );
		}
	}

	protected static final class WriteCommand extends DriveCommand {

		private final int sector;
		private final Address sourceMemoryAddress;

		protected WriteCommand(int sector,Address sourceMemoryAddress) {
			super(CommandType.WRITE);
			this.sector=sector;
			this.sourceMemoryAddress = sourceMemoryAddress;
		}		

		public int getSector() {
			return sector;
		}

		public Address getSourceMemoryAddress() {
			return sourceMemoryAddress;
		}

		@Override
		public String toString() {
			return "WRITE_SECTOR[ sector = "+sector+" , target memory = "+Misc.toHexString( sourceMemoryAddress );
		}		
	}	

	protected static final class TerminateCommand extends DriveCommand 
	{
		protected TerminateCommand() {
			super(CommandType.TERMINATE);
		}

		@Override
		public String toString() {
			return "TERMINATE";
		}			
	}		

	protected static final class DiskChangedCommand extends DriveCommand 
	{
		protected DiskChangedCommand() {
			super(CommandType.DISK_CHANGED);
		}

		@Override
		public String toString() {
			return "DISK_CHANGED";
		}			
	}		

	protected final class TimerThread extends Thread {

		private volatile boolean terminate = false;
		public TimerThread() {
			setDaemon( true );
			setName("floppy-timer-thread");
		}

		@Override
		public void run() {
			while ( ! terminate ) {
				sectorsLeftThisSecond.set( SECTORS_PER_SECOND );
				try {
					Thread.sleep( 1000 );
				} catch(InterruptedException e) {
					// can't help it
				}
			}
		}

		public void terminate() {
			this.terminate = true;
		}
	}

	public DefaultFloppyDrive(boolean runAtMaxSpeed) {
		this.runAtMaxSpeed = runAtMaxSpeed;
	}

	protected WorkerThread getWorkerThread() 
	{
		WorkerThread result = null;
		boolean workerStarted = false;
		synchronized(WORKER_THREAD_LOCK) 
		{
			if ( workerThread == null || ! workerThread.isAlive() ) 
			{
				WorkerThread tmp = new WorkerThread();
				tmp.start();
				workerThread = tmp;
				workerStarted = true;
			}
			result = workerThread;
		}
		if ( workerStarted ) {
			logDebug("Worker thread started.");
		}
		return result;
	}

	public FloppyDisk getDisk() {
		synchronized(DISK_LOCK ) {
			return disk;
		}
	}

	public void setRunAtMaxSpeed(boolean runAtMaxSpeed) {
		this.runAtMaxSpeed = runAtMaxSpeed;
	}

	public void setDisk(FloppyDisk disk) 
	{
		if ( disk == null ) {
			throw new IllegalArgumentException("disk must not be null");
		}
		synchronized(DISK_LOCK ) {
			this.disk = disk;
		}
		logDebug( "Disk inserted: "+disk);
		if ( emulator != null ) {
		    getWorkerThread().diskChanged();
		}
	}

	public void eject() 
	{
		boolean diskChanged = false;
		synchronized(DISK_LOCK ) 
		{
			if ( disk != null ) {
				disk = null;
				diskChanged = true;
			}
		}
		if ( diskChanged ) 
		{
			logDebug("Disk ejected");
			if ( emulator != null ) {
			    getWorkerThread().diskChanged();
			}
		}
	}

	private void updateStatus(StatusCode status) 
	{
		boolean statusChanged;
		synchronized(DISK_LOCK ) 
		{
			statusChanged = this.status != status;
			this.status = status;
		}

		if ( statusChanged ) 
		{
			logDebug("New status: "+status);
			if ( interruptsEnabled ) {
				emulator.triggerInterrupt( new HardwareInterrupt( this , interruptMessage ) );
			}
		}
	}
	
	private void updateStatus(StatusCode status,ErrorCode errorCode) 
	{
		boolean statusChanged;
		synchronized(DISK_LOCK ) 
		{
			statusChanged = this.status != status; 
			if ( errorCode != null && errorCode != this.error ) {
				statusChanged = true;
				this.error=errorCode;
			}
			this.status = status;
		}

		if ( statusChanged ) 
		{
			logDebug("New status: "+status+" / error: "+errorCode);
			if ( interruptsEnabled ) {
				emulator.triggerInterrupt( new HardwareInterrupt( this , interruptMessage ) );
			}
		}
	}	

	protected void stopWorkerThread() 
	{
		logDebug("Stopping worker thread");

		synchronized(WORKER_THREAD_LOCK) 
		{
			if ( workerThread != null && workerThread.isAlive() ) 
			{
				try {
					workerThread.terminate();
				} finally {
					workerThread = null;
				}
			}
		}
	}

	protected final class WorkerThread extends Thread {

		private final Object SLEEP_LOCK = new Object();

		private final BlockingQueue<DriveCommand> commandQueue = new ArrayBlockingQueue<DriveCommand>(1);
		private final CountDownLatch terminated = new CountDownLatch(1);

		// @GuardedBy( DISK_LOCK )
		private int currentHeadPosition = 0; // sector the disk's read/write head is currently at

		public WorkerThread() {
			setDaemon(true);
			setName("floppy-disk-worker-thread");
		}

		public void diskChanged() 
		{
			sendCommand( new DiskChangedCommand() , true );
		}

		private DriveCommand takeCommand() 
		{
			while(true) {
				try {
					return commandQueue.take();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}

		@Override
		public void run() 
		{
			logDebug("Worker thread is running.");
			try 
			{
				while ( true ) 
				{
					DriveCommand command = null;
					while( true ) 
					{
						command = commandQueue.peek();
						if ( command != null && ! command.hasType(CommandType.DISK_CHANGED ) ) 
						{
							logDebug("Worker thread received command "+command);
							break;
						}

						if ( command != null ) { // disk changed , reset head position
							logDebug("Disk changed, reset head position.");
							takeCommand();
							currentHeadPosition = 0;
						}

						setIdleStatus(null);

						while(true) 
						{
							try {
							    synchronized(SLEEP_LOCK) {
							        SLEEP_LOCK.wait(1000); // sleep for 1 second
							    }
								break;
							} catch(InterruptedException e) {
								Thread.currentThread().interrupt();
							}
						}						
					}

					if ( command.hasType( CommandType.TERMINATE ) ) 
					{
						takeCommand();
						break;
					}

					ErrorCode newErrorCode=ErrorCode.NONE;
					try 
					{
						updateStatus(StatusCode.BUSY,ErrorCode.NONE);
						newErrorCode = processCommand( command );
					} 
					catch(IOException e) 
					{
						logError("Command "+command+" failed",e);
						newErrorCode = ErrorCode.BAD_SECTOR;
					} 					
					catch(Exception e) 
					{
						logError("Command "+command+" failed",e);
						newErrorCode = ErrorCode.BROKEN;
					} 
					finally 
					{
						takeCommand();
						setIdleStatus(newErrorCode);
					}
				}
			} 
			finally {
				try {
					logDebug("Disk worker thread terminated.");
				} finally {
					terminated.countDown();
				}
			}
		}

		private void setIdleStatus(ErrorCode errorCode) 
		{
			synchronized( DISK_LOCK ) 
			{
				if ( disk == null ) {
					updateStatus(StatusCode.NO_MEDIA,errorCode);
				} else {
					updateStatus( disk.isWriteProtected() ? StatusCode.READY_WP : StatusCode.READY , errorCode );
				}
			}
		}

		private ErrorCode processCommand(DriveCommand cmd) throws IOException 
		{
			logDebug("Executing command "+cmd);

			switch( cmd.getType() ) 
			{
			case READ: /* READ */
				final ReadCommand readCmd = (ReadCommand) cmd;
				moveHead( readCmd.getSector() ); // moveHead() calls Object#sleep() , do NOT use in synchronized block

				synchronized( DISK_LOCK ) 
				{
					if ( disk != null ) 
					{
						enforceSpeed();
						// TODO: Make sure memory write is ATOMIC !!
						disk.readSector( readCmd.getSector() , emulator.getMemory() , readCmd.getTargetMemoryAddress() );
						return ErrorCode.NONE;
					}
				}
				return ErrorCode.NO_MEDIA;

			case WRITE: /* WRITE */
				final WriteCommand writeCmd = (WriteCommand) cmd;
				moveHead( writeCmd.getSector() ); // moveHead() calls Object#sleep() , do NOT use in synchronized block
				boolean writeProtected=false;
				synchronized( DISK_LOCK ) 
				{
					if ( disk != null ) 
					{
						if ( ! disk.isWriteProtected() ) 
						{
							enforceSpeed();
							// TODO: Make sure memory write is ATOMIC !!
							disk.writeSector( writeCmd.getSector() , emulator.getMemory() , writeCmd.getSourceMemoryAddress() );
							return ErrorCode.NONE;
						} 
						writeProtected = true;
					}
				}
				return writeProtected ? ErrorCode.PROTECTED : ErrorCode.NO_MEDIA;

			default:
				throw new RuntimeException("Internal error,unhandled command type "+cmd);
			}
		}

		private void enforceSpeed() 
		{
			if ( runAtMaxSpeed ) {
				return;
			}

			while( sectorsLeftThisSecond.get() <= 0 ) {
				try {
					Thread.sleep(10);
				} catch(Exception e) {
				}
			}
			sectorsLeftThisSecond.decrementAndGet();			
		}		

		private void moveHead(int sector) 
		{

			synchronized( DISK_LOCK ) 
			{
				if ( currentHeadPosition != sector ) 
				{
					final int delta = Math.abs( currentHeadPosition - sector );
					if ( ! runAtMaxSpeed ) {
						// 1 ns = 10^-9 seconds
						// 1 ms = 10^-3 seconds
						LockSupport.parkNanos( (long) SEEK_TIME_IN_MS_PER_TRACK * delta * 1000000 ); // 2.8ms per track
					}
					currentHeadPosition = sector; 
				}
			}
		}

		public boolean readSector(int sector,Address targetMemoryAddress) 
		{
			return sendCommand( new ReadCommand(sector,targetMemoryAddress ) , false );
		}

		public boolean writeSector(int sector,Address targetMemoryAddress) {
			return sendCommand( new WriteCommand(sector,targetMemoryAddress ) , false );
		}		

		private boolean sendCommand(DriveCommand cmd,boolean wait) 
		{
			boolean result;
			while( true ) {
				try {
					if ( wait ) {
						commandQueue.put( cmd );
						result = true;
					}  else {
					    result = commandQueue.offer( cmd );
					}
					break;
				} 
				catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
				}
			}
			
			if ( result ) 
			{
				synchronized (SLEEP_LOCK) {
					SLEEP_LOCK.notifyAll();
				}
			}
			return result;
		}

		public void terminate()  
		{
			sendCommand( new TerminateCommand() , true );

			while ( true ) 
			{
				try {
					terminated.await();
					break;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	/**
	 * Drive status codes.
	 *
	 * @author tobias.gierke@code-sourcery.de
	 */
	public static enum StatusCode 
	{
		NO_MEDIA(  0x0000 , "There's no floppy in the drive."),
		READY(  0x0001 , "The drive is ready to accept commands."),
		READY_WP(  0x0002 , "Same as ready, except the floppy is write protected."),
		BUSY(  0x0003 , "The drive is busy either reading or writing a sector.");

		private final int code;
		private final String description;

		private StatusCode(int code, String description) {
			this.code = code;
			this.description = description;
		}

		public int getCode() {
			return code;
		}

		public String getDescription() {
			return description;
		}
	}

	/**
	 * Error codes.
	 *
	 * @author tobias.gierke@code-sourcery.de
	 */
	public static enum ErrorCode {

		NONE(      0x0000 ,"There's been no error since the last poll."),
		BUSY(      0x0001 ,"Drive is busy performing an action"),
		NO_MEDIA(  0x0002 ,"Attempted to read or write with no floppy inserted."),
		PROTECTED( 0x0003 ,"Attempted to write to write protected floppy."),
		EJECT(     0x0004 ,"The floppy was removed while reading or writing."),
		BAD_SECTOR(0x0005 ,"The requested sector is broken, the data on it is lost."),
		BROKEN(    0xffff ,"There's been some major software or hardware problem,try turning off and turning on the device again.");	 	

		private final int code;
		private final String description;

		private ErrorCode(int code, String description) {
			this.code = code;
			this.description = description;
		}

		public int getCode() {
			return code;
		}

		public String getDescription() {
			return description;
		}
	}

	@Override
	public void afterAddDevice(IEmulator emulator) 
	{
		synchronized( WORKER_THREAD_LOCK ) {
			if ( timerThread == null || ! timerThread.isAlive() ) {
				timerThread = new TimerThread();
				timerThread.start();
			}
		}
		this.emulator = emulator;
	}

	@Override
	public void beforeRemoveDevice(IEmulator emulator) 
	{
		try {
			stopWorkerThread();
		} 
		finally 
		{
			try {
				synchronized( WORKER_THREAD_LOCK ) {
					timerThread.terminate();
					timerThread = null;
				}
			} finally {
				this.emulator = null;
			}
		}
	}	

	@Override
	public void reset() {
		interruptsEnabled = false;
		interruptMessage = 0;
		synchronized( DISK_LOCK ) 
		{
			error = ErrorCode.NONE;
			if ( disk == null ) {
			    status = StatusCode.NO_MEDIA;
			} else if ( disk.isWriteProtected() ) {
			    status = StatusCode.READY_WP;
			} else {
			    status = StatusCode.READY;
			}
		}
	}

	@Override
	public boolean supportsMultipleInstances() {
		return false;
	}

	@Override
	public DeviceDescriptor getDeviceDescriptor() {
		return DESC;
	}

	private void logError(String msg) {
	    if ( emulator != null ) {
	        emulator.getOutput().error( msg );
	    }
	}

	private void logError(String msg,Throwable t) {
	    if ( emulator != null ) {
	        emulator.getOutput().error( msg , t );
	    }
	}	

	private void logDebug(String msg) {
	    if ( emulator != null ) {
	        emulator.getOutput().debug( msg );
	    }
	}

	@Override
	public int handleInterrupt(IEmulator emulator) {

		final ICPU cpu = emulator.getCPU();
		final int msg= cpu.getRegisterValue( Register.A ); 
		switch( msg) {
		case 0:
			/*  0  Poll device. 
			 * Sets B to the current state (see below) and C to the last error
			 * since the last device poll. 
			 */
		    logDebug("Device status polled");
			cpu.setRegisterValue(Register.B , status.getCode() );
			cpu.setRegisterValue(Register.C , error.getCode() );
			synchronized(DISK_LOCK) {
				error = ErrorCode.NONE;
			}
			break;
		case 1:
			/*    
			 * 1  Set interrupt. Enables interrupts and sets the message to X if X is anything
			 *    other than 0, disables interrupts if X is 0. When interrupts are enabled,
			 *    the M35FD will trigger an interrupt on the DCPU-16 whenever the state or
			 *    error message changes.
			 */
			int irqMsg = cpu.getRegisterValue(Register.X);
			if ( irqMsg == 0 ) {
				logDebug("Interrupts disabled.");
				interruptMessage = 0;
				interruptsEnabled = false;					
			} else {
				logDebug("Interrupts enabled with message "+irqMsg);
				interruptMessage = irqMsg ;
				interruptsEnabled = true;
			}
			break;

		case 2:
			/* 2  Read sector. Reads sector X to DCPU ram starting at Y.
			 *    Sets B to 1 if reading is possible and has been started, anything else if it
			 *    fails. Reading is only possible if the state is STATE_READY or
			 *    STATE_READY_WP.
			 *    Protects against partial reads.
			 */		
	        final Address targetAddress = Address.wordAddress( cpu.getRegisterValue( Register.Y ) );
			final int readSector = cpu.getRegisterValue(Register.X);
			logDebug("Read request for sector #"+readSector+" , store at "+targetAddress);
			if ( ! isValidSector( readSector ) ) 
			{
				logError("Invalid sector number "+readSector);
				throw new DeviceErrorException("Invalid sector number "+readSector,this);
			}

			if (  getWorkerThread().readSector( readSector , targetAddress ) )
			{
				cpu.setRegisterValue(Register.B , 1 );
			} else {
				cpu.setRegisterValue(Register.B , 0 );
			}
			break;

		case 3:
			/* 3  Write sector. Writes sector X from DCPU ram starting at Y.
			 *    Sets B to 1 if writing is possible and has been started, anything else if it
			 *    fails. Writing is only possible if the state is STATE_READY.
			 *    Protects against partial writes.
			 */		
            final Address sourceAddress = Address.wordAddress( cpu.getRegisterValue( Register.Y ) );
			final int writeSector = cpu.getRegisterValue(Register.X);
			
			logDebug("Write request for sector #"+writeSector+" , read from "+sourceAddress);
			
			if ( ! isValidSector( writeSector ) ) 
			{
				logError("Invalid sector number "+writeSector);
				throw new DeviceErrorException("Invalid sector number "+writeSector,this);
			}

			if ( getWorkerThread().writeSector( writeSector , sourceAddress ) )
			{
				cpu.setRegisterValue(Register.B , 1 );
			} else {
				cpu.setRegisterValue(Register.B , 0 );
			}				
			break;
		default:
			logError("Received unknown interrupt message: "+msg);
			throw new DeviceErrorException("Received unknown interrupt message: "+msg,this);
		}

		return 0;
	}

	private boolean isValidSector(int sector) 
	{
		synchronized (DISK_LOCK) {
			if ( disk != null ) {
				return sector < disk.getSectorCount();
			}
			return true;
		}
	}
}