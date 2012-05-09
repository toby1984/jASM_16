package de.codesourcery.jasm16.ide.ui.viewcontainers;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * Helper class that contains various utility methods for
 * implementing view containers.
 * 
 * @author tobias.gierke@voipfuture.com
 */
public class ViewContainerHelper 
{
    private static final Logger LOG = Logger.getLogger(ViewContainerHelper.class);
    
    // @GuardedBy( listeners )
    private final List<IViewContainerListener> listeners = new ArrayList<IViewContainerListener>();
    
    public void addViewContainerListener(IViewContainerListener listener)
    {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be NULL.");
        }
        synchronized (listeners) 
        {
            listeners.add( listener );
        }
    }

    public void removeViewContainerListener(IViewContainerListener listener)
    {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be NULL.");
        }
        synchronized (listeners) {
            listeners.remove( listener );
        }        
    }

    public void fireViewContainerClosed(IViewContainer container) {
        
        final List<IViewContainerListener> copy;
        synchronized (listeners) {
            copy = new ArrayList<IViewContainerListener>( this.listeners );
        }  
        
        for ( IViewContainerListener l : copy ) {
            try {
                l.viewContainerClosed( container );
            } 
            catch(Exception e) {
                LOG.error("fireViewContainerClosed(): Listener "+l+" failed",e);
            }
        }
    }
    
}
