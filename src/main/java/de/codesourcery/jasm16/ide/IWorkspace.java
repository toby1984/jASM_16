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

import de.codesourcery.jasm16.compiler.io.IResource;

public interface IWorkspace
{
	public File getBaseDirectory();
	
    public boolean doesProjectExist(String name);
    
    public IAssemblerProject getProjectByName(String name);
    
    public IAssemblerProject createNewProject(String name) throws IOException;

    public List<IAssemblerProject> getAllProjects();
    
    public void deleteProject(IAssemblerProject project);
    
    public void saveOrUpdateProject(IAssemblerProject project);
    
    public void resourceChanged(IResource resource);
    
    public void addWorkspaceListener(IWorkspaceListener listener);
    
    public void removeWorkspaceListener(IWorkspaceListener listener);
    
    public void reloadWorkspace();
}
