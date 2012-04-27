package de.codesourcery.jasm16.ide;

import java.util.List;

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

public interface IAssemblerProject extends IResourceResolver
{
    public String getName();
    
    public IObjectCodeWriterFactory getObjectCodeWriterFactory();
    
    public void registerResource(IResource resource );
    
    public void unregisterResource(IResource resource );    
    
    public List<IResource> getSourceFiles();
    
    public List<IResource> getAllResources();
    
    /**
     * Returns this projects compilation units.
     * 
     * @return
     */
    public  List<ICompilationUnit> getCompilationUnits();
    
    public void setCompilationUnits(List<ICompilationUnit> units);
}
