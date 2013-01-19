package de.codesourcery.jasm16.ide;

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
import java.io.IOException;

import de.codesourcery.jasm16.compiler.ICompilationListener;

/**
 * Implementations of this interface know what it means to actually 'build' a project.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IBuildManager 
{
    /**
     * Rebuilds all projects.
     * 
     * @throws IOException
     */
    public void buildAll() throws IOException;
    
    /**
     * Rebuilds all projects.
     * 
     * @param listener
     * @throws IOException
     */
    public void buildAll(ICompilationListener listener) throws IOException;
    
    public IProjectBuilder getProjectBuilder(IAssemblyProject project);
}