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
 * Generic marker that may be added to a {@link ICompilationUnit}.
 * 
 * <p>Note that in general you should be careful when relying
 * on the availability of marker attributes ; better safe than sorry.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see ICompilationUnit#addMarker(IMarker)
 * @see ICompilationUnit#getMarkers(String...)
 */
public interface IMarker
{
    // marker attributes
    /**
     * Offset in source, starting at 0.
     */
    public static final String ATTR_SRC_OFFSET = "location";
    
    /**
     * {@link ITextRegion} instance describing source location.
     */
    public static final String ATTR_SRC_REGION = "region"; 
    
    /**
     * Line in source, starting at 1.
     */
    public static final String ATTR_LINE_NUMBER = "line_number"; 
    
    /**
     * Absolute offset of line start in source.
     */
    public static final String ATTR_LINE_START_OFFSET= "line_start_offset"; 
    
    /**
     * Column at line in source, starting at 1.
     */
    public static final String ATTR_COLUMN_NUMBER = "column_number"; // 
    
    /**
     * {@link Throwable}.
     */
    public static final String ATTR_EXCEPTION = "exception"; 
    
    /**
     * {@link Severity}.
     */
    public static final String ATTR_SEVERITY = "severity"; 
    
    /**
     * {@link ASTNode}.
     */
    public static final String ATTR_AST_NODE = "ast_node";  
    
    /**
     * Human-readable description.
     */
    public static final String ATTR_DESCRIPTION = "description"; 
    
    // marker types
    /**
     * Compilation error marker.
     * 
     * This marker is expected to always have at least {@link #ATTR_SRC_OFFSET} and
     * in most cases will also have {@link #ATTR_SRC_REGION} and {@link #ATTR_AST_NODE}.
     */
    public static final String TYPE_COMPILATION_ERROR = "compilation_error";
    
    /**
     * Compilation warning marker.
     * 
     * This marker is expected to always have at least {@link #ATTR_SRC_OFFSET} and
     * in most cases will also have {@link #ATTR_SRC_REGION} and {@link #ATTR_AST_NODE}.
     */
    public static final String TYPE_COMPILATION_WARNING= "compilation_warning";
    
    /**
     * Generic compilation error marker.
     * 
     * This marker may have {@link #ATTR_SRC_OFFSET} but in a case of an unexpected internal error,
     * even location information may be unavailable. 
     */    
    public static final String TYPE_GENERIC_COMPILATION_ERROR = "generic_compilation_error";
    
    public ICompilationUnit getCompilationUnit();
    
    public String getType();
    
    public boolean hasType(String type);
    
    public boolean hasAttribute(String name);
    
    public int getAttribute(String name,int defaultValue);
    
    public String getAttribute(String name,String defaultValue);
    
    public <T> T getAttribute(String name, T defaultValue);
    
    public void delete();
    
    public void setAttribute(String name,int value);
    
    public void setAttribute(String name,String value);
    
    public void setAttribute(String name,Object value);    
}
