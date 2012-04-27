package de.codesourcery.jasm16.ide;

import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;

public class UnsavedProject extends AbstractAssemblerProject
{
    public UnsavedProject(String name)
    {
        super(name);
    }

    @Override
    public IObjectCodeWriterFactory getObjectCodeWriterFactory()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IResource resolve(String identifier) throws ResourceNotFoundException
    {
        for ( IResource r : getAllResources() ) {
            if ( r.getIdentifier().equals( identifier ) ) {
                return r;
            }
        }
        throw new ResourceNotFoundException("Resource not found: '"+identifier+"'",identifier);
    }

    @Override
    public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException
    {
        return resolve( identifier );
    }

}
