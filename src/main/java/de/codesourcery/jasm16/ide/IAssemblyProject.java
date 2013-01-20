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
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.emulator.IEmulationOptionsProvider;

public interface IAssemblyProject extends IResourceResolver, IWorkspaceListener,IEmulationOptionsProvider
{
    public String getName();
    
    public ProjectConfiguration getConfiguration();
    
    public List<IResource> getResources(ResourceType type);
    
    public boolean containsResource(IResource resource);
    
    public void addedToWorkspace(IWorkspace workspace);
    
    public void removedFromWorkspace(IWorkspace workspace);
    
    public IProjectBuilder getProjectBuilder();
    
    /**
     * 
     * @param file
     * @return resource or <code>null</code> if this resource is currently not being
     * managed by this project / unknown to this project.
     */
    public IResource getResourceForFile(File file);
    
    /**
     * Check whether this project is the same as another.
     * 
     * @param other other project, may be <code>null</code>.
     * @return
     */
    public boolean isSame(IAssemblyProject other);
    
    public IResourceResolver getResourceResolver();    
    
    public boolean isOpen();
    
    public boolean isClosed();
    
    public void reload() throws IOException;
    
    public List<IResource> getAllResources();
    
    public IResource lookupResource(String identifier);
}
