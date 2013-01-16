package de.codesourcery.jasm16.compiler;

import de.codesourcery.jasm16.compiler.io.IResource;


public final class CompiledCode 
{
    private final IResource objectCode;
    private final ICompilationUnit compilationUnit;

    public CompiledCode(ICompilationUnit compilationUnit, IResource objectCode)
    {
        if ( compilationUnit == null ) {
            throw new IllegalArgumentException("compilationUnit must not be NULL.");
        }
        if ( objectCode == null ) {
            throw new IllegalArgumentException("objectCode must not be NULL.");
        }
        this.compilationUnit = compilationUnit;
        this.objectCode = objectCode;
    }
    
    public IResource getObjectCode()
    {
        return objectCode;
    }
    
    public ICompilationUnit getCompilationUnit()
    {
        return compilationUnit;
    }
} 