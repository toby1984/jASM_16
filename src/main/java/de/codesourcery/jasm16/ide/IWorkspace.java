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
import java.util.Collection;
import java.util.List;

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.ide.exceptions.ProjectAlreadyExistsException;
import de.codesourcery.jasm16.ide.exceptions.ProjectNotFoundException;
import de.codesourcery.jasm16.utils.IOrdered;

/**
 * A workspace.
 * 
 * <p>A workspace instance manages a set of {@link IAssemblyProject} instances and
 * allows {@link IWorkspaceListener}s to receive change notifications for these projects.</p>
 * @author tobias.gierke@code-sourcery.de
 */
public interface IWorkspace extends IResourceListener
{
	public File getBaseDirectory(); // ok
	
	// project management
    public boolean doesProjectExist(String name); // ok
    
    public IAssemblyProject getProjectByName(String name); // ok
    
    public IAssemblyProject importProject(File baseDirectory) throws IOException,ProjectAlreadyExistsException;
    
    public IAssemblyProject createNewProject(String name) throws IOException,ProjectAlreadyExistsException;

    public IAssemblyProject getProjectForResource(IResource resource) throws ProjectNotFoundException;
    
    public List<IAssemblyProject> getAllProjects(); // ok
    
    public void openProject(IAssemblyProject project);
    
    public void closeProject(IAssemblyProject project);
    
    public void deleteProject(IAssemblyProject project,boolean deletePhysically) throws IOException;
    
    public void refreshProjects(Collection<IAssemblyProject> projects) throws IOException;
    
    public IBuildManager getBuildManager();
    
    /**
     * Delete a file.
     * 
     * <p>If the file is actually the project's base directory, this method
     * behaves like invoking {@link #deleteProject(IAssemblyProject, boolean)}
     * with <code>deletePhysically</code> set to <code>true</code>.</p>
     * 
     * @param project
     * @param fileToDelete
     * @throws IOException
     */
    public void deleteFile(IAssemblyProject project,File fileToDelete) throws IOException;
    
    public void saveProjectConfiguration(IAssemblyProject project) throws IOException;
    
    // workspace listeners
    /**
     * Adds a workspace listener.
     * 
     * <p>Listeners may implement {@link IOrdered} to control invocation order.</p>
     * 
     * @param listener
     */
    public void addWorkspaceListener(IWorkspaceListener listener);
    
    public void removeWorkspaceListener(IWorkspaceListener listener);
    
    /**
     * Adds a resource listener.
     * 
     * <p>Listeners may implement {@link IOrdered} to control invocation order.</p>
     * 
     * @param listener
     */    
    public void addResourceListener(IResourceListener listener);
    
    public void removeResourceListener(IResourceListener listener);
    
    // state management
    public void open() throws IOException; // ok
    
    public void close() throws IOException; // ok
    
    public void reloadWorkspace() throws IOException; // ok
    
	public void buildStarted(IAssemblyProject assemblyProject);
	
	public void compilationFinished(IAssemblyProject project, ICompilationUnit unit);	

	public void buildFinished(IAssemblyProject assemblyProject, boolean buildSuccessful);
}