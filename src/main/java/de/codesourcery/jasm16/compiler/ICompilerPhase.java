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

import java.util.List;
import java.util.Set;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * A compiler phase.
 * 
 * <p>The compiler (assembler) is actually implemented in terms
 * of generic compilation phases that are executed one after another until
 * either compilation fails (and the phase indicates that compilation must 
 * not continue in this case) or there are no more phases to be executed.
 * </p>
 * <p>Each compiler phase needs to have a unique name (identifier) that is being
 * used to refer to the phase). 
 * </p>
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilerPhase 
{
	/**
	 * Name of the 'parse source' default compiler phase.
	 * 
	 * This phase is responsible for transforming the source code
	 * it into an {@link AST}. 
	 */
    public static final String PHASE_PARSE = "parse";
    
    /**
     * Name of macro expansion phase.
     */
    public static final String PHASE_EXPAND_MACROS = "expand-macros";
    
    /**
     * Name of first validation phase.
     */
    public static final String PHASE_VALIDATE_AST1 = "ast-validation1";
    /**
     * Name of first address (label) resolution phase.
     */
    public static final String PHASE_RESOLVE_ADDRESSES = "resolve-addresses";
    
    /**
     * Name of expression (constant) folding phase.
     */
    public static final String PHASE_FOLD_EXPRESSIONS = "fold-expressions";
    
    /**
     * Validation phase that gets run right before generating the object code.
     */
    public static final String PHASE_VALIDATE_AST2 = "ast-validation2"; 
    /**
     * Code generation phase.
     */
	public static final String PHASE_GENERATE_CODE = "gen-code";    
	
    /**
     * Execute this compiler phase.
     * 
     * @param units list of compilation units to process. Note that this list actually gets MODIFIED when
     * source includes are being processed (new compilation units will be added then).
     * @param globalSymbolTable global symbol table
     * @param writerFactory Used to obtain a writer for outputting object code
     * @param listener
     * @param resourceResolver 
     * @param options 
     * @param compUnitResolver
     * @return <code>true</code> if compilation should continue
     */
    public boolean execute(List<ICompilationUnit> units, 
            DebugInfo debugInfo,
    		IParentSymbolTable globalSymbolTable , 
    		IObjectCodeWriterFactory writerFactory, 
    		ICompilationListener listener, 
    		IResourceResolver resourceResolver, 
    		Set<CompilerOption> options, ICompilationUnitResolver compUnitResolver);
    
    /**
     * Returns the unique name of this phase.
     * 
     * @return
     */
    public String getName();
    
    /**
     * Returns whether compilation should always stop after this phase.
     * 
     * @return
     */
    public boolean isStopAfterExecution();
    
    /**
     * Set whether compilation should always stop after this phase.
     * 
     * @param yesNo
     */
    public void setStopAfterExecution(boolean yesNo);
}