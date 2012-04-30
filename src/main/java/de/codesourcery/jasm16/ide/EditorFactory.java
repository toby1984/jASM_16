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
