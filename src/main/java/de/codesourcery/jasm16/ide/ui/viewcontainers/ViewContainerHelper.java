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
package de.codesourcery.jasm16.ide.ui.viewcontainers;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * Helper class that contains various utility methods for
 * implementing view containers.
 * 
 * @author tobias.gierke@code-sourcery.de
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
