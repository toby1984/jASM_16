package de.codesourcery.jasm16.emulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.Emulator.CPU;
import de.codesourcery.jasm16.emulator.Emulator.IEmulationListenerInvoker;

final class BreakpointHelper {

	private static final Logger LOG = Logger.getLogger( BreakpointHelper.class );
	
	// @GuardedBy( breakpoints )
	protected final Map<Address,List<Breakpoint>> breakpoints = new HashMap<Address,List<Breakpoint>>(); 
	
	// @GuardedBy( breakpoints )	
	private int enabledBreakpointsCount = 0;
	
	private final IEmulator emulator;
	private final ListenerHelper listenerHelper;
	
	public BreakpointHelper(IEmulator emulator,ListenerHelper helper) {
		this.emulator = emulator;
		this.listenerHelper = helper;
	}
	
	public void removeAllInternalBreakpoints() 
	{
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
			deleteBreakpoint( bp , true );
		}		
	}
	
	public void maybeHandleBreakpoint(CPU hiddenCPU) 
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
			if ( enabledBreakpointsCount == 0 ) {
				return;
			}
			
			final List<Breakpoint> candidates = breakpoints.get( Address.wordAddress( hiddenCPU.pc ) ); 

			if ( candidates == null || candidates.isEmpty() ) 
			{
				return;
			}		

			for ( Breakpoint bp : candidates ) 
			{
				if ( bp.matches( emulator ) ) 
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
			emulator.stop();

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
	
	public void addBreakpoint(final Breakpoint newBP)
	{
		if (newBP == null) {
			throw new IllegalArgumentException("breakpoint must not be NULL.");
		}

		Breakpoint replacedBreakpoint;
		synchronized( breakpoints ) 
		{
			List<Breakpoint> list = breakpoints.get( newBP.getAddress() );
			if ( list == null ) {
				list = new ArrayList<Breakpoint>();
				breakpoints.put( newBP.getAddress() , list );
			} 
			if ( newBP.isEnabled() ) {
				enabledBreakpointsCount++;
			}
			replacedBreakpoint = extractRegularBreakpoint( list );
			if ( replacedBreakpoint != null ) {
				list.remove( replacedBreakpoint );
				if ( replacedBreakpoint.isEnabled() ) {
					enabledBreakpointsCount--;
				}
			}
			list.add( newBP );
		}  

		// notify listeners
		if ( replacedBreakpoint != null && ! replacedBreakpoint.isOneShotBreakpoint() ) {
			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.breakpointDeleted( emulator , newBP );
				}
			});         	
		}

		if ( ! newBP.isOneShotBreakpoint() ) 
		{
			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.breakpointAdded( emulator , newBP );
				}
			});        
		}
	}
	
	public void breakpointChanged(final Breakpoint changedBP) {

		Breakpoint existingBP;
		synchronized( breakpoints ) {
			final List<Breakpoint> list = breakpoints.get( changedBP.getAddress() );
			if ( list != null ) {
				existingBP = extractRegularBreakpoint( list );
				
				if ( existingBP.isEnabled() != changedBP.isEnabled() ) 
				{
					if ( existingBP.isEnabled() ) { // enabled -> disabled
						enabledBreakpointsCount--;
					} else { // disabled -> enabled
						enabledBreakpointsCount++;
					}
				}				
			} else {
				existingBP = null;
			}
		}     	

		if ( existingBP == null ) {
			LOG.warn("breakpointChanged(): Unknown breakpoint "+changedBP);
			return;
		}
		
		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.breakpointChanged( emulator , changedBP );
			}
		});         
	}	
	
	public void deleteAllBreakpoints() 
	{
		final List<Breakpoint> copy=new ArrayList<>();
		synchronized( breakpoints ) 
		{
			for ( List<Breakpoint> bp : breakpoints.values() ) {
				copy.addAll( bp );
			}
		}
		for ( Breakpoint bp : copy ) 
		{
			deleteBreakpoint( bp , false );
		}
		
		listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

			@Override
			public void invoke(IEmulator emulator, IEmulationListener listener)
			{
				listener.allBreakpointsDeleted( emulator );
			}
		});  		
	}	
	
	public void deleteBreakpoint(final Breakpoint bp,boolean notifyListeners)
	{
		if (bp == null) {
			throw new IllegalArgumentException("breakpoint must not be NULL.");
		}
		
		final Breakpoint removedBP;
		synchronized( breakpoints ) 
		{
			final List<Breakpoint> list = breakpoints.get( bp.getAddress() );
			if ( list != null ) 
			{
				final int idx = list.indexOf( bp );
				if ( idx != -1 ) {
					removedBP = list.remove( idx );
					if ( removedBP.isEnabled() ) 
					{
						enabledBreakpointsCount--;
					}
				} else {
					removedBP = null;
				}
				if ( list.isEmpty() ) 
				{
					breakpoints.remove( bp.getAddress() );
				} 
			} else {
				removedBP = null;
			}
		}      
		
		// notify listeners
		if ( notifyListeners && removedBP != null && ! removedBP.isOneShotBreakpoint() ) 
		{
			listenerHelper.notifyListeners( new IEmulationListenerInvoker() {

				@Override
				public void invoke(IEmulator emulator, IEmulationListener listener)
				{
					listener.breakpointDeleted( emulator , bp );
				}
			});         
		}
	}	
	
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
	
	private Breakpoint extractRegularBreakpoint(List<Breakpoint> bps) 
	{
		Breakpoint result = null;
		for ( Breakpoint bp : bps ) 
		{
			if ( ! bp.isOneShotBreakpoint() ) 
			{
				if ( result != null ) {
					throw new IllegalStateException("More than one regular breakpoint at address "+bp.getAddress());
				}
				result = bp;
			}
		}
		return result;
	}		
}
