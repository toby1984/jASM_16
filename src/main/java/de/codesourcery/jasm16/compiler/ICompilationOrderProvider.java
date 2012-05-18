package de.codesourcery.jasm16.compiler;

import java.util.List;

import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.exceptions.UnknownCompilationOrderException;

/**
 * Used to determine the order in which <code>ICompilationUnit</code>s will be compiled.
 * 
 * @author tobias.gierke@voipfuture.com
 */
public interface ICompilationOrderProvider
{
    /**
     * Determine the order in which a given set of <code>ICompilationUnit</code>s should be compiled.
     * 
     * @param units
     * @param resolver resource resolver used to resolve source files while processing "includesource" directives
     * @return
     * @throws UnknownCompilationOrderException
     * @throws ResourceNotFoundException 
     */
    public List<ICompilationUnit> determineCompilationOrder(List<ICompilationUnit> units,IResourceResolver resolver) throws UnknownCompilationOrderException, ResourceNotFoundException;
}
