package de.codesourcery.jasm16.compiler.io;

import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamCodeWriter extends AbstractObjectCodeWriter
{
    private final OutputStream out;
    
    public OutputStreamCodeWriter(OutputStream out)
    {
        this.out = out;
    }
    
    @Override
    protected void closeHook() throws IOException
    {
        out.close();
    }
    
    @Override
    protected OutputStream createOutputStream() throws IOException
    {
        return out;
    }
    
    @Override
    protected void deleteOutputHook() throws IOException
    {
        
    }
    
}
