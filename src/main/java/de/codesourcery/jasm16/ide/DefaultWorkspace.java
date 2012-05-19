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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.exceptions.ProjectAlreadyExistsException;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Default workspace implementation.
 * 
 * <p>This implementation uses a meta-data file {@link #WORKSPACE_METADATA_FILE}
 * in the root folder of the workspace to keep track of all 
 * projects that are to be managed by this workspace instance.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class DefaultWorkspace implements IWorkspace
{
	private static final Logger LOG = Logger.getLogger(DefaultWorkspace.class);

	private final List<IAssemblyProject> projects = new ArrayList<IAssemblyProject>();
	private final List<IResourceListener> listeners = new ArrayList<IResourceListener>();

	private final AtomicBoolean opened = new AtomicBoolean(false);
	private final IApplicationConfig appConfig;
	private WorkspaceConfig workspaceConfig;

	public DefaultWorkspace(IApplicationConfig appConfig) throws IOException 
	{
		if (appConfig == null) {
			throw new IllegalArgumentException("appConfig must not be NULL");
		}
		this.appConfig = appConfig;
	}
	
	private synchronized WorkspaceConfig getWorkspaceConfig() throws IOException 
	{
		if ( workspaceConfig == null ) {
			workspaceConfig = new WorkspaceConfig( new File( getBaseDirectory() ,
					WorkspaceConfig.FILE_NAME ) );
		}
		return workspaceConfig;
	}

	
	public File getBaseDirectory() {
		return appConfig.getWorkspaceDirectory();
	}

	
	public List<IAssemblyProject> getAllProjects()
	{
		assertWorkspaceOpen();
		return new ArrayList<IAssemblyProject>( projects );
	}

	protected void assertWorkspaceOpen() {
		if ( opened.get() == false ) {
			throw new IllegalStateException("Workspace "+getBaseDirectory().getAbsolutePath()+" is not open");
		}
	}

	protected void loadProjects() throws IOException 
	{
		opened.set( false );

		final List<IAssemblyProject> tmp = new ArrayList<IAssemblyProject>();
		for ( File dir : getWorkspaceConfig().getProjectsBaseDirectories() ) 
		{
			if ( ! dir.isDirectory() ) {
				LOG.error("loadProjects(): Project directory "+dir.getName()+" no longer exists");
				continue;
			}
			try {
				tmp.add( loadProject( dir ) );
			} catch (IOException e) {
				LOG.error("loadProjects(): Failed to load project from "+dir.getAbsolutePath());
			}
		}

		this.projects.clear();
		this.projects.addAll( tmp );
		for ( IAssemblyProject project : tmp ) {
			addResourceListener( project );
		}

		opened.set( true );
	}

	private IAssemblyProject loadProject(File baseDir) throws IOException 
	{
		final ProjectConfiguration config = new ProjectConfiguration( baseDir );
		config.load();
		
		final boolean isProjectOpen = getWorkspaceConfig().isProjectOpen( config.getProjectName() );
		final IAssemblyProject result = new AssemblyProject( this , config , isProjectOpen );
		return result;
	}

	
	public boolean doesProjectExist(String name)
	{
		assertWorkspaceOpen();

		for ( IAssemblyProject existing : projects ) {
			if ( existing.getName().equals( name ) ) {
				return true;
			}
		}        
		return false;
	}

	
	public IAssemblyProject createNewProject(String name) throws IOException, ProjectAlreadyExistsException
	{
		if (StringUtils.isBlank(name)) {
			throw new IllegalArgumentException("name must not be NULL/blank.");
		}

		assertWorkspaceOpen();

		if ( doesProjectExist( name ) ) {
			throw new ProjectAlreadyExistsException( name );
		}

		final File baseDir = new File( getBaseDirectory() , name );
		if ( baseDir.exists() ) {
			throw new ProjectAlreadyExistsException(name,"Cannot create project '"+name+"' , project directory "+
					baseDir.getAbsolutePath()+" already exists ?");
		}
		
		if ( ! baseDir.mkdirs() ) {
			throw new IOException("Failed to create project base directory "+baseDir.getAbsolutePath());
		}

		LOG.info("createNewProject(): Creating project '"+name+"'");
		
		final ProjectConfiguration config = new ProjectConfiguration(baseDir);
		config.setProjectName( name );
		try {
			config.create();
		} 
		catch (IOException e) 
		{
			baseDir.delete();
			throw e;
		}

		 return internalAddProject( config , true );
	}
	
	private IAssemblyProject internalAddProject(ProjectConfiguration config,boolean deleteProjectFilesOnError) throws IOException 
	{
		final IAssemblyProject result = new AssemblyProject( this , config , true );
		projects.add( result );
		
		try {
			getWorkspaceConfig().projectAdded( result );
			getWorkspaceConfig().saveConfiguration();
		} 
		catch(IOException e) 
		{
			LOG.error("internalAddProject(): Failed to save metadata",e);
			if ( deleteProjectFilesOnError ) {
				try {
					internalDeleteFile( null , config.getBaseDirectory() , false );
				} catch(Exception e2) {
					LOG.error("internalAddProject(): Caught during rollback ",e2);
					// ok, can't help it
				}
				projects.remove( result );
			}
			throw e;
		}
		
		// register project as resource listener
		addResourceListener( result );
		
		notifyListeners( new IInvoker() {
			
			public void invoke(IResourceListener listener) 
			{
			    if ( listener instanceof IWorkspaceListener) {
			        ((IWorkspaceListener) listener).projectCreated(result);
			    }
			}
			
			public String toString() {
				return "PROJECT-CREATED: "+result;
			}			
		});			
		return result;		
	}

	
	public void deleteProject(final IAssemblyProject project,boolean deletePhyiscally) throws IOException
	{
		if (project == null) {
			throw new IllegalArgumentException("project must not be NULL.");
		}

		assertWorkspaceOpen();

		for (Iterator<IAssemblyProject> it = projects.iterator(); it.hasNext();) 
		{
			final IAssemblyProject existing = it.next();
			if ( existing.getName().equals( project.getName() ) ) 
			{
				it.remove();
				
				if ( deletePhyiscally ) {
					internalDeleteFile( existing , existing.getConfiguration().getBaseDirectory() , true );					
				}
				
				removeResourceListener( existing );
				
				try {
					getWorkspaceConfig().projectDeleted( project );
					getWorkspaceConfig().saveConfiguration();
				} 
				catch (IOException e) {
					LOG.error("createNewProject(): Failed to save metadata",e);		
					if ( ! deletePhyiscally ) { // no use re-adding the file
						projects.add( project );
					}
					throw e;
				}
				
				notifyListeners( new IInvoker() {
					
					public void invoke(IResourceListener listener) {
		                if ( listener instanceof IWorkspaceListener) {
		                    ((IWorkspaceListener) listener).projectDeleted(existing);
		                }					    
					}
					
					public String toString() {
						return "PROJECT-DELETED: "+project;
					}					
				});				
				return;
			}
		}
	}
	
	
	public void deleteFile(final IAssemblyProject project, final File file) throws IOException 
	{
		if ( project == null ) {
			throw new IllegalArgumentException("project must not be null");
		}
		if ( file == null ) {
			throw new IllegalArgumentException("file must not be null");
		}
		internalDeleteFile(project,file,false);
	}
	
	/**
	 * 
	 * @param project project or <code>null</code> if no resource listeners should be notified
	 * @param file
	 * @throws IOException
	 */
	protected void internalDeleteFile(final IAssemblyProject project, final File file,boolean calledByDeleteProject) throws IOException 
	{
		if ( project != null &&
			 ! calledByDeleteProject && 
			 project.getConfiguration().getBaseDirectory().equals( file ) ) 
		{
			deleteProject(project,true);
			return;
		}
		
		if ( file.isDirectory() ) {
			for ( File child : file.listFiles() ) {
				internalDeleteFile( project , child , calledByDeleteProject );
			}
		} 
		
		file.delete();
		
		if ( project != null  ) 
		{
			notifyListeners( new IInvoker() {
				
				public void invoke(IResourceListener listener) 
				{
					IResource resource = project.getResourceForFile( file );
					if ( resource == null ) {
						resource = new FileResource( file , ResourceType.UNKNOWN );
					}
					listener.resourceDeleted( project , resource );
				}
				
				public String toString() {
					return "RESOURCE-DELETED: "+file.getAbsolutePath();
				}					
			});		
		}
	}

	
	public void saveProjectConfiguration(IAssemblyProject project) throws IOException
	{
		if (project == null) {
			throw new IllegalArgumentException("project must not be NULL.");
		}

		assertWorkspaceOpen();

		if ( ! doesProjectExist( project.getName() ) ) {
			throw new IllegalArgumentException("Project '"+project.getName()+"' not registered?");
		}
		project.getConfiguration().save();
	}

	
	public void resourceChanged(final IAssemblyProject project , final IResource resource)
	{
		notifyListeners( new IInvoker() {
			
			public void invoke(IResourceListener listener) {
				listener.resourceChanged( project , resource );
			}
			
			
			public String toString() {
				return "RESOURCE-CHANGED: "+resource;
			}			
		});
	}
	
	protected interface IInvoker {
		public void invoke(IResourceListener listener);
	}
	
	private void notifyListeners(IInvoker invoker) 
	{
		System.out.println( invoker.toString() );
		
		final List<IResourceListener> copy;
		synchronized (listeners) {
			copy = new ArrayList<IResourceListener>( this.listeners );
		}

		for ( IResourceListener l : copy ) {
			try {
				invoker.invoke( l );
			} 
			catch(Exception e) {
				e.printStackTrace();
			}
		}		
	}
	
	
	public void addWorkspaceListener(IWorkspaceListener listener)
	{
	    addResourceListener( listener );
	}

	
	public void removeWorkspaceListener(IWorkspaceListener listener)
	{
	    removeResourceListener( listener );
	}

	
	public void reloadWorkspace() throws IOException 
	{
		loadProjects();
	}

	
	public IAssemblyProject getProjectByName(String name) 
	{
		assertWorkspaceOpen();

		for ( IAssemblyProject p : projects ) 
		{
			if ( p.getName().equals( name ) ) {
				return p;
			}
		}
		throw new NoSuchElementException("Found no project named '"+name+"'");
	}

	
	public void close() throws IOException {
		this.opened.set( false );
		getWorkspaceConfig().saveConfiguration();		
	}

	
	public void open() throws IOException 
	{
		reloadWorkspace();
	}

	
	public void resourceCreated(final IAssemblyProject project, final IResource resource) {

		notifyListeners( new IInvoker() {

			
			public void invoke(IResourceListener listener) {
				listener.resourceCreated( project , resource );
			}
			
			
			public String toString() {
				return "RESOURCE-CREATED: "+resource;
			}
		});
	}

	
	public void resourceDeleted(final IAssemblyProject project, final IResource resource) 
	{
		notifyListeners( new IInvoker() {

			
			public void invoke(IResourceListener listener) 
			{
				listener.resourceDeleted( project , resource );
			}
			
			
			public String toString() {
				return "RESOURCE-DELETED: "+resource;
			}			
		});		
	}

    
    public void addResourceListener(IResourceListener listener)
    {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be NULL.");
        }

        synchronized (listeners) 
        {
        	if ( listener instanceof IAssemblyProject) 
        	{
        		// add projects BEFORE any other listeners because
        		// IAssemblyProject#projectOpened() and
        		// IAssemblyProject#projectClosed() update internal state
        		// that may be checked by other IWorkspaceListener implementations
        		// when their projectOpened() / projectClosed() methods are invoked 
        		listeners.add( 0 , listener );
        	} else {
        		listeners.add( listener );
        	}
        }        
    }

    
    public void removeResourceListener(IResourceListener listener)
    {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be NULL.");
        }
        synchronized (listeners) {
            listeners.remove( listener );
        }         
    }

	
	public void buildStarted(final AssemblyProject assemblyProject) 
	{
		notifyListeners( new IInvoker() {
			
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener ) {
					((IWorkspaceListener) listener).buildStarted( assemblyProject );
				}
			}
			
			
			public String toString() {
				return "BUILD-STARTED: "+assemblyProject;
			}			
		});		
	}

	
	public void buildFinished(final AssemblyProject assemblyProject, final boolean buildSuccessful) 
	{
		notifyListeners( new IInvoker() {
			
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener ) {
					((IWorkspaceListener) listener).buildFinished( assemblyProject , buildSuccessful );
				}
			}
			
			
			public String toString() {
				return "BUILD-FINISHED: "+assemblyProject+" ( success = "+buildSuccessful+" )";
			}			
		});			
	}

    
    public void refreshProjects(Collection<IAssemblyProject> projects) throws IOException
    {
        if (projects == null) {
            throw new IllegalArgumentException("project must not be NULL.");
        }
        for ( IAssemblyProject p : projects ) 
        {
        	if ( p.isOpen() ) {
        		LOG.info("refreshProjects(): Refreshing "+p);
        		p.rescanResources();
        	}
        }
    }

	
	public void openProject(final IAssemblyProject project) {
		
		try {
			getWorkspaceConfig().projectOpened( project );
			getWorkspaceConfig().saveConfiguration();
		} catch (IOException e) {
			LOG.error("closeProject(): Failed to update workspace configuration");
		}
		
		// nothing to do here since IAssemblyProject
		// implements IWorkspaceListener#projectOpened() and
		// will update it's internal state when it receives the message
		notifyListeners( new IInvoker() {
			
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener ) {
					((IWorkspaceListener) listener).projectOpened( project );
				}
			}
			
			
			public String toString() {
				return "PROJECT-OPENED: "+project;
			}			
		});			
	}

	
	public void closeProject(final IAssemblyProject project) 
	{
		try {
			getWorkspaceConfig().projectClosed( project );
			getWorkspaceConfig().saveConfiguration();
		} catch (IOException e) {
			LOG.error("closeProject(): Failed to update workspace configuration");
		}
		
		// nothing to do here since IAssemblyProject
		// implements IWorkspaceListener#projectClosed() and
		// will update it's internal state when it receives the message
		notifyListeners( new IInvoker() {
			
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener ) {
					((IWorkspaceListener) listener).projectClosed( project );
				}
			}
			
			
			public String toString() {
				return "PROJECT-CLOSED: "+project;
			}			
		});			
	}

	
	public IAssemblyProject importProject(File baseDirectory) throws IOException, ProjectAlreadyExistsException 
	{
		if (baseDirectory == null) {
			throw new IllegalArgumentException("baseDirectory must not be null");
		}
		
		Misc.checkFileExistsAndIsDirectory( baseDirectory , false );
		
		if ( ! baseDirectory.getAbsolutePath().startsWith( getBaseDirectory().getAbsolutePath() ) ) 
		{
			throw new IllegalArgumentException("Folder "+baseDirectory.getAbsolutePath()+
					" is not within the workspace folder "+getBaseDirectory().getAbsolutePath());
		}

		final ProjectConfiguration config = new ProjectConfiguration(baseDirectory);
		config.load();
		
		return internalAddProject( config , false );
	}

}
