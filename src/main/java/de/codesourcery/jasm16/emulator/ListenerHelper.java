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
	
	private static final Object LOCK = new Object();
	
	// @GuardedBy( LOCK )
	private IEmulationListener[] allListeners = new IEmulationListener[0];

	// @GuardedBy( LOCK )
	private IEmulationListener[] beforeCommandExecListeners = new IEmulationListener[0];

	// @GuardedBy( LOCK )
	private IEmulationListener[] continuousModeBeforeCommandExecListeners = new IEmulationListener[0];

	// @GuardedBy( LOCK )
	private IEmulationListener[] afterCommandExecListeners = new IEmulationListener[0];

	// @GuardedBy( LOCK )
	private IEmulationListener[] continuousModeAfterCommandExecListeners = new IEmulationListener[0];

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
	
	private static IEmulationListener[] append(IEmulationListener[] data,IEmulationListener listener) 
	{
		IEmulationListener[] result = new IEmulationListener[data.length+1];
		System.arraycopy( data , 0 , result , 0 , data.length );
		result[data.length]=listener;
		return result;
	}

	public void addEmulationListener(IEmulationListener listener)
	{
		if (listener == null) {
			throw new IllegalArgumentException("listener must not be NULL.");
		}
		synchronized (LOCK) 
		{
			allListeners = append(allListeners, listener );
			if ( listener.isInvokeBeforeCommandExecution() ) {
				beforeCommandExecListeners = append( beforeCommandExecListeners , listener );
				if ( listener.isInvokeAfterAndBeforeCommandExecutionInContinuousMode() ) {
					continuousModeBeforeCommandExecListeners = append( continuousModeBeforeCommandExecListeners , listener );
				}
			}
			if ( listener.isInvokeAfterCommandExecution() ) 
			{
				afterCommandExecListeners = append( afterCommandExecListeners , listener );
				if ( listener.isInvokeAfterAndBeforeCommandExecutionInContinuousMode() ) {
					continuousModeAfterCommandExecListeners = append( continuousModeAfterCommandExecListeners , listener );
				}
			}
		}
	}        

	public void removeAllEmulationListeners() 
	{
		synchronized (LOCK) 
		{
			allListeners = removeAllNonHardwareListeners( allListeners );
			beforeCommandExecListeners = removeAllNonHardwareListeners( beforeCommandExecListeners );
			continuousModeBeforeCommandExecListeners = removeAllNonHardwareListeners( continuousModeBeforeCommandExecListeners );
			continuousModeAfterCommandExecListeners = removeAllNonHardwareListeners( continuousModeAfterCommandExecListeners );
			afterCommandExecListeners = removeAllNonHardwareListeners( afterCommandExecListeners );
		} 		    
	}

	private IEmulationListener[] removeAllNonHardwareListeners(final IEmulationListener[] list) 
	{
		final ArrayList<IEmulationListener> toKeep = new ArrayList<>();
		for ( int i = 0 ; i < list.length ; i++ ) 
		{
			IEmulationListener l = list[i];
			if ( l.belongsToHardwareDevice() ) {
				toKeep.add( l );
			}
		}
		return toKeep.toArray( new IEmulationListener[ toKeep.size() ] );
	}

	public void removeEmulationListener(IEmulationListener listener)
	{
		if (listener == null) {
			throw new IllegalArgumentException("listener must not be NULL.");
		}
		synchronized (LOCK) 
		{
			allListeners = remove( allListeners , listener );
			beforeCommandExecListeners = remove( beforeCommandExecListeners , listener );
			continuousModeBeforeCommandExecListeners = remove( continuousModeBeforeCommandExecListeners , listener );
			continuousModeAfterCommandExecListeners = remove( continuousModeAfterCommandExecListeners , listener );
			afterCommandExecListeners = remove( afterCommandExecListeners , listener );
		}        
	}
	
	private static IEmulationListener[] remove(final IEmulationListener[] data,IEmulationListener l) 
	{
		for ( int i = 0 ; i < data.length ; i++ ) {
			if ( data[i].equals( l ) ) 
			{
				if ( data.length == 1 ) {
					return new IEmulationListener[0];
				}
				final IEmulationListener[] result = new IEmulationListener[ data.length-1 ];				
				if ( i == 0 ) {
					System.arraycopy( data , 1 , result , 0 , data.length-1 );
				} else if ( i == (data.length-1 ) ) {
					System.arraycopy( data , 0 , result , 0 , data.length-1 );
				} else {
					System.arraycopy( data , 0 , result , 0 , i );
					System.arraycopy( data , i , result , 0 , data.length-i );
				}
			}
		}
		return data;
	}

	public void notifyListeners(IEmulationListenerInvoker invoker) 
	{
		final IEmulationListener[] copy;
		synchronized(LOCK) 
		{
			copy = allListeners;
		}
		notifyListeners( invoker , copy );
	}

	public void invokeAfterCommandExecutionListeners(boolean continousMode) 
	{
		final IEmulationListener[] toCall;
		synchronized(LOCK) 
		{
			if ( continousMode ) 
			{
				toCall = continuousModeAfterCommandExecListeners;
			} 
			else 
			{
				toCall = afterCommandExecListeners;
			}
		}
		notifyListeners( AFTER_COMMAND_INVOKER , toCall );
	}

	public void invokeBeforeCommandExecutionListeners(boolean continousMode) 
	{
		final IEmulationListener[] toCall;
		synchronized(LOCK) 
		{
			if ( continousMode ) {
				toCall = continuousModeBeforeCommandExecListeners;
			} else {        	
				toCall = beforeCommandExecListeners;
			}
		}
		notifyListeners( BEFORE_COMMAND_INVOKER , toCall );
	}   
	
	private void notifyListeners(IEmulationListenerInvoker invoker,IEmulationListener[] listeners) 
	{
		final int len = listeners.length;
		for ( int i = 0 ; i < len ; i++) 
		{
			final IEmulationListener l = listeners[i];
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

		final IEmulationListener[] copy;
		synchronized( LOCK ) 
		{
			copy = allListeners;
		}              
		for ( IEmulationListener l : copy ) {
			removeEmulationListener( l );
		}
	}
}