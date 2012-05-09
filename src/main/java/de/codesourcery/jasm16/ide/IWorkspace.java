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
import java.util.List;

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
    
    public IAssemblyProject createNewProject(String name) throws IOException;

    public List<IAssemblyProject> getAllProjects(); // ok
    
    public void deleteProject(IAssemblyProject project) throws IOException;
    
    public void saveMetaData(IAssemblyProject project) throws IOException;
    
    // workspace listeners
    public void addWorkspaceListener(IWorkspaceListener listener);
    
    public void removeWorkspaceListener(IWorkspaceListener listener);
    
    public void addResourceListener(IResourceListener listener);
    
    public void removeResourceListener(IResourceListener listener);
    
    // state management
    public void open() throws IOException; // ok
    
    public void close() throws IOException; // ok
    
    public void reloadWorkspace() throws IOException; // ok
}
