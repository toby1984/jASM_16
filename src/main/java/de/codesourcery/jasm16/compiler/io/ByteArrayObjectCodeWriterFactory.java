package de.codesourcery.jasm16.compiler.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import de.codesourcery.jasm16.compiler.ICompilationContext;

public class ByteArrayObjectCodeWriterFactory extends AbstractObjectCodeWriterFactory
{

    private ByteArrayOutputStream out;
    
    @Override
    protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context)
    {
        if ( out == null ) {
            out = new ByteArrayOutputStream();
        }
        return new AbstractObjectCodeWriter() {

            @Override
            protected void closeHook() throws IOException
            {
            }

            @Override
            protected OutputStream createOutputStream() throws IOException
            {
                return out;
            }

            @Override
            protected void deleteOutputHook() throws IOException
            {
                out = null;
            }
        };
    }

    @Override
    protected void deleteOutputHook() throws IOException
    {
        out = null;
    }

    public byte[] getBytes() {
        return out == null ? null : out.toByteArray();
    }
}
