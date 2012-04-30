package de.codesourcery.jasm16.ide.ui.views;

import java.io.IOException;

import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.ide.IAssemblyProject;

public interface IEditorView extends IView 
{
	public boolean hasUnsavedContent();

	public IEditorView getOrCreateEditor(IAssemblyProject project , IResource resource);
	
	public void openResource(IAssemblyProject project,IResource resource) throws IOException;
	
	public IResource getCurrentResource();
}
