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

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.exceptions.ProjectAlreadyExistsException;
import de.codesourcery.jasm16.ide.exceptions.ProjectNotFoundException;
import de.codesourcery.jasm16.utils.IOrdered;
import de.codesourcery.jasm16.utils.IOrdered.Priority;
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
	private static final boolean DEBUG_EVENTS = false;

	private static final Logger LOG = Logger.getLogger(DefaultWorkspace.class);

	private final List<IAssemblyProject> projects = new ArrayList<IAssemblyProject>();
	private final List<IResourceListener> listeners = new ArrayList<IResourceListener>();

	private final AtomicBoolean opened = new AtomicBoolean(false);
	private final IApplicationConfig appConfig;
	private WorkspaceConfig workspaceConfig;

	private final IBuildManager buildManager;

	public DefaultWorkspace(IApplicationConfig appConfig) throws IOException 
	{
		if (appConfig == null) {
			throw new IllegalArgumentException("appConfig must not be NULL");
		}
		this.appConfig = appConfig;
		final BuildManager tmp = new BuildManager(this);
		this.buildManager = tmp;
		addResourceListener( tmp );
	}

	@Override
	public IBuildManager getBuildManager() {
		return buildManager;
	}

	private synchronized WorkspaceConfig getWorkspaceConfig() throws IOException 
	{
		if ( workspaceConfig == null ) {
			workspaceConfig = new WorkspaceConfig( new File( getBaseDirectory() ,
					WorkspaceConfig.FILE_NAME ) );
		}
		return workspaceConfig;
	}

	@Override
	public File getBaseDirectory() {
		return appConfig.getWorkspaceDirectory();
	}

	@Override
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
				LOG.error("loadProjects(): Failed to load project from "+dir.getAbsolutePath(),e);
			}
		}

		// dispose any projects that are already loaded
		for ( Iterator<IAssemblyProject> it = projects.iterator() ; it.hasNext() ; ) 
		{
			final IAssemblyProject project = it.next();
			it.remove();

			project.removedFromWorkspace( this );

			notifyListeners( new IInvoker() {
				@Override
				public void invoke(IResourceListener listener) 
				{
					if ( listener instanceof IWorkspaceListener) {
						((IWorkspaceListener) listener).projectDisposed( project );
					}
				}
				@Override
				public String toString() {
					return "PROJECT-DISPOSED: "+project;
				}			
			});				
		}

		// add new projects
		for ( final IAssemblyProject p : tmp ) 
		{
			this.projects.add( p );
			p.addedToWorkspace( this );

			notifyListeners( new IInvoker() {
				@Override
				public void invoke(IResourceListener listener) 
				{
					if ( listener instanceof IWorkspaceListener) {
						((IWorkspaceListener) listener).projectLoaded( p );
					}
				}
				@Override
				public String toString() {
					return "PROJECT-LOADED: "+p;
				}			
			});				
		}

		opened.set( true );
	}

	private IAssemblyProject loadProject(File baseDir) throws IOException 
	{
		final ProjectConfiguration config = new ProjectConfiguration( baseDir );
		config.load();

		final boolean isProjectOpen = getWorkspaceConfig().isProjectOpen( config.getProjectName() );

		final AssemblyProject tmp = new AssemblyProject( this , config , isProjectOpen );
		final IProjectBuilder builder = buildManager.getProjectBuilder( tmp );			
		tmp.setProjectBuilder( builder );
		return tmp;
	}

	@Override
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

	@Override
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

	private AssemblyProject internalAddProject(ProjectConfiguration config,boolean deleteProjectFilesOnError) throws IOException 
	{
		final AssemblyProject result = new AssemblyProject( this , config , true );
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
		result.addedToWorkspace( this );

		final IProjectBuilder builder = buildManager.getProjectBuilder( result );			
		result.setProjectBuilder( builder );		

		notifyListeners( new IInvoker() {
			@Override
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener) {
					((IWorkspaceListener) listener).projectCreated(result);
				}
			}
			@Override
			public String toString() {
				return "PROJECT-CREATED: "+result;
			}			
		});			
		return result;		
	}

	@Override
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

				existing.removedFromWorkspace( this );

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
					@Override
					public void invoke(IResourceListener listener) {
						if ( listener instanceof IWorkspaceListener) {
							((IWorkspaceListener) listener).projectDeleted(existing);
						}					    
					}
					@Override
					public String toString() {
						return "PROJECT-DELETED: "+project;
					}					
				});				
				return;
			}
		}
	}

	@Override
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
				@Override
				public void invoke(IResourceListener listener) 
				{
					IResource resource = project.getResourceForFile( file );
					if ( resource == null ) {
						resource = new FileResource( file , ResourceType.UNKNOWN );
					}
					listener.resourceDeleted( project , resource );
				}
				@Override
				public String toString() {
					return "RESOURCE-DELETED: "+file.getAbsolutePath();
				}					
			});		
		}
	}

	@Override
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

	@Override
	public void resourceChanged(final IAssemblyProject project , final IResource resource)
	{
		notifyListeners( new IInvoker() {
			@Override
			public void invoke(IResourceListener listener) {
				listener.resourceChanged( project , resource );
			}

			@Override
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
		if ( DEBUG_EVENTS ) {
			System.out.println( invoker.toString() );
		}

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

	@Override
	public void addWorkspaceListener(IWorkspaceListener listener)
	{
		addResourceListener( listener );
	}

	@Override
	public void removeWorkspaceListener(IWorkspaceListener listener)
	{
		removeResourceListener( listener );
	}

	@Override
	public void reloadWorkspace() throws IOException 
	{
		loadProjects();
	}

	@Override
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

	@Override
	public void close() throws IOException 
	{
		System.out.println("Closing workspace...");
		try 
		{		
			if ( this.opened.compareAndSet( true , false ) ) 
			{
				for ( IAssemblyProject p : projects ) 
				{
					if ( p.isOpen() ) 
					{
						p.getConfiguration().save();
					}
				}
			}
		} finally {
			getWorkspaceConfig().saveConfiguration();
		}
	}

	@Override
	public void open() throws IOException 
	{
		reloadWorkspace();
	}

	@Override
	public void compilationFinished(final IAssemblyProject project, final ICompilationUnit unit) {
		notifyListeners( new IInvoker() {

			@Override
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener) {
					((IWorkspaceListener) listener).compilationFinished( project , unit );
				}
			}

			@Override
			public String toString() {
				return "COMPILATION-FINISHED: "+project+" - "+unit;
			}
		});		
	}

	@Override
	public void resourceCreated(final IAssemblyProject project, final IResource resource) {

		notifyListeners( new IInvoker() {

			@Override
			public void invoke(IResourceListener listener) {
				listener.resourceCreated( project , resource );
			}

			@Override
			public String toString() {
				return "RESOURCE-CREATED: "+resource;
			}
		});
	}

	@Override
	public void resourceDeleted(final IAssemblyProject project, final IResource resource) 
	{
		notifyListeners( new IInvoker() {

			@Override
			public void invoke(IResourceListener listener) 
			{
				listener.resourceDeleted( project , resource );
			}

			@Override
			public String toString() {
				return "RESOURCE-DELETED: "+resource;
			}			
		});		
	}

	@Override
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
				return;
			} 

			final IOrdered.Priority prio = getPriority(listener);
			if ( prio != Priority.DONT_CARE ) 
			{
				for (int i = 0; i < listeners.size(); i++) 
				{
					final IResourceListener existing = listeners.get(i);
					if ( ! getPriority( existing ).isHigherThan( prio ) ) {
						listeners.add( i , listener );
						return;
					}
				}
			}
			listeners.add( listener );
		}        
	}

	private static IOrdered.Priority getPriority(IResourceListener listener) {
		return (listener instanceof IOrdered) ? ((IOrdered) listener).getPriority() : Priority.DONT_CARE;
	}

	@Override
	public void removeResourceListener(IResourceListener listener)
	{
		if (listener == null) {
			throw new IllegalArgumentException("listener must not be NULL.");
		}
		synchronized (listeners) {
			listeners.remove( listener );
		}         
	}

	@Override
	public void buildStarted(final IAssemblyProject assemblyProject) 
	{
		notifyListeners( new IInvoker() {
			@Override
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener ) {
					((IWorkspaceListener) listener).buildStarted( assemblyProject );
				}
			}

			@Override
			public String toString() {
				return "BUILD-STARTED: "+assemblyProject;
			}			
		});		
	}

	@Override
	public void buildFinished(final IAssemblyProject assemblyProject, final boolean buildSuccessful) 
	{
		notifyListeners( new IInvoker() {
			@Override
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener ) {
					((IWorkspaceListener) listener).buildFinished( assemblyProject , buildSuccessful );
				}
			}

			@Override
			public String toString() {
				return "BUILD-FINISHED: "+assemblyProject+" ( success = "+buildSuccessful+" )";
			}			
		});			
	}

	@Override
	public void refreshProjects(Collection<IAssemblyProject> projects) throws IOException
	{
		if (projects == null) {
			throw new IllegalArgumentException("project must not be NULL.");
		}
		for ( final IAssemblyProject p : projects ) 
		{
			if ( p.isOpen() ) 
			{
				LOG.info("refreshProjects(): Refreshing "+p);

				ProjectConfiguration reloaded = new ProjectConfiguration( p.getConfiguration().getBaseDirectory() );
				reloaded.load();
				p.getConfiguration().populateFrom( reloaded );
				p.reload();

				notifyListeners( new IInvoker() {
					@Override
					public void invoke(IResourceListener listener) 
					{
						if ( listener instanceof IWorkspaceListener ) {
							((IWorkspaceListener) listener).projectConfigurationChanged( p );
						}
					}

					@Override
					public String toString() {
						return "PROJECT-CONFIGURATION-CHANGED: "+p;
					}			
				});	        		
			}
		}
	}

	@Override
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
			@Override
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener ) {
					((IWorkspaceListener) listener).projectOpened( project );
				}
			}

			@Override
			public String toString() {
				return "PROJECT-OPENED: "+project;
			}			
		});			
	}

	@Override
	public void closeProject(final IAssemblyProject project) 
	{
		try {
			getWorkspaceConfig().projectClosed( project );
			getWorkspaceConfig().saveConfiguration();
		} 
		catch (IOException e) {
			LOG.error("closeProject(): Failed to update workspace configuration",e);
		}

		// nothing to do here since IAssemblyProject
		// implements IWorkspaceListener#projectClosed() and
		// will update it's internal state when it receives the message
		notifyListeners( new IInvoker() {
			@Override
			public void invoke(IResourceListener listener) 
			{
				if ( listener instanceof IWorkspaceListener ) {
					((IWorkspaceListener) listener).projectClosed( project );
				}
			}

			@Override
			public String toString() {
				return "PROJECT-CLOSED: "+project;
			}			
		});			
	}

	@Override
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

		if ( doesProjectExist( config.getProjectName() ) ) {
			throw new ProjectAlreadyExistsException(config.getProjectName() );
		}

		return internalAddProject( config , false );
	}

	@Override
	public IAssemblyProject getProjectForResource(IResource resource) throws ProjectNotFoundException
	{
		for ( IAssemblyProject project : getAllProjects() ) 
		{
			if ( project.containsResource( resource ) ) {
				return project;
			}
		}
		throw new ProjectNotFoundException("Unable to find project that owns resource '"+resource.getIdentifier()+"' ("+resource+")");
	}

}
