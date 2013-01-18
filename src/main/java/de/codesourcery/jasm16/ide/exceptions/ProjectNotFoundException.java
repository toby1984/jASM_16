package de.codesourcery.jasm16.ide.exceptions;

/**
 * Thrown if some project could not be found/resolved. 
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ProjectNotFoundException extends RuntimeException
{
    public ProjectNotFoundException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ProjectNotFoundException(String message)
    {
        super(message);
    }
}
