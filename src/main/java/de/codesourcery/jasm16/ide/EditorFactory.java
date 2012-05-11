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

import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.ui.views.IEditorView;
import de.codesourcery.jasm16.ide.ui.views.SourceEditorView;

public class EditorFactory {

	public static IEditorView createEditor(IAssemblyProject project,IResource resource) {
		
		if ( resource.hasType( ResourceType.SOURCE_CODE ) ) {
			return new SourceEditorView();
		}
		throw new IllegalArgumentException("Unsupported resource: "+resource);
	}
}
