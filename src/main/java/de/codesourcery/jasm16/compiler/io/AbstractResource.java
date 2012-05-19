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

import java.io.IOException;

/**
 * Abstract resource base-class.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AbstractResource implements IResource {

	private final ResourceType type;

	protected AbstractResource(ResourceType type) {
		if (type == null) {
			throw new IllegalArgumentException("type must not be NULL");
		}
		this.type = type;
	}
	
	
	public final ResourceType getType() {
		return type;
	}

	
	public boolean hasType(ResourceType t) {
		if (t == null) {
			throw new IllegalArgumentException("t must not be NULL");
		}
		return getType().equals( t );
	}
	
	
	public boolean supportsDelete() {
		return false;
	}
	
	
	public void delete() throws IOException {
		throw new UnsupportedOperationException("Not supported");
	}

}
