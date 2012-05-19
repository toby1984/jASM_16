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

import de.codesourcery.jasm16.compiler.io.IResource;

public class WorkspaceListener implements IWorkspaceListener {

	
	public void resourceCreated(IAssemblyProject project, IResource resource) {
	}

	
	public void resourceDeleted(IAssemblyProject project, IResource resource) {
	}

	
	public void resourceChanged(IAssemblyProject project, IResource resource) {
	}

	
	public void projectCreated(IAssemblyProject project) {
	}

	
	public void projectDeleted(IAssemblyProject project) {
	}

	
	public void buildStarted(IAssemblyProject project) {
	}

	
	public void buildFinished(IAssemblyProject project, boolean success) {
	}

	
	public void projectClosed(IAssemblyProject project) {
	}
	
	
	public void projectOpened(IAssemblyProject project) {
	}	

}
