package de.codesourcery.jasm16.ide.ui.viewcontainers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 * 
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ViewContainerManager implements IViewContainerListener
{
    // @GuardedBy( containers )
    private final List<IViewContainer> containers = new ArrayList<IViewContainer>();
    
    public void addViewContainer(Perspective p) 
    {
        if (p == null) {
            throw new IllegalArgumentException("p must not be NULL.");
        }
        
        synchronized(containers) 
        {
            p.addViewContainerListener( this );
            containers.add( p );
        }
    }
    
    public void removeViewContainer(IViewContainer p) 
    {
        if (p == null) {
            throw new IllegalArgumentException("p must not be NULL.");
        }
        
        synchronized(containers) {
            p.removeViewContainerListener( this );
            containers.remove( p );
        }
    }    
    
    public List<IViewContainer> getPerspectives(String id) {
        
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("id must not be NULL/blank.");
        }
        
        final List<IViewContainer> result = new ArrayList<>();
        
        synchronized(containers) {
            for ( IViewContainer p : containers ) {
                if ( p.getID().equals( id ) ) {
                    result.add( p );
                }
            }
        }
        return result;
    }

    @Override
    public void viewContainerClosed(IViewContainer container)
    {
        removeViewContainer( container );
    }
}
