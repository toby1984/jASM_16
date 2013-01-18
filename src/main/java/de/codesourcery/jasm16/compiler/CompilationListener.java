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
 * No-op implementation of the {@link ICompilationListener} interface
 * suitable for subclassing.
 * 
 * <p>All methods of this class have empty implements.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class CompilationListener implements ICompilationListener
{
    @Override
    public void start(ICompilerPhase phase) {
    }

    @Override
    public void success(ICompilerPhase phase) {
    }

    @Override
    public void failure(ICompilerPhase phase) {
    }

	@Override
	public void failure(ICompilerPhase phase, ICompilationUnit unit) {
	}

	@Override
	public void start(ICompilerPhase phase, ICompilationUnit unit) {
	}

	@Override
	public void success(ICompilerPhase phase, ICompilationUnit unit) {
	}

    @Override
    public void onCompileStart(ICompilerPhase firstPhase)
    {
    }

    @Override
    public void afterCompile(ICompilerPhase lastPhase)
    {
    }

    @Override
    public void skipped(ICompilerPhase phase, ICompilationUnit unit)
    {
    }
}