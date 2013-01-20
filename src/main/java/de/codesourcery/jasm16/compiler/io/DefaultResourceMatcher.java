package de.codesourcery.jasm16.compiler.io;

public class DefaultResourceMatcher implements IResourceMatcher
{
    public static final IResourceMatcher INSTANCE = new DefaultResourceMatcher();
    
    @Override
    public boolean isSame(IResource r1, IResource r2)
    {
        return r1.getIdentifier().equals( r2.getIdentifier() );
    }
}
