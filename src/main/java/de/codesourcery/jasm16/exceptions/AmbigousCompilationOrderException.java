package de.codesourcery.jasm16.exceptions;

import java.util.List;

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationOrderProvider;
import de.codesourcery.jasm16.compiler.DefaultCompilationOrderProvider.DependencyNode;

/**
 * Thrown by {@link ICompilationOrderProvider}s that were unable to determine the compilation order
 * for a given set of {@link ICompilationUnit}s.  
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see ICompilationOrderProvider#determineCompilationOrder(de.codesourcery.jasm16.compiler.ICompiler, List)
 */
public class AmbigousCompilationOrderException extends UnknownCompilationOrderException
{
    private final List<DependencyNode> rootSet;
    
    public AmbigousCompilationOrderException(String message, List<DependencyNode> rootSet) {
        this( message, rootSet , null );
    }
    
    public AmbigousCompilationOrderException(List<DependencyNode> rootSet) {
        this("Unable to determine compilation order ",rootSet);
    }
    
    public AmbigousCompilationOrderException(String message, List<DependencyNode> rootSet,Throwable cause) {
        super( message , cause );
        this.rootSet = rootSet;
    }    
    
    public List<DependencyNode> getDependencyGraph()
    {
        return rootSet;
    }    
}
