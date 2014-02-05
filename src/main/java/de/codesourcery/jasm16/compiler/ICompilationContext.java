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
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * Provides information related to the compilation unit that is currently 
 * being compiled.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationContext extends IResourceResolver , ICompilationUnitResolver {

    /**
     * Returns the compilation unit that is currently being processed.
     * @return
     */
    public ICompilationUnit getCurrentCompilationUnit();
    
    /**
     * Returns the global symbol table.
     * 
     * @return
     */
    public IParentSymbolTable getSymbolTable();
    
    public DebugInfo getDebugInfo();

    /**
     * Returns a factory for creating {@link IObjectCodeWriter} instances
     * to use when emitting object code.
     * 
     * <p>The final compilation phase needs to invoke {@link IObjectCodeWriterFactory#closeObjectWriters()}
     * when no more object code will be generated.</p>
     * @return
     * @see #closeObjectWriters()
     */
    public IObjectCodeWriterFactory getObjectCodeWriterFactory();

	/**
	 * Check whether a specific compiler option is enabled.
	 * 
	 * @param option
	 * @return
	 */
	public boolean hasCompilerOption(CompilerOption option);   
	
	/**
	 * Adds a marker to the current compilation unit.
	 * 
	 * @param marker
	 */
	public void addMarker(IMarker marker);
	
    /**
     * Add a compilation error.
     * 
     * <p>Convenience method that uses {@link ICompilationUnit#addMarker(IMarker)} 
     * to add an error that encompasses a specific AST node to the current compilation unit.</p>
     * @param message
     * @param node
     * @see ICompilationUnit#addMarker(IMarker)
     */
    public void addCompilationError(String message, ASTNode node); 	
}