package de.codesourcery.jasm16.ide;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.StringResource;

public abstract class AbstractAssemblerProject implements IAssemblerProject
{
    private String name;
    private final List<IResource> allResources = new ArrayList<IResource>();

    private final List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
    
    public AbstractAssemblerProject(String name) 
    {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be NULL/blank.");
        }
        this.name = name;
    }

    @Override
    public String getName()
    {
        return name;
    }
    
    @Override
    public List<ICompilationUnit> getCompilationUnits()
    {
        return new ArrayList<ICompilationUnit>( units );
    }
    
    @Override
    public void setCompilationUnits(List<ICompilationUnit> units)
    {
        if (units == null) {
            throw new IllegalArgumentException("units must not be NULL.");
        }
        this.units.clear();
        this.units.addAll( units );
    }
    
    @Override
    public void registerResource(IResource resource)
    {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be NULL.");
        }
        for ( IResource existing : allResources ) {
            if ( existing.getIdentifier().equals( resource.getIdentifier() ) ) {
                throw new IllegalArgumentException("Resource "+resource+" already registered.");
            }
        }
        allResources.add( resource );
    }

    @Override
    public void unregisterResource(IResource resource)
    {
        for (Iterator<IResource> it = allResources.iterator(); it.hasNext();) {
            final IResource existing = it.next();
            if ( existing.getIdentifier().equals( resource.getIdentifier() ) ) {
                it.remove();
                return;
            }
        }        
    }

    @Override
    public List<IResource> getSourceFiles()
    {
        final List<IResource> result = new ArrayList<IResource>();

        for ( IResource r : getAllResources() ) {
            if ( isSourceFile( r ) ) {
                result.add( r );
            }
        }
        return result;
    }

    private boolean isSourceFile(IResource res) 
    {
        if ( res instanceof FileResource ) {
            final File file = ((FileResource) res).getFile();
            final String fileName = file.getName();
            return fileName.toLowerCase().endsWith(".dasm16") || fileName.endsWith(".dasm" ) || fileName.endsWith(".asm");
        } 
        if ( res instanceof StringResource ) {
            return true;
        }
        return false;
    }

    @Override
    public List<IResource> getAllResources()
    {
        return new ArrayList<IResource>( this.allResources );
    }

}
