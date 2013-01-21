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
import de.codesourcery.jasm16.compiler.io.IResource;

public class WorkspaceListener implements IWorkspaceListener {

	@Override
	public void resourceCreated(IAssemblyProject project, IResource resource) {
	}

	@Override
	public void resourceDeleted(IAssemblyProject project, IResource resource) {
	}
	
	@Override
	public void compilationFinished(IAssemblyProject project, ICompilationUnit unit) {
	}

	@Override
	public void resourceChanged(IAssemblyProject project, IResource resource) {
	}

	@Override
	public void projectCreated(IAssemblyProject project) {
	}

	@Override
	public void projectLoaded(IAssemblyProject project)
	{
	}
	
	@Override
	public void projectDeleted(IAssemblyProject project) {
	}

	@Override
	public void buildStarted(IAssemblyProject project) {
	}

	@Override
	public void buildFinished(IAssemblyProject project, boolean success) {
	}

	@Override
	public void projectClosed(IAssemblyProject project) {
	}
	
	@Override
	public void projectOpened(IAssemblyProject project) {
	}

    @Override
    public void projectDisposed(IAssemblyProject project)
    {
    }

	@Override
	public void projectConfigurationChanged(IAssemblyProject project) {
		// TODO Auto-generated method stub
	}	
}