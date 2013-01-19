package de.codesourcery.jasm16.ide;

import static de.codesourcery.jasm16.compiler.ICompiler.CompilerOption.GENERATE_RELOCATION_INFORMATION;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.CompiledCode;
import de.codesourcery.jasm16.compiler.Compiler;
import de.codesourcery.jasm16.compiler.DefaultCompilationOrderProvider;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.Linker;
import de.codesourcery.jasm16.compiler.dependencyanalysis.DependencyNode;
import de.codesourcery.jasm16.compiler.dependencyanalysis.DependencyNode.NodeVisitor;
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
import de.codesourcery.jasm16.utils.DebugCompilationListener;
import de.codesourcery.jasm16.utils.Misc;

/**
 * 
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ProjectBuilder implements IProjectBuilder , IResourceListener {

    // @GuardedBy( compUnits )
    private final List<ICompilationUnit> compUnits = new ArrayList<ICompilationUnit>();
    
    private final IResourceMatcher resourceMatcher = DefaultResourceMatcher.INSTANCE;
    private final IWorkspace workspace;
    private final IAssemblyProject project;
    
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    
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
        compiler.setResourceResolver( resolver );
        compiler.setObjectCodeWriterFactory(new NullObjectCodeWriterFactory());

        final List<ICompilationUnit> compUnits = getCompilationUnits();

        final ICompilationUnit result = CompilationUnit.createInstance( source.getIdentifier() , source );

        boolean replaced = false;
        for ( int i =0 ; i < compUnits.size() ; i++ ) {
            final ICompilationUnit unit  = compUnits.get(i);
            if ( unit.getResource().getIdentifier().equals( source.getIdentifier() ) ) 
            {
                compUnits.remove( i );
                compUnits.add( i  , result );
                replaced = true;
                break;
            }
        }

        if ( ! replaced ) 
        {
            compUnits.add(  result );
        }
        compiler.compile( compUnits , listener );
        return result;
    }
    
    private ProjectConfiguration getConfiguration() {
    	return project.getConfiguration();
    }

    protected ICompiler createCompiler() 
    {
        final ICompiler compiler = new Compiler();

        // set compiler options
        compiler.setCompilerOption(CompilerOption.DEBUG_MODE , true );
        compiler.setCompilerOption(CompilerOption.RELAXED_PARSING , true );
       
        compiler.setCompilationOrderProvider( new DefaultCompilationOrderProvider() 
        {
            @Override
            protected List<ICompilationUnit> resolveAmbigousRootSet(final List<DependencyNode> rootSet) throws AmbigousCompilationOrderException                
            {
                try {
                    return super.resolveAmbigousRootSet(rootSet);
                } 
                catch(AmbigousCompilationOrderException e) 
                {
                    final File compRoot = getConfiguration().getCompilationRoot();
                    if ( compRoot  == null) {
                        throw new AmbigousCompilationOrderException("Unable to determine compilation order - please configure this project's compilation root",rootSet,e);
                    }
                    if ( ! compRoot.exists() ) {
                        throw new AmbigousCompilationOrderException( "Unable to determine compilation order - configuration of project "+project.getName()+" has non-existant compilation root '"+compRoot.getAbsolutePath()+"'",e.getRootSet());
                    }
                    DependencyNode match = null;
                    for ( DependencyNode n : rootSet ) 
                    {
                        if ( checkGraphContainsResource( n , compRoot ) ) 
                        {
                            if ( match != null ) {
                                throw new IllegalStateException("Internal error, file "+compRoot.getAbsolutePath()+" is in more than one compilation root set?");
                            }
                            match = n;
                        }
                    }
                    if ( match == null ) {
                        throw new RuntimeException("Internal error, failed to find file "+compRoot.getAbsolutePath()+" in any compilation root set?");
                    }
                    return gatherCompilationUnits( match );
                }
            }
            
            private List<ICompilationUnit> gatherCompilationUnits(DependencyNode graph) 
            {
                final List<ICompilationUnit> result = new ArrayList<>();
                final NodeVisitor v = new NodeVisitor() {

                    @Override
                    public boolean visit(DependencyNode node)
                    {
                        result.add( node.getCompilationUnit() );
                        return true;
                    }
                };
                graph.visitRecursively( v );
                return result;
            }
            
            private boolean checkGraphContainsResource(DependencyNode graph,final File file) 
            {
                final boolean[] result = new boolean[] { false };
                final String absPath = file.getAbsolutePath();
                final NodeVisitor v = new NodeVisitor() {

                    @Override
                    public boolean visit(DependencyNode node)
                    {
                        if ( node.getCompilationUnit().getResource().getIdentifier().equals( absPath ) ) {
                            result[0] = true;
                            return false;
                        }
                        return true;
                    }
                };
                graph.visitRecursively( v );                    
                return result[0];
            }
        });
        
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
    public boolean build() throws IOException 
    {
    	assertNotDisposed();
        return build( new DebugCompilationListener(true) );
    }

    @Override
    public boolean build(ICompilationListener listener) throws IOException 
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
        try {
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
            
            compiler.compile( compilationUnits , listener , relaxedResolver );

            // create executable
            if ( isCompilationSuccessful( compilationUnits ) )
            {
                final IResource executable = link( objectFiles ,compiler.hasCompilerOption( CompilerOption.GENERATE_RELOCATION_INFORMATION ) );
                workspace.resourceCreated( project , executable );
                buildSuccessful = true; 
            } else {
                buildSuccessful = false;
            }
        } finally {
            workspace.buildFinished( project , buildSuccessful );
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
    public ICompilationUnit getCompilationUnit(IResource resource) throws NoSuchElementException
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
    public List<ICompilationUnit> getCompilationUnits()
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
}