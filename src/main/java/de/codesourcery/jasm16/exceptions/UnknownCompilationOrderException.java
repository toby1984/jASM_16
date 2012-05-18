package de.codesourcery.jasm16.exceptions;

import java.util.List;

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationOrderProvider;

/**
 * Thrown by {@link ICompilationOrderProvider}s that failed to determine the compilation/link order
 * for a given set of {@link ICompilationUnit}s.  
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see ICompilationOrderProvider#determineCompilationOrder(de.codesourcery.jasm16.compiler.ICompiler, List) 
 */
public class UnknownCompilationOrderException extends RuntimeException
{
    public UnknownCompilationOrderException(String message) {
        super( message );
    }
    
    public UnknownCompilationOrderException(String message, Throwable cause) {
        super( message , cause );
    }    
    
}
