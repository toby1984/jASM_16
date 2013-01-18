package de.codesourcery.jasm16.ide;

import java.io.IOException;

import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.io.IResource;

public class BuildManager implements IBuildManager , IWorkspaceListener
{
    private final IWorkspace workspace;

    @Override
    public void resourceDeleted(IAssemblyProject project, IResource resource)
    {
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
    public void projectOpened(IAssemblyProject project)
    {
        System.out.println("BUILD-MANAGER: Project opened: "+project);
    }

    public void projectDisposed(IAssemblyProject project) {

    }

    @Override
    public void projectDeleted(IAssemblyProject project)
    {
        // TODO Auto-generated method stub

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

    public BuildManager(IWorkspace workspace)
    {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be NULL.");
        }
        this.workspace = workspace;
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
    public boolean build(IAssemblyProject project) throws IOException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean build(IAssemblyProject project, ICompilationListener listener) throws IOException
    {
        // TODO Auto-generated method stub
        return false;
    }
}