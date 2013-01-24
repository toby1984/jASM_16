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
package de.codesourcery.jasm16.ide.ui.views;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import de.codesourcery.jasm16.compiler.io.AbstractResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.utils.ITextRegion;

public class InMemorySourceResource extends AbstractResource {
	
	private final IResource persistentResource;
	private final JTextComponent editor;
	
	public InMemorySourceResource(IResource resourceOnDisk , JTextComponent editor) 
	{
		super(ResourceType.SOURCE_CODE);
		if ( resourceOnDisk == null ) {
			throw new IllegalArgumentException("resourceOnDisk must not be null");
		}
		if ( resourceOnDisk instanceof InMemorySourceResource) {
		    throw new IllegalArgumentException("InMemorySourceResource called with another InMemorySourceResource as source ?");
		}
		if ( editor == null ) {
			throw new IllegalArgumentException("editor must not be null");
		}
		this.persistentResource = resourceOnDisk;
		this.editor = editor;
	}
	
    @Override
    public String readText(ITextRegion range) throws IOException
    {
        return range.apply( getTextFromEditor() );
    }
    
    public IResource getPersistentResource() {
		return persistentResource;
	}
    
    public final String getTextFromEditor() 
    {
        final int len = editor.getDocument().getLength();
        if ( len == 0 ) {
            return "";
        }
        try {
            return editor.getDocument().getText( 0 , len );
        } catch (BadLocationException e) {
            throw new RuntimeException("bad location: ",e);
        }
    }    

    @Override
    public String getIdentifier()
    {
        return persistentResource.getIdentifier();
    }

    @Override
    public long getAvailableBytes() throws IOException
    {
        return editor.getDocument().getLength();
    }

    @Override
    public OutputStream createOutputStream(boolean append) throws IOException
    {
        throw new UnsupportedOperationException("Cannot save to "+this);
    }

    @Override
    public InputStream createInputStream() throws IOException
    {
        return new ByteArrayInputStream( getTextFromEditor().getBytes() );
    }
}