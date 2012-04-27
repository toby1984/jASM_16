package de.codesourcery.jasm16.ide;

import java.util.List;

import de.codesourcery.jasm16.compiler.io.IResource;

public interface IWorkspace
{
    public List<IAssemblerProject> getAllProjects();
    
    public boolean doesProjectExist(String name);
    
    public IAssemblerProject createNewProject(String name);
    
    public void deleteProject(IAssemblerProject project);
    
    public void saveOrUpdateProject(IAssemblerProject project);
    
    public void resourceChanged(IResource resource);
    
    public void addWorkspaceListener(IWorkspaceListener listener);
    
    public void removeWorkspaceListener(IWorkspaceListener listener);
}
