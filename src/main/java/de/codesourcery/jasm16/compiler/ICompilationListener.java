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

/**
 * Listener that gets invoked when compilation starts/stops and before
 * and after each compilation phase.
 * 
 * <p>Note that listener implementations should be thread-safe (just in
 * case the assembler runs in multi-threaded mode).
 * </p>
 * @author tobias.gierke@code-sourcery.de
 * @see ICompilerPhase 
 */
public interface ICompilationListener {

	/**
	 * Invoked when compilation begins.
	 * 
	 * @param firstPhase the first phase that is going to be executed by the compiler.
	 */
    public void onCompileStart(ICompilerPhase firstPhase);
    
    /**
     * Invoked after compilation has finished.
     * 
     * <p>Note that this method gets always invoked, regardless of the
     * actual compilation outcome.</p>
     * @param lastPhase
     */
    public void afterCompile(ICompilerPhase lastPhase);
    
    /**
     * Invoked before a new compilation phase is executed. 
     * @param phase
     */
    public void start(ICompilerPhase phase);
    
    /**
     * Invoked whenever a compilation phase completed without errors.
     * 
     * @param phase
     */
    public void success(ICompilerPhase phase);  
    
    /**
     * Invoked whenever a compilation phase exited with errors.
     * 
     * @param phase
     */
    public void failure(ICompilerPhase phase); 
    
    /**
     * Invoked when a compilation phase starts processing a new 
     * compilation unit.
     * 
     * @param phase
     * @param unit
     */
    public void start(ICompilerPhase phase,ICompilationUnit unit);
    
    public void skipped(ICompilerPhase phase,ICompilationUnit unit);

    /**
     * Invoked after a compilation phase successfully finished processing 
     * a compilation unit.
     * 
     * @param phase
     * @param unit
     */
    public void success(ICompilerPhase phase,ICompilationUnit unit);    
 
    /**
     * Invoked after a compilation phase failed processing 
     * a compilation unit.
     * 
     * @param phase
     * @param unit
     */    
    public void failure(ICompilerPhase phase,ICompilationUnit unit);     
}
