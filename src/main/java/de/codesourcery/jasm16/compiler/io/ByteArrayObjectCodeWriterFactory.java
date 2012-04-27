package de.codesourcery.jasm16.compiler.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.ICompilationContext;

public class ByteArrayObjectCodeWriterFactory extends AbstractObjectCodeWriterFactory
{
    private ByteArrayOutputStream out;
    
    private int firstWriteOffset = 0;
    
    @Override
    protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context)
    {
        if ( out == null ) {
            out = new ByteArrayOutputStream();
        }
        return new AbstractObjectCodeWriter() {

            
            @Override
            public void advanceToWriteOffset(Address offset) throws IOException
            {
                super.advanceToWriteOffset(offset);
                if ( firstWriteOffset == 0 ) {
                    firstWriteOffset = offset.getValue();
                }
            }
            
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
    
    /**
     * Returns the first write offset in BYTES.
     * 
     * @return
     */
    public int getFirstWriteOffset() {
        return firstWriteOffset;
    }

    @Override
    protected void deleteOutputHook() throws IOException
    {
        firstWriteOffset = -1;
        out = null;
    }

    public byte[] getBytes() {
        return out == null ? null : out.toByteArray();
    }
}
