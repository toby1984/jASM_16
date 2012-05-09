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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.io.IResource;

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

	public static final String WORKSPACE_METADATA_FILE=".jasm16_metadata";

	private final AtomicBoolean opened = new AtomicBoolean(false);
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
		for ( File dir : loadProjectDirectories() ) 
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

		opened.set( true );
	}

	private List<File> loadProjectDirectories() throws IOException {

		final List<File> result = new ArrayList<File>();

		final File configFile = new File( getBaseDirectory() , WORKSPACE_METADATA_FILE );
		if ( configFile.exists() ) 
		{
			final BufferedReader reader = new BufferedReader( new FileReader( configFile ) );
			try {
				String line=null;
				while( ( line = reader.readLine() ) != null ) {
					result.add( new File( getBaseDirectory() , line ) );
				}
			} finally {
				IOUtils.closeQuietly( reader );
			}
		} 

		return result;
	}

	private void rememberProjectDirectories() throws IOException 
	{
		final File configFile = new File( getBaseDirectory() , WORKSPACE_METADATA_FILE );
		final BufferedWriter writer = new BufferedWriter( new FileWriter( configFile ) );
		try {
			for ( IAssemblyProject project : projects ) 
			{
				writer.write( project.getConfiguration().getBaseDirectory().getName()+"\n");
			}
		} finally {
			IOUtils.closeQuietly( writer);
		}
	}

	private IAssemblyProject loadProject(File baseDir) throws IOException 
	{
		final ProjectConfiguration config = new ProjectConfiguration( baseDir );
		config.load();
		return new AssemblyProject( this , config );
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
	public IAssemblyProject createNewProject(String name) throws IOException
	{
		if (StringUtils.isBlank(name)) {
			throw new IllegalArgumentException("name must not be NULL/blank.");
		}

		assertWorkspaceOpen();

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

		LOG.info("createNewProject(): Creating project '"+name+"'");
		final ProjectConfiguration config = new ProjectConfiguration(baseDir);
		config.setProjectName( name );
		config.create();

		final IAssemblyProject result = new AssemblyProject( this , config );
		projects.add( result );
		
		try {
			rememberProjectDirectories();
		} 
		catch(IOException e) 
		{
			LOG.error("createNewProject(): Failed to save metadata",e);
			projects.remove( result );
			throw e;
		}
		
		// register project as resource listener
		addResourceListener( result );
		
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
	public void deleteProject(final IAssemblyProject project) throws IOException
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
				
				removeResourceListener( existing );
				
				try {
					rememberProjectDirectories();
				} 
				catch (IOException e) {
					LOG.error("createNewProject(): Failed to save metadata",e);					
					projects.add( project );
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
	public void saveMetaData(IAssemblyProject project) throws IOException
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
	public void close() throws IOException {
		this.opened.set( false );
		rememberProjectDirectories();
	}

	@Override
	public void open() throws IOException 
	{
		reloadWorkspace();
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

        synchronized (listeners) {
            listeners.add( listener );
        }        
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

}
