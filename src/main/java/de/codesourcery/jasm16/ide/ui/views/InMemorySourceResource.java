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
	
	private final IResource resourceOnDisk;
	private final JTextComponent editor;
	
	public InMemorySourceResource(IResource resourceOnDisk , JTextComponent editor) 
	{
		super(ResourceType.SOURCE_CODE);
		if ( resourceOnDisk == null ) {
			throw new IllegalArgumentException("resourceOnDisk must not be null");
		}
		if ( editor == null ) {
			throw new IllegalArgumentException("editor must not be null");
		}
		this.resourceOnDisk = resourceOnDisk;
		this.editor = editor;
	}
	
    @Override
    public String readText(ITextRegion range) throws IOException
    {
        return range.apply( getTextFromEditor() );
    }
    
    public IResource getResourceOnDisk() {
		return resourceOnDisk;
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
        return resourceOnDisk.getIdentifier();
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

    @Override
    public boolean isSame(IResource other)
    {
        if ( other == this ) {
            return true;
        }
        return false;
    }
};