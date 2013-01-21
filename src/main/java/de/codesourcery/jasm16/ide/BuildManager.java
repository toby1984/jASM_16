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

import java.io.IOException;
import java.util.IdentityHashMap;

import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.IResource;

public class BuildManager implements IBuildManager , IWorkspaceListener
{
	private final IWorkspace workspace;
    
    private final IdentityHashMap<IAssemblyProject,IProjectBuilder> builders = new IdentityHashMap<>();

    public BuildManager(IWorkspace workspace)
    {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be NULL.");
        }
        this.workspace = workspace;
    }
    
    @Override
    public void resourceDeleted(IAssemblyProject project, IResource resource)
    {
        // TODO Auto-generated method stub

    }
    
    @Override
    public void compilationFinished(IAssemblyProject project,
    		ICompilationUnit unit) {
    	// TODO Auto-generated method stub
    }

    @Override
    public void resourceCreated(IAssemblyProject project, IResource resource)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void resourceChanged(IAssemblyProject project, IResource resource)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void projectConfigurationChanged(IAssemblyProject project) {
    	// TODO Auto-generated method stub
    	
    }
    
    @Override
    public void projectLoaded(IAssemblyProject project)
    {
    }
    @Override
    public void projectOpened(IAssemblyProject project)
    {
    }

    public void projectDisposed(IAssemblyProject project) 
    {
    	synchronized( builders ) 
    	{
    		final IProjectBuilder builder = builders.remove( project );
    		if ( builder != null ) 
    		{
    			workspace.removeResourceListener( builder );
    			builder.dispose();
    		}
    	}
    }

    @Override
    public void projectDeleted(IAssemblyProject project)
    {
    	
    }

    @Override
    public void projectCreated(IAssemblyProject project)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void projectClosed(IAssemblyProject project)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void buildStarted(IAssemblyProject project)
    {
    }

    @Override
    public void buildFinished(IAssemblyProject project, boolean success)
    {
    }

    @Override
    public void buildAll() throws IOException
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void buildAll(ICompilationListener listener) throws IOException
    {
        // TODO Auto-generated method stub
    }

	@Override
	public IProjectBuilder getProjectBuilder(IAssemblyProject project) 
	{
		IProjectBuilder result;
    	synchronized( builders ) 
    	{
    		result = builders.get(project);
    		if ( result == null ) {
    			result = new ProjectBuilder( workspace , project );
    			builders.put( project , result);
    			workspace.addResourceListener( result );
    		}
    	}
		return result;
	}
}