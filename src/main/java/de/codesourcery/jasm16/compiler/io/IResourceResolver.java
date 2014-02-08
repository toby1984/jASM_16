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
package de.codesourcery.jasm16.compiler.io;

import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;

/**
 * Responsible for looking-up an {@link IResource} instance by string identifier.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see IResourceResolver#resolve(String)
 */
public interface IResourceResolver
{
	/**
	 * Resolver whose methods always fail with {@link ResourceNotFoundException}.
	 */
	public static final IResourceResolver NOP_RESOLVER = new IResourceResolver() {

		@Override
		public IResource resolve(String identifier)throws ResourceNotFoundException {
			throw new ResourceNotFoundException("Not implemented: resolve("+identifier+")",identifier);
		}

		@Override
		public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException 
		{
			throw new ResourceNotFoundException("Not implemented: resolve("+identifier+","+parent+")",identifier);			
		}
		
	};
	
    /**
     * Look-up a resource by it's identifier.
     * 
     * <p>
     * The outcome of passing "relative" identifiers to this method is undefined.
     * Use {@link #resolveRelative(String, IResource)} if you suspect the identifier
     * may be a relative resource reference.
     * </p>
     * 
     * @param identifier
     * @return
     * @throws ResourceNotFoundException
     */
    public IResource resolve(String identifier) throws ResourceNotFoundException;
    
    /**
     * Look-up a resource while assuming that relative identifiers should be resolved relative to a specific parent resource. 
     * 
     * <p>
     * If the identifier is an absolute reference, behaves exactly like {@link #resolve(String)}. 
     * If the identifier is a relative reference, the reference is resolved relative to 
     * the given parent resource.
     * </p>
     * 
     * @param identifier
     * @param parent parent resource
     * @return
     * @throws ResourceNotFoundException
     */
    public IResource resolveRelative(String identifier,IResource parent) throws ResourceNotFoundException;    
}
