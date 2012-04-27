package de.codesourcery.jasm16.ide;

import java.io.File;

import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.SimpleFileObjectCodeWriterFactory;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;

public class AssemblerProject extends AbstractAssemblerProject
{
    private File baseDirectory;
    private File outputFolder;
    
    private final FileResourceResolver resourceResolver =new FileResourceResolver();
    
    public AssemblerProject(String name)
    {
        super(name);
    }

    public void setOutputFolder(File outputFolder)
    {
        if (outputFolder == null) {
            throw new IllegalArgumentException("outputFolder must not be NULL.");
        }
        this.outputFolder = outputFolder;
    }
    
    public File getOutputFolder()
    {
        return outputFolder;
    }
    
    @Override
    public IObjectCodeWriterFactory getObjectCodeWriterFactory()
    {
        return new SimpleFileObjectCodeWriterFactory( new File( outputFolder , getName() ) , false ); 
    }

    public File getBaseDirectory()
    {
        return baseDirectory;
    }

    public void setBaseDirectory(File baseDirectory)
    {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public IResource resolve(String identifier) throws ResourceNotFoundException
    {
        return resourceResolver.resolve( identifier );
    }

    @Override
    public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException
    {
        return resourceResolver.resolveRelative( identifier , parent );
    }

}
