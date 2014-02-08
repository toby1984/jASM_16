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
package de.codesourcery.jasm16.ide;

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
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.CompiledCode;
import de.codesourcery.jasm16.compiler.Compiler;
import de.codesourcery.jasm16.compiler.DebugInfo;
import de.codesourcery.jasm16.compiler.Executable;
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
    private final IParentSymbolTable globalSymbolTable;
    
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    
    private final SourceFileDependencyAnalyzer analyzer = new SourceFileDependencyAnalyzer();
    
    private volatile Executable executable;
    
    public ProjectBuilder(IWorkspace workspace,IAssemblyProject project) {
    	if (project == null) {
			throw new IllegalArgumentException("project must not be null");
		}
    	if ( workspace == null ) {
			throw new IllegalArgumentException("workspace must not be null");
		}
    	this.workspace = workspace;
    	this.project = project;
    	this.globalSymbolTable = new ParentSymbolTable( "project: "+project.getName() );
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
        // dependent sources as well (and these are probably already compiled)
        compiler.setCompilerOption(CompilerOption.NO_SOURCE_INCLUDE_PROCESSING , true );
        
        compiler.setResourceResolver( resolver );
        compiler.setObjectCodeWriterFactory(new NullObjectCodeWriterFactory());

        final List<ICompilationUnit> toCompile = new ArrayList<>();
        final ICompilationUnit newCompilationUnit = CompilationUnit.createInstance( source.getIdentifier() , source );
        toCompile.add( newCompilationUnit );
        
        globalSymbolTable.clear( newCompilationUnit );
        
        final List<ICompilationUnit> dependencies = new ArrayList<>( getCompilationUnits() );

        for ( int i =0 ; i < dependencies.size() ; i++ ) 
        {
            final ICompilationUnit unit  = dependencies.get(i);
            if ( unit.getResource().getIdentifier().equals( source.getIdentifier() ) ) 
            {
            	dependencies.remove( i );
                break;
            }
        }
        
        for ( Iterator<ICompilationUnit> it = dependencies.iterator() ; it.hasNext() ; ) 
        {
        	final ICompilationUnit dependency = it.next();
        	if ( dependency.getAST() == null ) {
        		it.remove();
        		toCompile.add( dependency );
        	}
        }
        
        compiler.compile( toCompile  , 
        		dependencies , 
        		globalSymbolTable , 
        		listener ,
        		DefaultResourceMatcher.INSTANCE );
        
      	workspace.compilationFinished( project , newCompilationUnit );
        return newCompilationUnit;
    }
    
    private ProjectConfiguration getConfiguration() {
    	return project.getConfiguration();
    }

    protected ICompiler createCompiler() 
    {
        final ICompiler compiler = new Compiler();

        // set compiler options
        final BuildOptions buildOptions = getConfiguration().getBuildOptions();
        
        compiler.setCompilerOption( CompilerOption.DEBUG_MODE , true );
        compiler.setCompilerOption( CompilerOption.RELAXED_PARSING , true );
        compiler.setCompilerOption( CompilerOption.LOCAL_LABELS_SUPPORTED,true );
        compiler.setCompilerOption( CompilerOption.DISABLE_INLINING, ! buildOptions.isInlineShortLiterals() );
        compiler.setCompilerOption( CompilerOption.GENERATE_DEBUG_INFO ,true );
        compiler.setCompilerOption( CompilerOption.GENERATE_RELOCATION_INFORMATION , buildOptions.isGenerateSelfRelocatingCode() );
        
        final FileResourceResolver delegate  = new FileResourceResolver() {
        	@Override
        	protected ResourceType determineResourceType(File file) 
        	{
        		return project.getConfiguration().isSourceFile( file ) ? ResourceType.SOURCE_CODE : ResourceType.UNKNOWN;
        	}
        }; 
        
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

        	private FileObjectCodeWriter lastWriter;
        	
            protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context) 
            {
                final ICompilationUnit currentUnit = context.getCurrentCompilationUnit();
                
                final File outputFile = getOutputFileForSource( currentUnit.getResource() );
                
                final IResource resource = new FileResource( outputFile , ResourceType.OBJECT_FILE );
                
                WordAddress currentOffset = lastWriter == null ? WordAddress.ZERO : lastWriter.getCurrentWriteOffset().toWordAddress();
                
//                System.out.println(">>>>>>>>> createObjectCodeWriter(): Compiling "+currentUnit+" to object file "+outputFile.getAbsolutePath()+" , offset = "+currentOffset);
                
                lastWriter = new FileObjectCodeWriter( outputFile , currentOffset , false ) 
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
//                        System.out.println("closeHook(): [ "+start+" - "+end+" ] Closing object file "+outputFile.getAbsolutePath()+", bytes_written: "+len );
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
                return lastWriter;
            }
        } );            
    }

    @Override
    public synchronized boolean build() throws IOException 
    {
    	assertNotDisposed();
        // return build( new CompilationListener() );
    	long time = -System.currentTimeMillis();
    	try {
    		return build( new DebugCompilationListener(true) );
    	} finally {
    		time += System.currentTimeMillis();
    		System.out.println("Building project "+this.project.getName()+" took "+time+" ms");
    	}
    }
    
	/**
	 * Check whether a given compilation unit is part of the generated executable (compilation root).
	 * 
	 * @param unit
	 * @return
	 * @see ProjectConfiguration#getCompilationRoot()
	 */
	public boolean isPartOfExecutable(ICompilationUnit unit) {
		
		final List<ICompilationUnit> units = getCompilationUnitsForExecutable();
		for ( ICompilationUnit that : units ) {
			if ( that == unit || that.getResource().getIdentifier().equals( unit.getResource().getIdentifier() ) ) {
				return true;
			}
		}
		return false;
	}
	
	private List<DependencyNode> calculateRootSet() 
	{
        // compile stuff
        final List<ICompilationUnit> compilationUnits = getCompilationUnits();
        
        final IResourceMatcher relaxedResolver = new IResourceMatcher() {
            
            @Override
            public boolean isSame(IResource resource1,IResource resource2) 
            {
                return resource1.getIdentifier().equals( resource2.getIdentifier() );
            }
        };
        return analyzer.calculateRootSet( compilationUnits , project ,  relaxedResolver );
	}
	
	private List<ICompilationUnit> getCompilationUnitsForExecutable() 
	{
        final List<DependencyNode> rootSet = calculateRootSet();
        
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
        	return Collections.emptyList();
        }
        
        return analyzer.linearize( nodeToCompile );
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
            List<ICompilationUnit> units  = getCompilationUnits();
            if ( units.isEmpty() ) {
            	return true; // => return 'success' immediately
            }
            if ( units.size() > 1 ) 
            {
                File root =  getConfiguration().getCompilationRoot() ; 
                if ( root == null ) {
                    throw new IllegalArgumentException("Please set the compilation root on project "+project.getName());
                }
                for (Iterator<ICompilationUnit> it = units.iterator(); it.hasNext();) {
                    final ICompilationUnit unit = it.next();
                    if ( ! unit.getResource().getIdentifier().equals( root.getAbsolutePath() ) ) {
                        it.remove();
                    }
                }
                if ( units.isEmpty() ) {
                    throw new RuntimeException("Internal error, Failed to find resource for compilation root "+root.getAbsolutePath()+" in project "+project.getName());
                }
            }  
            
            buildSuccessful = build( units , listener );
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

    private boolean build(List<ICompilationUnit> compilationUnits,ICompilationListener listener) throws IOException 
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
        
        globalSymbolTable.clear();
        
        final ArrayList<ICompilationUnit> dependencies = new ArrayList<ICompilationUnit>();
//        for ( ICompilationUnit unit : getCompilationUnits() ) 
//        {
//            boolean alreadyContained = false;
//            for ( ICompilationUnit unit2 : compilationUnits ) {
//                if ( unit2.getResource().getIdentifier().equals( unit.getResource().getIdentifier() ) ) {
//                    alreadyContained=true;
//                    break;
//                }
//            }
//            if ( ! alreadyContained ) {
//                dependencies.add( unit );
//            }
//        }
  
        final DebugInfo debugInfo = compiler.compile( compilationUnits , dependencies , globalSymbolTable , listener , relaxedResolver );

        // create executable
        if ( isCompilationSuccessful( compilationUnits ) && ! objectFiles.isEmpty() )
        {
//            System.out.println("\n-------- DEBUG ------------\n"+debugInfo.dumpToString());
        	final List<CompiledCode> toLink = objectFiles.subList(0, 1 );
        	LOG.debug("[ "+this+"] Linking "+toLink);
        	executable = link( toLink , debugInfo , compiler.hasCompilerOption( CompilerOption.GENERATE_RELOCATION_INFORMATION ) );
        	workspace.resourceCreated( project , executable );
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

    private Executable link(List<CompiledCode> objectFiles,
            DebugInfo debugInfo,
            boolean generateRelocatableCode) throws IOException 
    {
        final File outputFolder = getConfiguration().getOutputFolder();
        final File outputFile = new File( outputFolder , getConfiguration().getExecutableName() );
        return new Linker().link( objectFiles , debugInfo , outputFile , generateRelocatableCode , true );
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
            if ( executable != null && executable.getIdentifier().equals( f.getAbsolutePath() ) ) 
            {
            	executable = null;
            	workspace.resourceDeleted( project , new FileResource( f , ResourceType.EXECUTABLE ) );            	
            } else {
            	workspace.resourceDeleted( project , new FileResource( f , ResourceType.UNKNOWN) );
            }
        }
        
        if ( executable != null ) {
        	Executable tmp = executable;
        	executable = null;
        	workspace.resourceDeleted( project , new FileResource( new File(tmp.getIdentifier()) , ResourceType.EXECUTABLE ) );    
        }
    }
    
    @Override
    public Executable getExecutable() 
    {
    	assertNotDisposed();
    	return executable;
    }
    
    public boolean isBuildRequired() 
    {
    	assertNotDisposed();

        for ( ICompilationUnit unit : getCompilationUnitsForExecutable()) 
        {
            if ( unit.getAST() == null || unit.hasErrors() ) {
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
                if ( resourceMatcher.isSame( existing.getResource() , resource ) ) 
                {
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
        maybeRemoveExecutable( resource );
    }
    
    private void maybeRemoveExecutable(IResource resource) {
    	if ( executable == null ) {
    		return;
    	}
    	if ( executable.refersTo( resource ) ) 
    	{
    		IResource tmp = executable;
    		executable = null;
    		workspace.resourceDeleted( project , tmp );
    	}
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
            maybeRemoveExecutable( resource );            
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