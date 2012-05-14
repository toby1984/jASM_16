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
package de.codesourcery.jasm16.exceptions;

import java.io.File;
import java.io.IOException;

/**
 * Thrown if some {@link File} was expected to be a directory
 * but was not.
 * 
 * @author tobias.gierke@code-sourcery.de
 *
 */
public class NoDirectoryException extends IOException {

	private final File file;
	
	public NoDirectoryException(File file) {
		super("Expected "+file.getAbsolutePath()+" to be a directory but was not");
		this.file = file;
	}
	
	public File getFile() {
		return file;
	}
}
