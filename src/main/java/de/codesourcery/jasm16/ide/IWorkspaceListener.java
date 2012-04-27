package de.codesourcery.jasm16.ide;

import de.codesourcery.jasm16.compiler.io.IResource;

public interface IWorkspaceListener
{

    public void resourceChanged(IResource resource);
}
