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

import static de.codesourcery.jasm16.compiler.ICompiler.CompilerOption.GENERATE_RELOCATION_INFORMATION;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import de.codesourcery.jasm16.compiler.DefaultCompilationOrderProvider;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnitResolver;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.Linker;
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
import de.codesourcery.jasm16.emulator.EmulationOptions;
import de.codesourcery.jasm16.exceptions.AmbigousCompilationOrderException;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.utils.DebugCompilationListener;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.Misc.IFileVisitor;

/**
 * DCPU-16 assembly project.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class AssemblyProject implements IAssemblyProject 
{
    private static final Logger LOG = Logger.getLogger(AssemblyProject.class);

    private final ProjectConfiguration projectConfiguration;

    private final AtomicBoolean registeredWithWorkspace = new AtomicBoolean(false);
    
    private final Object RESOURCE_LOCK = new Object();

    // @GuardedBy( RESOURCE_LOCK )
    private final List<IResource> resources = new ArrayList<IResource>();
    
    private final IWorkspace workspace;
    private boolean isOpen;
    
    private final MyProjectBuilder builder = new MyProjectBuilder();

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

    public AssemblyProject(IWorkspace workspace , ProjectConfiguration config,boolean isOpen) throws IOException 
    {
        if (config == null) {
            throw new IllegalArgumentException("config must not be NULL");
        }
        if ( workspace == null ) {
            throw new IllegalArgumentException("workspace must not be NULL");
        }
        this.isOpen = isOpen;
        this.workspace = workspace;
        this.projectConfiguration = config;
        synchronized( RESOURCE_LOCK ) { // unnecessary since we're inside this classes constructor but makes FindBugs & PMD happy
            resources.addAll( scanForResources() );
        }
    }
    
    private static final IResourceMatcher resourceMatcher = new DefaultResourceMatcher();

    @Override
    public void rescanResources() throws IOException
    {
        final List<IResource> deletedResources=new ArrayList<IResource>();   
        final List<IResource> newResources= scanForResources();
        synchronized( RESOURCE_LOCK ) { // unnecessary since we're inside this classes constructor but makes FindBugs & PMD happy

            // find deleted resources
            outer:            
                for ( IResource existing : resources )
                {
                    for ( IResource r : newResources ) 
                    {
                        if ( resourceMatcher.isSame( existing , r ) ) {
                            continue outer;
                        }
                    }
                    deletedResources.add( existing );
                }      

        // remove existing (=unchanged) resources
        for ( Iterator<IResource> it=newResources.iterator() ; it.hasNext() ; ) 
        {
            final IResource newResource = it.next();
            for ( IResource existingResource : resources ) {
                if ( resourceMatcher.isSame( existingResource,newResource ) ) {
                    it.remove();
                    break;
                }
            }
        }        
        }

        for ( IResource deleted : deletedResources ) {
            workspace.resourceDeleted( this , deleted );
        }

        for ( IResource added : newResources ) {
            workspace.resourceCreated( this  , added );
        }
    }

    protected List<IResource> scanForResources() throws IOException {

        final Map<String,IResource> result = new HashMap<String,IResource> ();

        // scan files
        final IFileVisitor visitor = new IFileVisitor() 
        {
            private final ProjectConfiguration projConfig = getConfiguration();
                    
            @Override
            public boolean visit(File file) throws IOException 
            {
                if ( ! result.containsKey( file.getAbsolutePath() ) ) 
                {                
                    final ResourceType type;
                    // note: if clauses sorted by probability, most likely comes first
                    if ( projConfig.isSourceFile( file ) ) 
                    {
                        type = ResourceType.SOURCE_CODE;
                    } 
                    else if ( ! ProjectConfiguration.isProjectConfigurationFile( file ) ) 
                    {
                        type = ResourceType.UNKNOWN;                        
                    } else {
                        type = ResourceType.PROJECT_CONFIGURATION_FILE;
                    }
                    final FileResource resource = new FileResource( file , type);
                    result.put( file.getAbsolutePath() , resource );
                }
                return true;
            }
        };
        
        for ( File f : projectConfiguration.getBaseDirectory().listFiles() ) {
        	if ( ! visitor.visit( f ) ) {
        		break;
        	}
        }

        for ( File srcFolder : projectConfiguration.getSourceFolders() ) 
        {
            if ( srcFolder.exists() ) {
                Misc.visitDirectoryTreePostOrder( srcFolder , visitor );
            } else {
                LOG.warn("scanForResources(): Missing source folder: "+srcFolder.getAbsolutePath());
            }
        }

        // scan binary output folder
        final File outputFolder = projectConfiguration.getOutputFolder();
        if ( outputFolder.exists() ) 
        {
            final IFileVisitor executableVisitor = new IFileVisitor() {

                @Override
                public boolean visit(File file) throws IOException
                {
                    if ( file.isFile() ) 
                    {
                        if ( file.getName().equals( projectConfiguration.getExecutableName() ) ) {
                            result.put( file.getAbsolutePath() , new FileResource( file , ResourceType.EXECUTABLE ) );
                        } else {
                            result.put( file.getAbsolutePath() , new FileResource( file , ResourceType.OBJECT_FILE ) );                            
                        }
                    }
                    return true;
                }
            };
            Misc.visitDirectoryTreeInOrder( outputFolder , executableVisitor );
        }
        return new ArrayList<IResource>( result.values() );
    }

    @Override
    public String getName()
    {
        return projectConfiguration.getProjectName();
    }

    @Override
    public List<IResource> getAllResources()
    {
        synchronized( RESOURCE_LOCK ) {
            return new ArrayList<IResource>( this.resources );
        }
    }

    @Override
    public IResource resolve(String identifier) throws ResourceNotFoundException 
    {
        return new FileResourceResolver( projectConfiguration.getBaseDirectory() ).resolve( identifier );
    }

    @Override
    public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException {
        return new FileResourceResolver( projectConfiguration.getBaseDirectory() ).resolveRelative( identifier ,parent );
    }

    @Override
    public ProjectConfiguration getConfiguration() {
        return projectConfiguration;
    }

    @Override
    public List<IResource> getResources(ResourceType type) 
    {
        if (type == null) {
            throw new IllegalArgumentException("type must not be NULL");
        }

        final List<IResource> result = new ArrayList<IResource>();
        for ( IResource r : getAllResources() ) {
            if ( r.hasType( type ) ) {
                result.add( r );
            }
        }
        return result;
    }

    protected void handleResourceDeleted(IResource resource) 
    {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be NULL");
        }

        for (Iterator<IResource> it = getAllResources().iterator(); it.hasNext();) 
        {
            final IResource existing = it.next();
            if ( existing.getIdentifier().equals( resource.getIdentifier() ) ) 
            {
                it.remove();
                break;
            }
        }
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
            workspace.resourceDeleted( this , new FileResource( f , ResourceType.UNKNOWN) );
        }
    }

    @Override
    public IProjectBuilder getProjectBuilder() {
        return builder;
    }

    @Override
    public IResource lookupResource(String identifier) 
    {
        for ( IResource r : getAllResources() ) {
            if ( r.getIdentifier().equals( identifier ) ) {
                return r;
            }
        }
        throw new NoSuchElementException("Unable to find resource '"+identifier+" in project "+this);
    }

    @Override
    public void resourceChanged(IAssemblyProject project, IResource resource) {

        if ( this != project) {
            return;
        }
        
        IResource found=null;
        synchronized( RESOURCE_LOCK ) 
        {
            for ( IResource r : getAllResources() ) 
            {
                if ( resourceMatcher.isSame( r , resource ) ) {
                    found = r;
                    break;
                }
            }
        }
        if ( found == null ) {
            return;
        }

        builder.resourceChanged(project,resource);
    }

    @Override
    public void resourceCreated(IAssemblyProject project, IResource resource) 
    {
        if ( this != project) {
            return;
        }

        if ( resource instanceof FileResource) 
        {
            if ( ((FileResource) resource).getFile().isDirectory() ) 
            {
                return; // we don't care about directories
            }

            synchronized( RESOURCE_LOCK ) 
            {
                for ( IResource r : getAllResources() ) {
                    if ( resourceMatcher.isSame( r, resource ) ) // resource update
                    {
                        resources.remove( r );
                        resources.add( resource );
                        return;
                    }
                }

                if ( resource.hasType( ResourceType.EXECUTABLE ) ) {
                    synchronized( RESOURCE_LOCK ) 
                    {
                        for ( IResource r : getAllResources() ) {
                            if ( r.hasType( ResourceType.EXECUTABLE ) ) {
                                throw new IllegalArgumentException("Cannot add executable "+resource+" to project "+this+" , already has executable "+r);
                            }
                        }
                    }
                }

                resources.add( resource );
            }
            builder.resourceCreated(project,resource);
        }
    }

    @Override
    public void resourceDeleted(IAssemblyProject project, IResource resource) {

        if ( this != project) {
            return;
        }
        
        synchronized( RESOURCE_LOCK ) 
        {
            for (Iterator<IResource> it = resources.iterator(); it.hasNext();) {
                IResource existing = it.next();
                if ( resourceMatcher.isSame( existing,resource ) ) 
                {
                    it.remove();
                    return;
                }
            }
        }	    
        builder.resourceDeleted(project,resource);        
    }

    @Override
    public IResource getResourceForFile(File file)
    {
        for ( IResource r : getAllResources() ) {
            if ( r instanceof FileResource) {
                if ( ((FileResource) r).getFile().getAbsolutePath().equals( file.getAbsolutePath() ) ) {
                    return r;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isSame(IAssemblyProject other) 
    {
        if ( other == this ) {
            return true;
        }
        if ( other == null ) {
            return false;
        }    	
        if ( this.getName().equals( other.getName() ) ) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public boolean isClosed() {
        return !isOpen;
    }

    @Override
    public void projectCreated(IAssemblyProject project) { /* sooo not interested */ }

    @Override
    public void projectClosed(IAssemblyProject project) {
        if ( project == this ) {
            this.isOpen = false;
        }		
    }

    @Override
    public void projectOpened(IAssemblyProject project) 
    {
        if ( project == this ) {
            this.isOpen = true;
        }
    }

    @Override
    public void projectDeleted(IAssemblyProject project) { /* sooo not interested */ }
    
    @Override
    public void projectDisposed(IAssemblyProject project)
    {
    }

    @Override
    public void buildStarted(IAssemblyProject project) { /* sooo not interested */ }

    @Override
    public void buildFinished(IAssemblyProject project, boolean success) { /* sooo not interested */ }

    @Override
    public String toString()
    {
        return getConfiguration().getProjectName();
    }

	@Override
	public EmulationOptions getEmulationOptions() {
		return getConfiguration().getEmulationOptions();
	}

	@Override
	public void setEmulationOptions(EmulationOptions emulationOptions) {
		getConfiguration().setEmulationOptions( emulationOptions );
	}

    @Override
    public void changeResourceType(IResource resource, ResourceType newType)
    {
        if ( resource.getType() == newType ) {
            return;
        }
        resource.setType( newType );
        workspace.resourceChanged( this , resource );
    }

    @Override
    public boolean containsResource(IResource resource)
    {
        synchronized(RESOURCE_LOCK) {
            for ( IResource existing : resources ) {
                if ( resourceMatcher.isSame( existing , resource ) ) {
                    return true;
                }
            }
        }
        return false;
    }
    
    protected class MyProjectBuilder implements IProjectBuilder , IResourceListener {

        // @GuardedBy( compUnits )
        private final List<ICompilationUnit> compUnits = new ArrayList<ICompilationUnit>();
        
        @Override
        public ICompilationUnit parse(IResource source, IResourceResolver resolver , ICompilationListener listener) throws IOException 
        {
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
                            throw new AmbigousCompilationOrderException( "Unable to determine compilation order - configuration of project "+getName()+" has non-existant compilation root '"+compRoot.getAbsolutePath()+"'",e.getRootSet());
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
                
                @Override
                public void changeResourceType(IResource resource, ResourceType newType)
                {
                    if ( containsResource( resource ) ) {
                        resource.setType( newType );
                        workspace.resourceChanged( AssemblyProject.this , resource );
                    } else {
                        delegate.changeResourceType(resource,newType);                        
                    }
                }
            });
            return compiler;
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
                                workspace.resourceCreated( AssemblyProject.this , resource );
                            }
                        }

                        protected void deleteOutputHook() throws IOException 
                        {
                            workspace.resourceCreated( AssemblyProject.this , resource );
                        };
                    };
                }
            } );            
        }

        @Override
        public boolean build() throws IOException 
        {
            return build( new DebugCompilationListener(true) );
        }

        @Override
        public boolean build(ICompilationListener listener) throws IOException 
        {
            final ICompiler compiler = createCompiler();

            /* Set output code writer.
             * 
             * The following array list will be populated by the ObjectCodeOutputFactory
             * with all generated object files.
             */
            final List<CompiledCode> objectFiles = new ArrayList<>();
            setObjectCodeOutputFactory( compiler , objectFiles );

            workspace.buildStarted( AssemblyProject.this );
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
                    workspace.resourceCreated( AssemblyProject.this , executable );
                    buildSuccessful = true; 
                } else {
                    buildSuccessful = false;
                }
            } finally {
                workspace.buildFinished( AssemblyProject.this , buildSuccessful );
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
            cleanOutputFolder();
        }

        @Override
        public IResource getExecutable() 
        {
            List<IResource> results = getResources( ResourceType.EXECUTABLE );
            if ( results.size() == 1 ) {
                return results.get(0);
            } else if ( results.isEmpty() ) {
                return null;
            }
            throw new RuntimeException("Internal error, more than one executable in project "+AssemblyProject.this+": "+results);
        }
        
        public boolean isBuildRequired() 
        {
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

        public void createAllCompilationUnits() 
        {
            for ( IResource r : getAllResources() ) {
                resourceCreated( AssemblyProject.this , r );
            }
        }
        
        public void deleteAllCompilationUnits() 
        {
            for ( IResource r : getAllResources() ) {
                resourceCreated( AssemblyProject.this , r );
            }
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
            if ( project != AssemblyProject.this) {
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
            if ( project != AssemblyProject.this) {
                return;
            }
            removeCompilationUnit( resource );
        }

        @Override
        public void resourceChanged(IAssemblyProject project, IResource resource)
        {
            if ( project != AssemblyProject.this) {
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
    }

    @Override
    public void addedToWorkspace(IWorkspace workspace)
    {
        if ( workspace != this.workspace ) {
            throw new IllegalStateException("Project "+this+" attached to different workspace?");
        }
        if ( ! registeredWithWorkspace.compareAndSet(false,true) ) 
        {
            throw new IllegalStateException("addedToWorkspace() called on already registered project "+this);
        }
        builder.createAllCompilationUnits();
        workspace.addResourceListener( this );
    }

    @Override
    public void removedFromWorkspace(IWorkspace workspace)
    {
        if ( workspace != this.workspace ) {
            throw new IllegalStateException("Project "+this+" attached to different workspace?");
        }       
        if ( ! registeredWithWorkspace.compareAndSet(true,false) ) 
        {
            throw new IllegalStateException("removedFromWorkspace() called on detached project "+this);
        }
        builder.deleteAllCompilationUnits();
        workspace.removeResourceListener( this );
    }
}