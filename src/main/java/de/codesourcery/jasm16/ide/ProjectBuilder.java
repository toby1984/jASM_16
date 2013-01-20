package de.codesourcery.jasm16.ide;

import static de.codesourcery.jasm16.compiler.ICompiler.CompilerOption.GENERATE_RELOCATION_INFORMATION;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.CompiledCode;
import de.codesourcery.jasm16.compiler.Compiler;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.IParentSymbolTable;
import de.codesourcery.jasm16.compiler.Linker;
import de.codesourcery.jasm16.compiler.ParentSymbolTable;
import de.codesourcery.jasm16.compiler.dependencyanalysis.DependencyNode;
import de.codesourcery.jasm16.compiler.dependencyanalysis.SourceFileDependencyAnalyzer;
import de.codesourcery.jasm16.compiler.io.DefaultResourceMatcher;
import de.codesourcery.jasm16.compiler.io.FileObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.compiler.io.NullObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.SimpleFileObjectCodeWriterFactory;
import de.codesourcery.jasm16.exceptions.AmbigousCompilationOrderException;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.exceptions.UnknownCompilationOrderException;
import de.codesourcery.jasm16.utils.DebugCompilationListener;
import de.codesourcery.jasm16.utils.IOrdered;
import de.codesourcery.jasm16.utils.Misc;

/**
 * 
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ProjectBuilder implements IProjectBuilder , IResourceListener, IOrdered {

	private static final Logger LOG = Logger.getLogger(ProjectBuilder.class);
	
    // @GuardedBy( compUnits )
    private final List<ICompilationUnit> compUnits = new ArrayList<ICompilationUnit>();
    
    private final IResourceMatcher resourceMatcher = DefaultResourceMatcher.INSTANCE;
    private final IWorkspace workspace;
    private final IAssemblyProject project;
    private final IParentSymbolTable globalSymbolTable = new ParentSymbolTable();
    
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    
    private final SourceFileDependencyAnalyzer analyzer = new SourceFileDependencyAnalyzer();
    
    public ProjectBuilder(IWorkspace workspace,IAssemblyProject project) {
    	if (project == null) {
			throw new IllegalArgumentException("project must not be null");
		}
    	if ( workspace == null ) {
			throw new IllegalArgumentException("workspace must not be null");
		}
    	this.workspace = workspace;
    	this.project = project;
    }
    
    @Override
    public void dispose() {
    	this.disposed.set(true);
    }
    
    private void assertNotDisposed() {
    	if ( disposed.get() ) {
    		throw new IllegalStateException("Builder "+this+" is already disposed");
    	}
    }
    
    @Override
    public ICompilationUnit parse(IResource source, IResourceResolver resolver , ICompilationListener listener) throws IOException 
    {
    	assertNotDisposed();
    	
        final ICompiler compiler = createCompiler();
        
        // do not process .includesource directives here as this would recompile
        // dependent sources as well
        compiler.setCompilerOption(CompilerOption.NO_SOURCE_INCLUDE_PROCESSING , true );
        
        compiler.setResourceResolver( resolver );
        compiler.setObjectCodeWriterFactory(new NullObjectCodeWriterFactory());

        final List<ICompilationUnit> compUnits = getCompilationUnits();
        final ICompilationUnit result = CompilationUnit.createInstance( source.getIdentifier() , source );

        for ( int i =0 ; i < compUnits.size() ; i++ ) {
            final ICompilationUnit unit  = compUnits.get(i);
            if ( unit.getResource().getIdentifier().equals( source.getIdentifier() ) ) 
            {
                compUnits.remove( i );
                break;
            }
        }

        compiler.compile( Collections.singletonList( result )  , 
        		compUnits , 
        		globalSymbolTable , 
        		listener ,
        		DefaultResourceMatcher.INSTANCE );
        
      	workspace.compilationFinished( project , result );
        return result;
    }
    
    private ProjectConfiguration getConfiguration() {
    	return project.getConfiguration();
    }

    protected ICompiler createCompiler() 
    {
        final ICompiler compiler = new Compiler();

        // set compiler options
//        compiler.setCompilerOption(CompilerOption.DEBUG_MODE , true );
        compiler.setCompilerOption(CompilerOption.RELAXED_PARSING , true );
       
        if ( getConfiguration().getBuildOptions().isGenerateSelfRelocatingCode() ) {
            compiler.setCompilerOption(GENERATE_RELOCATION_INFORMATION , true );
        }
        
        final FileResourceResolver delegate  = new FileResourceResolver(); // getConfiguration().getBaseDirectory() ); 
        
        compiler.setResourceResolver( new IResourceResolver() {
            
            @Override
            public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException
            {
                return delegate.resolveRelative(identifier, parent);
            }
            
            @Override
            public IResource resolve(String identifier) throws ResourceNotFoundException
            {
                return delegate.resolve( identifier );
            }
            
            @SuppressWarnings("deprecation")
			@Override
            public void changeResourceType(IResource resource, ResourceType newType)
            {
                if ( project.containsResource( resource ) ) {
                    resource.setType( newType );
                    workspace.resourceChanged( project , resource );
                } else {
                    delegate.changeResourceType(resource,newType);                        
                }
            }
        });
        return compiler;
    }

    protected File getOutputFileForSource(IResource resource) 
    {
        final String objectCodeFile = getNameWithoutSuffix( resource )+".dcpu16";
        final File outputDir = getConfiguration().getOutputFolder();
        return new File( outputDir , objectCodeFile );
    }
    
    protected String getNameWithoutSuffix(IResource resource) {

        String name;
        if ( resource instanceof FileResource) {
            FileResource file = (FileResource) resource;
            name = file.getFile().getName();
        } else {
            name = resource.getIdentifier();
        }

        // get base name
        final String[] components = name.split("["+Pattern.quote("\\/")+"]");
        if ( components.length == 1 ) {
            name = components[0];
        } else {
            name = components[ components.length -1 ];
        }
        if ( ! name.contains("." ) ) {
            return name;
        }
        final String[] dots = name.split("\\.");
        return StringUtils.join( ArrayUtils.subarray( dots , 0 , dots.length-1) );
    }	    
    
    protected void setObjectCodeOutputFactory(final ICompiler compiler,final List<CompiledCode> objectFiles) {

        compiler.setObjectCodeWriterFactory( new SimpleFileObjectCodeWriterFactory() {

            protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context) 
            {
                final ICompilationUnit currentUnit = context.getCurrentCompilationUnit();
                
                final File outputFile = getOutputFileForSource( currentUnit.getResource() );
                
                System.out.println("createObjectCodeWriter(): Compiling "+currentUnit+" to object file "+outputFile.getAbsolutePath());

                final IResource resource = new FileResource( outputFile , ResourceType.OBJECT_FILE );
                return new FileObjectCodeWriter( outputFile , false ) 
                {
                    protected void closeHook() throws IOException 
                    {
                        Address start = getFirstWriteOffset();
                        Address end = getCurrentWriteOffset();
                        final int len;
                        if ( start != null && end != null ) {
                            len = end.toByteAddress().getValue() - start.toByteAddress().getValue();
                        } else {
                            len = 0;
                        }
                        System.out.println("closeHook(): Closing object file "+outputFile.getAbsolutePath()+", bytes_written: "+len );
                        if ( len > 0 ) {
                            objectFiles.add( new CompiledCode( currentUnit , resource ) );
                            workspace.resourceCreated( project , resource );
                        }
                    }

                    protected void deleteOutputHook() throws IOException 
                    {
                        workspace.resourceCreated( project , resource );
                    };
                };
            }
        } );            
    }

    @Override
    public synchronized boolean build() throws IOException 
    {
    	assertNotDisposed();
        return build( new DebugCompilationListener(true) );
    }

    @Override
    public synchronized boolean build(ICompilationListener listener) throws IOException, UnknownCompilationOrderException 
    {
    	assertNotDisposed();
    	
        final ICompiler compiler = createCompiler();

        /* Set output code writer.
         * 
         * The following array list will be populated by the ObjectCodeOutputFactory
         * with all generated object files.
         */
        final List<CompiledCode> objectFiles = new ArrayList<>();
        setObjectCodeOutputFactory( compiler , objectFiles );

        workspace.buildStarted( project );
        
        boolean buildSuccessful = false;
        try 
        {
            // clean output directory
            clean();

            // compile stuff
            final List<ICompilationUnit> compilationUnits = getCompilationUnits();
            
            final IResourceMatcher relaxedResolver = new IResourceMatcher() {
                
                @Override
                public boolean isSame(IResource resource1,IResource resource2) 
                {
                    return resource1.getIdentifier().equals( resource2.getIdentifier() );
                }
            };
            
            
            final List<DependencyNode> rootSet = analyzer.calculateRootSet( compilationUnits , project ,  relaxedResolver );
            
            DependencyNode nodeToCompile = null;
            if ( rootSet.size() > 1 ) 
            {
            	for ( DependencyNode n : rootSet ) 
            	{
                    final List<ICompilationUnit> units  = analyzer.linearize( n );
                    if ( containsCompilationRoot( units ) ) {
                    	nodeToCompile = n;
                    	break;
                    }
            	}
            	if ( nodeToCompile == null ) {
            		throw new AmbigousCompilationOrderException( "Project configuration requires a compilation root", rootSet );
            	}
            } 
            else if ( rootSet.size() == 1 ) 
            {
            	nodeToCompile = rootSet.get(0);
            } else {
            	return true;
            }
            
            final List<ICompilationUnit> units  = analyzer.linearize( nodeToCompile );
            final boolean link = units.size() == 1 || containsCompilationRoot( units );
            buildSuccessful = build( units , listener , link );
        } 
        catch (ResourceNotFoundException e) {
        	LOG.error("build(): Caught ",e);
		} finally {
            workspace.buildFinished( project , buildSuccessful );
        }
        return buildSuccessful;        
    }
    
    private boolean containsCompilationRoot(List<ICompilationUnit> units ) 
    {
    	final File compilationRoot = project.getConfiguration().getCompilationRoot();    	
    	if ( compilationRoot == null ) {
    		return false;
    	}
    	for ( ICompilationUnit unit : units ) 
    	{
    		if ( unit.getResource().getIdentifier().equals( compilationRoot.getAbsolutePath() ) ) {
    			return true;
    		}
    	}
    	return false;
    }

    private boolean build(List<ICompilationUnit> compilationUnits,ICompilationListener listener,boolean link) throws IOException 
    {
        final ICompiler compiler = createCompiler();

        /* Set output code writer.
         * 
         * The following array list will be populated by the ObjectCodeOutputFactory
         * with all generated object files.
         */
        final List<CompiledCode> objectFiles = new ArrayList<>();
        setObjectCodeOutputFactory( compiler , objectFiles );

        boolean buildSuccessful = false;

        // compile stuff
        LOG.info("build(): Starting to build: \n"+StringUtils.join( compilationUnits, "\n" ) );
        
        final IResourceMatcher relaxedResolver = new IResourceMatcher() {
            
            @Override
            public boolean isSame(IResource resource1,IResource resource2) 
            {
                return resource1.getIdentifier().equals( resource2.getIdentifier() );
            }
        };
        
        compiler.compile( compilationUnits , new ArrayList<ICompilationUnit>() , globalSymbolTable , listener , relaxedResolver );

        // create executable
        if ( isCompilationSuccessful( compilationUnits ) )
        {
        	if ( link ) 
        	{
        		LOG.debug("[ "+this+"] Linking "+compilationUnits);
        		final IResource executable = link( objectFiles ,compiler.hasCompilerOption( CompilerOption.GENERATE_RELOCATION_INFORMATION ) );
        		workspace.resourceCreated( project , executable );
        	}
            buildSuccessful = true; 
        } else {
            buildSuccessful = false;
        }
        return buildSuccessful;
    }

    private boolean isCompilationSuccessful( List<ICompilationUnit> compilationUnits) 
    {
        for ( ICompilationUnit unit : compilationUnits ) { 
            if ( unit.hasErrors() ) 
            {
                return false;
            }
        }           
        return true;
    }

    private IResource link(List<CompiledCode> objectFiles,boolean generateRelocatableCode) throws IOException 
    {
        final File outputFolder = getConfiguration().getOutputFolder();
        final File outputFile = new File( outputFolder , getConfiguration().getExecutableName() );
        return new Linker().link( objectFiles , outputFile , generateRelocatableCode , true );
    }

    @Override
    public void clean() throws IOException {
    	assertNotDisposed();
        cleanOutputFolder();
    }

    protected void cleanOutputFolder() throws IOException 
    {
        File folder = getConfiguration().getOutputFolder();
        if ( ! folder.exists() ) {
            if ( ! folder.mkdirs() ) {
                throw new IOException("Failed to create output folder "+folder.getAbsolutePath());
            }
            return;
        }

        for ( File f : folder.listFiles() ) 
        {
            Misc.deleteRecursively( f );
            workspace.resourceDeleted( project , new FileResource( f , ResourceType.UNKNOWN) );
        }
    }
    
    @Override
    public IResource getExecutable() 
    {
    	assertNotDisposed();
    	
        List<IResource> results = project.getResources( ResourceType.EXECUTABLE );
        if ( results.size() == 1 ) {
            return results.get(0);
        } else if ( results.isEmpty() ) {
            return null;
        }
        throw new RuntimeException("Internal error, more than one executable in project "+project+": "+results);
    }
    
    public boolean isBuildRequired() 
    {
    	assertNotDisposed();
    	
        for ( ICompilationUnit unit : getCompilationUnits() ) {
            if ( unit.getAST() == null ) {
                return true;
            }
        }
        if ( getExecutable() == null ) {
            return true;
        }
        return false;
    }

    @Override
    public synchronized ICompilationUnit getCompilationUnit(IResource resource) throws NoSuchElementException
    {
        final ICompilationUnit result = findCompilationUnit( resource );
        if ( result == null ) {
            throw new NoSuchElementException("Could not find compilation unit for "+resource);
        }
        return result;
    }
    
    private ICompilationUnit findCompilationUnit(IResource source) throws NoSuchElementException
    {
        if (source == null) {
            throw new IllegalArgumentException("source must not be NULL");
        }

        for ( ICompilationUnit unit : getCompilationUnits() ) {
            if ( resourceMatcher.isSame( unit.getResource() , source ) ) {
                return unit;
            }
        }
        return null;
    }        

    @Override
    public synchronized List<ICompilationUnit> getCompilationUnits()
    {
        // TODO: Project building is currently NOT thread-safe , compilation units
        // passed to callers may be mutated at any time
        synchronized(compUnits) {
            return new ArrayList<ICompilationUnit>( compUnits );
        }
    }

    @Override
    public void resourceCreated(IAssemblyProject project, IResource resource)
    {
        if ( this.project != project) {
            return;
        }
        
        if ( resource.hasType( ResourceType.SOURCE_CODE ) ) 
        {
            addCompilationUnit( resource );
        }             
    }

    private void addCompilationUnit(IResource resource) 
    {
        System.out.println("New source-code resource added.");
        synchronized (compUnits) 
        {
            if ( findCompilationUnit( resource ) != null ) {
                throw new IllegalStateException("Already got a compilation unit for "+resource+" ?");
            }                
            compUnits.add( CompilationUnit.createInstance( resource.getIdentifier() , resource  ) );
        }
    }
    
    private boolean removeCompilationUnit(IResource resource) 
    {
        synchronized (compUnits) 
        {
            for (Iterator<ICompilationUnit> it = compUnits.iterator(); it.hasNext();) 
            {
                final ICompilationUnit existing = it.next();
                if ( resourceMatcher.isSame( existing.getResource() , resource ) ) {
                    it.remove();
                    return true;
                }
            }            
            return false;
        }
    }
    
    @Override
    public void resourceDeleted(IAssemblyProject project, IResource resource)
    {
        if ( this.project != project) {
            return;
        }
        removeCompilationUnit( resource );
    }

    @Override
    public void resourceChanged(IAssemblyProject project, IResource resource)
    {
        if ( this.project != project) {
            return;
        }

        final ICompilationUnit found = findCompilationUnit( resource );
        if ( found != null ) 
        {
            removeCompilationUnit( resource );
        } 
        
        if ( resource.hasType( ResourceType.SOURCE_CODE  ) ) {
            addCompilationUnit( resource );
        }
    }
    
    @Override
    public String toString() {
    	return "ProjectBuilder[ project="+project+" , disposed="+disposed+"]";
    }

	@Override
	public Priority getPriority() {
		return Priority.HIGHEST;
	}
}