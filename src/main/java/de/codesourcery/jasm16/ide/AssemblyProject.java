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

import de.codesourcery.jasm16.compiler.io.DefaultResourceMatcher;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.emulator.EmulationOptions;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.Misc.IFileVisitor;

/**
 * DCPU-16 assembly project.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class AssemblyProject extends WorkspaceListener implements IAssemblyProject 
{
    private static final Logger LOG = Logger.getLogger(AssemblyProject.class);
    
    private static final IResourceMatcher resourceMatcher = new DefaultResourceMatcher();    

    private final ProjectConfiguration projectConfiguration;

    private final AtomicBoolean registeredWithWorkspace = new AtomicBoolean(false);
    
    private final Object RESOURCE_LOCK = new Object();
    
    private IProjectBuilder projectBuilder;

    // @GuardedBy( RESOURCE_LOCK )
    private final List<IResource> resources = new ArrayList<IResource>();
    
    private final IResourceResolver resolver;    
    
    private final IWorkspace workspace;
    private boolean isOpen;
    
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
        
        resolver = new FileResourceResolver( projectConfiguration.getBaseDirectory() ) 
    	{
    		@Override
    		protected ResourceType determineResourceType(File file) {
    			if ( getConfiguration().isSourceFile( file ) ) {
    			    return ResourceType.SOURCE_CODE;
    			}
    			return ResourceType.UNKNOWN;
    		}
    		
    		@Override
    		protected File getBaseDirectory() {
    			return projectConfiguration.getBaseDirectory();
    		}
    	};
    	
        synchronized( RESOURCE_LOCK ) { // unnecessary since we're inside this classes constructor but makes FindBugs & PMD happy
            resources.addAll( scanForResources() );
        }
    }
    
    public void setProjectBuilder(IProjectBuilder projectBuilder) {
    	if (projectBuilder == null) {
			throw new IllegalArgumentException("projectBuilder must not be null");
		}
    	if ( this.projectBuilder != null ) {
    		throw new IllegalStateException("Project builder already set on "+this);
    	}
		this.projectBuilder = projectBuilder;
	}
    
    public IProjectBuilder getProjectBuilder() {
		return projectBuilder;
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

    @Override
    public void reload() throws IOException
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
        return resolver.resolve( identifier );
    }
    
    @Override
    public IResourceResolver getResourceResolver() 
    {
    	return resolver;
    }

    @Override
    public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException {
        return resolver.resolveRelative( identifier ,parent );
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
	public void projectConfigurationChanged(IAssemblyProject project) {
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
    public boolean containsResource(IResource resource)
    {
        synchronized(RESOURCE_LOCK) {
            for ( IResource existing : resources ) 
            {
                if ( resourceMatcher.isSame( existing , resource ) ) {
                    return true;
                }
            }
        }
        return false;
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
        workspace.addResourceListener( this );
        for ( IResource r : getAllResources() ) {
        	workspace.resourceCreated( this , r );
        }
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
        workspace.removeResourceListener( this );
    }
}