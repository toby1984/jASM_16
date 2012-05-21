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
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * A compilation warning that includes a source-code reference.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class CompilationWarning extends CompilationMarker 
{
    private void setNodeAndLocation(ASTNode node,ITextRegion range) 
    {
        if ( node != null ) 
        {
            setAttribute(IMarker.ATTR_AST_NODE , node );
            if ( range == null && node.getTextRegion() != null ) 
            {
                setLocation( new TextRegion( node.getTextRegion() ) );
                if ( ! hasAttribute( IMarker.ATTR_SRC_OFFSET ) ) {
                    setErrorOffset( node.getTextRegion().getStartingOffset() );
                }               
            }
        } 
        
        if ( range != null ) 
        {
            setLocation( new TextRegion( range ) );
            if ( ! hasAttribute( IMarker.ATTR_SRC_OFFSET ) ) {
                setErrorOffset( range.getStartingOffset() );
            }               
        }
    }
    
    public CompilationWarning(String message, ICompilationUnit unit , ASTNode node) 
    {
        super(IMarker.TYPE_COMPILATION_WARNING, message,unit,Severity.WARNING);
        setNodeAndLocation(node,null);
    }    
    
    public CompilationWarning(String message, ICompilationUnit unit , ASTNode node,Throwable cause) 
    {
        super(IMarker.TYPE_COMPILATION_WARNING, message,unit,cause,Severity.WARNING);
        setNodeAndLocation(node,null);      
    }   
    
    public CompilationWarning(String message, ICompilationUnit unit ,ITextRegion location) 
    {
        super(IMarker.TYPE_COMPILATION_WARNING, message,unit,null,Severity.WARNING);
        setNodeAndLocation(null,location);
    }
    
    public CompilationWarning(String message, ICompilationUnit unit , ITextRegion location,Throwable cause) 
    {
        super(IMarker.TYPE_COMPILATION_WARNING, message,unit,cause,Severity.WARNING);
        setNodeAndLocation(null,location);
    }   
        
}
