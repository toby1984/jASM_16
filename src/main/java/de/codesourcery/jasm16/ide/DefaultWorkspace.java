package de.codesourcery.jasm16.ide;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.compiler.io.IResource;

public class DefaultWorkspace implements IWorkspace
{
    private final List<IAssemblerProject> projects = new ArrayList<IAssemblerProject>();
    
    private final List<IWorkspaceListener> listeners = new ArrayList<IWorkspaceListener>();
    
    @Override
    public List<IAssemblerProject> getAllProjects()
    {
        return new ArrayList<IAssemblerProject>( projects );
    }
    
    @Override
    public boolean doesProjectExist(String name)
    {
        for ( IAssemblerProject existing : projects ) {
            if ( existing.getName().equals( name ) ) {
                return true;
            }
        }        
        return false;
    }

    @Override
    public IAssemblerProject createNewProject(String name)
    {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be NULL/blank.");
        }
        
        if ( doesProjectExist( name ) ) {
            throw new IllegalArgumentException("A project with this name already exists");
        }
        
        final IAssemblerProject result = new UnsavedProject(name);
        projects.add( result );
        return result;
    }

    @Override
    public void deleteProject(IAssemblerProject project)
    {
        if (project == null) {
            throw new IllegalArgumentException("project must not be NULL.");
        }
        for (Iterator<IAssemblerProject> it = projects.iterator(); it.hasNext();) {
            final IAssemblerProject existing = it.next();
            if ( existing.getName().equals( project.getName() ) ) {
                it.remove();
                return;
            }
        }
    }

    @Override
    public void saveOrUpdateProject(IAssemblerProject project)
    {
        if (project == null) {
            throw new IllegalArgumentException("project must not be NULL.");
        }
        
        for (Iterator<IAssemblerProject> it = projects.iterator(); it.hasNext();) 
        {
            final IAssemblerProject existing = it.next();
            if ( existing.getName().equals( project.getName() ) ) {
                it.remove();
            }
        }      
        projects.add( project );
    }

    @Override
    public void resourceChanged(IResource resource)
    {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be NULL.");
        }
        
        final List<IWorkspaceListener> copy;
        synchronized (listeners) {
            System.out.println("Resource '"+resource.getIdentifier()+"' has changed, notifying "+this.listeners.size()+" listeners");
            copy = new ArrayList<IWorkspaceListener>( this.listeners );
        }

        for ( IWorkspaceListener l : copy ) {
            try {
                l.resourceChanged( resource );
            } 
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void addWorkspaceListener(IWorkspaceListener listener)
    {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be NULL.");
        }
        synchronized (listeners) {
            listeners.add( listener );
        }
        
    }

    @Override
    public void removeWorkspaceListener(IWorkspaceListener listener)
    {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be NULL.");
        }
        synchronized (listeners) {
            listeners.remove( listener );
        }        
    }

}
