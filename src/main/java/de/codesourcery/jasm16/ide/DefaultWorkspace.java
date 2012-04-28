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
package de.codesourcery.jasm16.ide;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.io.IResource;

public class DefaultWorkspace implements IWorkspace
{
	private static final Logger LOG = Logger.getLogger(DefaultWorkspace.class);
	
    private final List<IAssemblerProject> projects = new ArrayList<IAssemblerProject>();
    private final List<IWorkspaceListener> listeners = new ArrayList<IWorkspaceListener>();

    private final AtomicBoolean projectsLoaded = new AtomicBoolean(false);
	private final IApplicationConfig appConfig;
	
    public DefaultWorkspace(IApplicationConfig appConfig) throws IOException 
    {
    	if (appConfig == null) {
			throw new IllegalArgumentException("appConfig must not be NULL");
		}
    	this.appConfig = appConfig;
    }
    
    @Override
    public File getBaseDirectory() {
    	return appConfig.getWorkspaceDirectory();
    }
    
    @Override
    public List<IAssemblerProject> getAllProjects()
    {
    	loadProjects();
        return new ArrayList<IAssemblerProject>( projects );
    }
    
    protected void loadProjects() 
    {
    	if ( projectsLoaded.compareAndSet( false , true ) ) 
    	{
	    	final File[] topLevelDirs = getBaseDirectory().listFiles( new FileFilter() {
	
				@Override
				public boolean accept(File pathname) 
				{
					return pathname.isDirectory() && pathname.canRead();
				}
	    	} );
	    	
	    	final List<IAssemblerProject> tmp = new ArrayList<IAssemblerProject>();
	    	for ( File dir : topLevelDirs ) {
				try {
					tmp.add( loadProject( dir ) );
				} catch (IOException e) {
					LOG.error("loadProjects(): Failed to load project from "+dir.getAbsolutePath());
				}
	    	}
	    	this.projects.addAll( tmp );
    	}
    }
    
    private IAssemblerProject loadProject(File baseDir) throws IOException 
    {
    	final ProjectConfiguration config = new ProjectConfiguration( baseDir );
    	config.load();
    	return new AssemblerProject( config );
    }
    
    @Override
    public boolean doesProjectExist(String name)
    {
    	loadProjects();
    	
        for ( IAssemblerProject existing : projects ) {
            if ( existing.getName().equals( name ) ) {
                return true;
            }
        }        
        return false;
    }

    @Override
    public IAssemblerProject createNewProject(String name) throws IOException
    {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be NULL/blank.");
        }
        
        if ( doesProjectExist( name ) ) {
            throw new IllegalArgumentException("A project with this name already exists");
        }
        
        final File baseDir = new File( getBaseDirectory() , name );
        if ( ! baseDir.exists() ) 
        {
        	if ( ! baseDir.mkdirs() ) {
                throw new IOException("Failed to create project base directory "+baseDir.getAbsolutePath());
        	}
        }
        
        final ProjectConfiguration config = new ProjectConfiguration(baseDir);
        config.setProjectName( name );
        config.create();
        
		final IAssemblerProject result = new AssemblerProject( config );
        projects.add( result );
        return result;
    }

    @Override
    public void deleteProject(IAssemblerProject project)
    {
        if (project == null) {
            throw new IllegalArgumentException("project must not be NULL.");
        }
    	
        loadProjects();
    	
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
        
        loadProjects();
        
        for (Iterator<IAssemblerProject> it = projects.iterator(); it.hasNext();) 
        {
            final IAssemblerProject existing = it.next();
            if ( existing.getName().equals( project.getName() ) ) 
            {
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

	@Override
	public void reloadWorkspace() {
		projectsLoaded.set( false );	
		loadProjects();
	}

	@Override
	public IAssemblerProject getProjectByName(String name) 
	{
		loadProjects();
		for ( IAssemblerProject p : projects ) 
		{
			if ( p.getName().equals( name ) ) {
				return p;
			}
		}
		throw new NoSuchElementException("Found no project named '"+name+"'");
	}

}
