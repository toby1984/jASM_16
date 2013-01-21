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

import de.codesourcery.jasm16.compiler.ICompilationUnit;

/**
 * A listener that gets notified whenever important
 * changes happen to the {@link IWorkspace} instance the
 * listener was registered with.
 *  
 * @author tobias.gierke@code-sourcery.de
 * 
 * @see IWorkspace#addWorkspaceListener(IWorkspaceListener)
 * @see IWorkspace#removeWorkspaceListener(IWorkspaceListener)
 */
public interface IWorkspaceListener extends IResourceListener
{
	/**
	 * Invoked after a new project was created in this workspace.
	 * 
	 * @param project
	 */
	public void projectCreated(IAssemblyProject project);
	
	/**
	 * 
	 * @param project
	 * @see IWorkspace#close()
	 * @see IWorkspace#closeProject(IAssemblyProject)
	 */
	public void projectClosed(IAssemblyProject project);
	
	/**
	 * Invoked when a closed project has been opened <b>or</b> 
	 * a project has been loaded from disk.
	 * 
	 * @param project
	 * @see IWorkspace#open()
	 * @see IWorkspace#openProject()
	 */
	public void projectOpened(IAssemblyProject project);	
	
	public void projectLoaded(IAssemblyProject project);
	
	public void projectConfigurationChanged(IAssemblyProject project);
	
    /**
     * Invoked after a project has been unloaded from memory.
     * 
     * <p>If a project is deleted, the invocation order is {@link #projectDeleted(IAssemblyProject) } -&gt; {@link #projectDisposed(IAssemblyProject)}.</p>
     * @param project
     */
    public void projectDisposed(IAssemblyProject project);	
	
	/**
	 * Invoked after a project has been deleted from a workspace by the user.
	 * 
	 * @param project
	 */
	public void projectDeleted(IAssemblyProject project);
	
	public void buildStarted(IAssemblyProject project);
	
	public void compilationFinished(IAssemblyProject project,ICompilationUnit unit);
	
	public void buildFinished(IAssemblyProject project,boolean success);
}