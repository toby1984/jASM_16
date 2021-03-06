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
package de.codesourcery.jasm16.ide;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import de.codesourcery.jasm16.compiler.Executable;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * Implementations of this interface know what it means to actually 'build' a project.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IProjectBuilder extends IResourceListener {

    /**
     *
     * @return <code>true</code> if build was successful, otherwise <code>false</code>     
     * @throws IOException
     */
	public boolean build() throws IOException;
	
	public void dispose();
	
	/**
	 * 
	 * @param listener
	 * @return <code>true</code> if build was successful, otherwise <code>false</code>
	 * @throws IOException
	 */
	public boolean build(ICompilationListener listener) throws IOException;
	
	/**
	 * Parses/compiles a source code file in context of any other
	 * compilation units this builder's project may have.
	 * 
	 * <p>This method is indented for use by source code editors
	 * that need to get hold of an AST for their text.</p>
	 *
	 * <p>For speed reasons, this method does <b>not</b> generate any object code,
	 * use {@link #build()} if you want to do a real build.</p>
	 * 
	 * @param source a resource of type {@link ResourceType#SOURCE_CODE}.
	 * @param listener
	 * @return
	 * @throws IOException
	 */
	public ICompilationUnit parse(IResource source, IResourceResolver resolver , ICompilationListener listener) throws IOException;
	
	/**
	 * Check whether a given compilation unit is part of the generated executable (compilation root).
	 * 
	 * @param unit
	 * @return
	 * @see ProjectConfiguration#getCompilationRoot()
	 */
	public boolean isPartOfExecutable(ICompilationUnit unit);
	
	/**
	 * Returns the executable generated by the last <b>successful</b>
	 * build or <code>null</code>.
	 * 
	 * @return
	 */
	public Executable getExecutable();
	
	/**
	 * Check whether this project needs to be built.
	 * 
	 * <p>A project needs to be built if either no executable is
	 * available or at least one of the compilation units has a 
	 * <code>NULL</code> AST (=has not been built yet).
	 * @return
	 */
    public boolean isBuildRequired();
	
	/**
	 * Removes all derived resources (object files etc.) generated by this builder.
	 * 
	 * @throws IOException
	 */
	public void clean() throws IOException;
	
    /**
     * Returns all compilation units for this builder.
     * 
     * @return
     */
    public  List<ICompilationUnit> getCompilationUnits();
    
    /**
     * Look-up compilation unit by resource.
     * 
     * @param source a resource of type {@link ResourceType#SOURCE_CODE}.
     * @return
     * @throws NoSuchElementException If no compilation unit for the given resource could be found
     */
    public ICompilationUnit getCompilationUnit(IResource source) throws NoSuchElementException;	
}