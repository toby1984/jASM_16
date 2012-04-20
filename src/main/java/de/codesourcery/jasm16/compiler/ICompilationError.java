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
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * A compilation error.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationError extends IMarker {

	/**
	 * Returns the error message.
	 * @return
	 */
	public String getMessage();
	
	/**
	 * Returns the exception that may have been the root
	 * cause of this exception.
	 * 
	 * @return exception or <code>null</code>
	 */
	public Throwable getCause();
	
    /**
     * Returns the error's line number or -1.
     * 
     * @return
     */ 	
    public int getLineNumber();
    
    /**
     * Sets the error's line number.
     * 
     * @return
     */     
    public void setLineNumber(int num);    
    
    /**
     * Returns the error's column number or -1.
     * 
     * @return
     */ 
    public int getColumnNumber();
    
    /**
     * Set the error's column number.
     * 
     * @return
     */ 
    public void setColumnNumber(int number);    
    
    /**
     * Returns the error's absolute position in the
     * source code or -1.
     * 
     * @return
     */     
    public int getErrorOffset();
    
    public void setErrorOffset(int offset);    
    
    public ASTNode getNode();
    
    public void setLocation(ITextRegion location);
    
    public ITextRegion getLocation();

    public int getLineStartOffset();

    public void setLineStartOffset(int lineStartOffset);	

}