/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.jasm16.compiler;

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.utils.ITextRange;

/**
 * Generic compilation error that is not related to a specific source-code location.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see CompilationError
 */
public class GenericCompilationError extends AbstractMarker implements ICompilationError
{

    protected GenericCompilationError(String markerType, String message, ICompilationUnit unit)
    {
        this(markerType, message, unit, null);
    }

    public GenericCompilationError(String message, ICompilationUnit unit)
    {
        this(message, unit, null);
    }

    public GenericCompilationError(String message, ICompilationUnit unit, Throwable cause)
    {
        this(IMarker.TYPE_GENERIC_COMPILATION_ERROR, message, unit, cause);
    }

    protected GenericCompilationError(String markerType, String message, ICompilationUnit unit, Throwable cause)
    {
        super(markerType, unit);
        setAttribute(IMarker.ATTR_SEVERITY, Severity.ERROR);
        if (message != null) {
            setAttribute(IMarker.ATTR_DESCRIPTION, message);
        }
        if (cause != null) {
            setAttribute(IMarker.ATTR_EXCEPTION, cause);
        }
    }

    @Override
    public String getMessage()
    {
        return getAttribute(IMarker.ATTR_DESCRIPTION, "");
    }

    @Override
    public Throwable getCause()
    {
        return getAttribute(IMarker.ATTR_EXCEPTION, (Throwable) null);
    }

    /**
     * Returns the error's line number or -1 if no line number is available.
     * 
     * @return
     */
    @Override
    public int getLineNumber()
    {
        return getAttribute(IMarker.ATTR_LINE_NUMBER, -1);
    }

    public void setLineNumber(int lineNumber)
    {
        setAttribute(IMarker.ATTR_LINE_NUMBER, lineNumber);
    }

    /**
     * Returns the error's column number or -1.
     * 
     * @return
     */
    @Override
    public int getColumnNumber()
    {
        return getAttribute(IMarker.ATTR_COLUMN_NUMBER, -1);
    }

    public void setColumnNumber(int column)
    {
        setAttribute(IMarker.ATTR_COLUMN_NUMBER, column);
    }

    @Override
    public ASTNode getNode()
    {
        return getAttribute(IMarker.ATTR_AST_NODE, (ASTNode) null);
    }

    @Override
    public void setErrorOffset(int offset)
    {
        setAttribute(IMarker.ATTR_SRC_OFFSET, offset);
    }    

    @Override
    public int getErrorOffset()
    {
        return getAttribute(IMarker.ATTR_SRC_OFFSET, -1);
    }

    @Override
    public int getLineStartOffset()
    {
        return getAttribute(IMarker.ATTR_LINE_START_OFFSET, -1);
    }

    @Override
    public void setLineStartOffset(int lineStartOffset)
    {
        setAttribute(IMarker.ATTR_LINE_START_OFFSET, lineStartOffset);
    }

    @Override
    public ITextRange getLocation()
    {
        return getAttribute(IMarker.ATTR_SRC_REGION, (ITextRange) null);
    }
    
    @Override
    public void setLocation(ITextRange location)
    {
        if (location == null) {
            throw new IllegalArgumentException("location must not be NULL.");
        }
        setAttribute( IMarker.ATTR_SRC_REGION , location );        
    }
}
