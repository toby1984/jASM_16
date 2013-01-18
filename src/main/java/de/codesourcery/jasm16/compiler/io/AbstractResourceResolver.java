package de.codesourcery.jasm16.compiler.io;

import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;

public abstract class AbstractResourceResolver implements IResourceResolver
{
    @Override
    public void changeResourceType(IResource resource, ResourceType newType)
    {
        throw new UnsupportedOperationException( "changeResourceType() not implemented , " +
        		"cannot change type of resource "+resource+" from "+resource.getType()+" -> "+newType );
    }
}
