package de.codesourcery.jasm16.emulator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.emulator.Emulator.IEmulationListenerInvoker;

/**
 * Helper to manage IEmulationListeners. 
 * This class manages multiple internal lists , each for a specific listener type.
 * That way we avoid having to look-up listeners with a specific type each
 * time a notification needs to be send.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
final class ListenerHelper 
{
	private static final Logger LOG = Logger.getLogger( BreakpointHelper.class );
	
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
	
	private final IEmulationListenerInvoker AFTER_COMMAND_INVOKER = new IEmulationListenerInvoker() {

		@Override
		public void invoke(IEmulator emulator, IEmulationListener listener)
		{
			listener.afterCommandExecution( emulator );
		}
	}; 	
	
	private final IEmulator emulator;
	
	public ListenerHelper(IEmulator emulator) {
		this.emulator = emulator;
	}

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

	public void invokeAfterCommandExecutionListeners(boolean continousMode) 
	{
		final List<IEmulationListener> toCall;
		if ( continousMode ) 
		{
			toCall = continuousModeAfterCommandExecListeners;
		} 
		else 
		{
			toCall = afterCommandExecListeners;
		}
		notifyListeners( AFTER_COMMAND_INVOKER , toCall );
	}

	public void invokeBeforeCommandExecutionListeners(boolean continousMode) 
	{
		final List<IEmulationListener> toCall;
		if ( continousMode ) {
			toCall = continuousModeBeforeCommandExecListeners;
		} else {        	
			toCall = beforeCommandExecListeners;
		}
		notifyListeners( BEFORE_COMMAND_INVOKER , toCall );
	}   
	
	public void notifyListeners(IEmulationListenerInvoker invoker,List<IEmulationListener> listeners) 
	{
		final List<IEmulationListener> copy;
		synchronized( emuListeners )
		{
			if ( listeners.isEmpty() ) {
				return;
			}
			// create safe copy so we don't need to hold the lock while invoking the an alien method
			copy = new ArrayList<IEmulationListener>( listeners );
		}    	
		final int len = copy.size(); 
		for ( int i = 0 ; i < len ; i++) 
		{
			final IEmulationListener l = copy.get(i);
			try {
				invoker.invoke( emulator , l );
			}
			catch(Exception e) {
				LOG.error("notifyListeners(): Listener "+l+" failed",e);					    
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