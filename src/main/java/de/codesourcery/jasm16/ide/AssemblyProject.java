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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.Compiler;
import de.codesourcery.jasm16.compiler.DefaultCompilationOrderProvider;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.FileObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.compiler.io.NullObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.SimpleFileObjectCodeWriterFactory;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
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

	private final Object RESOURCE_LOCK = new Object();

	// @GuardedBy( RESOURCE_LOCK )
	private final List<IResource> resources = new ArrayList<IResource>();
	private final List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
	private final IWorkspace workspace;
	private boolean isOpen;
	
	private final IProjectBuilder builder = new IProjectBuilder() 
	{
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

		protected ICompiler createCompiler() {
			final ICompiler compiler = new Compiler();

			// set compiler options
			compiler.setCompilerOption(CompilerOption.DEBUG_MODE , true );
			compiler.setCompilerOption(CompilerOption.RELAXED_PARSING , true );
			return compiler;
		}

		protected void setObjectCodeOutputFactory(ICompiler compiler,final List<IResource> objectFiles) {

			compiler.setObjectCodeWriterFactory( new SimpleFileObjectCodeWriterFactory() {

				protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context) 
				{
					final File outputFile = getOutputFileForSource( context.getCurrentCompilationUnit().getResource() );
					System.out.println("createObjectCodeWriter(): Compiling "+
					context.getCurrentCompilationUnit()+" to object file "+outputFile.getAbsolutePath());
					
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
								objectFiles.add( resource );
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
			return build( new CompilationListener() );
		}

		@Override
		public boolean build(ICompilationListener listener) throws IOException 
		{
			final ICompiler compiler = createCompiler();

			compiler.setCompilationOrderProvider( new DefaultCompilationOrderProvider() );
			
			// set output code writer
			final List<IResource> objectFiles = new ArrayList<IResource>();

			setObjectCodeOutputFactory( compiler , objectFiles );

			workspace.buildStarted( AssemblyProject.this );
			boolean buildSuccessful = false;
			try {
				// clean output directory
				clean();

				// compile stuff
				final List<ICompilationUnit> compilationUnits = getCompilationUnits();
				System.out.println("Compiling: "+compilationUnits);
				compiler.compile( compilationUnits , listener );

				// create executable
				if ( isCompilationSuccessful( compilationUnits ) )
				{
					final IResource executable = link( objectFiles );
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

		private IResource link(List<IResource> objectFiles) throws IOException 
		{
			final File outputFolder = getConfiguration().getOutputFolder();
			final File outputFile = new File( outputFolder , getConfiguration().getExecutableName() );
			final FileResource executable = new FileResource( outputFile , ResourceType.EXECUTABLE );

			final OutputStream out = executable.createOutputStream( true );
			try {
				for ( IResource r : objectFiles ) {
					final InputStream in = r.createInputStream();
					try {
						IOUtils.copy( in , out );
					} finally {
						IOUtils.closeQuietly( in );
					}
				}
			} finally {
				IOUtils.closeQuietly( out );
			}
			return executable;
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
			throw new RuntimeException("Internal error, more than one executable in project "+AssemblyProject.this);
		}

		@Override
		public ICompilationUnit getCompilationUnit(IResource source) 
		{
			if (source == null) {
				throw new IllegalArgumentException("source must not be NULL");
			}

			if ( ! source.hasType( ResourceType.SOURCE_CODE ) ) {
				throw new IllegalArgumentException("Not a source file: "+source);
			}

			for ( ICompilationUnit unit : getCompilationUnits() ) {
				if ( unit.getResource().getIdentifier().equals( source.getIdentifier() ) ) {
					return unit;
				}
			}
			throw new NoSuchElementException("Could not find compilation unit for "+source);
		}

		@Override
		public List<ICompilationUnit> getCompilationUnits()
		{
			return new ArrayList<ICompilationUnit>( units );
		}

	};

	protected File getOutputFileForSource(IResource resource) 
	{
		if ( ! resource.hasType(ResourceType.SOURCE_CODE ) ) {
			throw new IllegalArgumentException("Not a source file: "+resource);
		}
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
                    if ( existing.isSame( r ) ) {
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
                    if ( existingResource.isSame( newResource ) ) {
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

		// scan source folders
		final IFileVisitor visitor = new IFileVisitor() 
		{
			@Override
			public boolean visit(File file) throws IOException 
			{
				if ( Misc.isSourceFile( file ) ) 
				{
					if ( ! result.containsKey( file.getAbsolutePath() ) ) 
					{
						final FileResource resource = new FileResource( file , ResourceType.SOURCE_CODE );
						result.put( file.getAbsolutePath() , resource );
						units.add( CompilationUnit.createInstance( resource.getIdentifier() , resource  ) );
					}
				}
				return true;
			}
		};

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
	public IResource resolve(String identifier, ResourceType resourceType) throws ResourceNotFoundException 
	{
		return new FileResourceResolver( projectConfiguration.getBaseDirectory() ).resolve( identifier, resourceType );
	}

	@Override
	public IResource resolveRelative(String identifier, IResource parent, ResourceType resourceType) throws ResourceNotFoundException {
		return new FileResourceResolver( projectConfiguration.getBaseDirectory() ).resolveRelative( identifier ,parent, resourceType );
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

	private void removeCompilationUnitFor(IResource resource) 
	{
		for (Iterator<ICompilationUnit> it = units.iterator(); it.hasNext();) 
		{
			final ICompilationUnit existing = it.next();
			if ( existing.getResource().getIdentifier().equals( resource.getIdentifier() ) ) {
				it.remove();
				return;
			}
		}
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
				if ( resource.hasType( ResourceType.SOURCE_CODE ) ) {
					removeCompilationUnitFor( resource );
				}				
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
	public IProjectBuilder getBuilder() {
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

		// nothing to do yet...
	}

	@Override
	public void resourceCreated(IAssemblyProject project, IResource resource) 
	{
		if ( resource instanceof FileResource) 
		{
			if ( ((FileResource) resource).getFile().isDirectory() ) 
			{
				return; // we don't care about directories
			}

			synchronized( RESOURCE_LOCK ) 
			{
				for ( IResource r : getAllResources() ) {
					if ( r.isSame( resource ) ) 
					{
						resources.remove( r );
						removeCompilationUnitFor( r );

						resources.add( resource );
						if ( r.hasType( ResourceType.SOURCE_CODE ) ) {
							units.add( CompilationUnit.createInstance( r.getIdentifier() , r  ) );
						}    	                
						return;
					}
				}
				resources.add( resource );

				if ( resource.hasType( ResourceType.SOURCE_CODE ) ) 
				{
					System.out.println("New source-code resource added.");
					units.add( CompilationUnit.createInstance( resource.getIdentifier() , resource  ) );
				}       	        
			}
		}
	}

	@Override
	public void resourceDeleted(IAssemblyProject project, IResource resource) {

		synchronized( RESOURCE_LOCK ) 
		{
			for (Iterator<IResource> it = resources.iterator(); it.hasNext();) {
				IResource existing = it.next();
				if ( existing.isSame( resource ) ) 
				{
					it.remove();
					removeCompilationUnitFor( existing );
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
	public void projectDeleted(IAssemblyProject project) { /* sooo not interested */ }

	@Override
	public void buildStarted(IAssemblyProject project) { /* sooo not interested */ }

	@Override
	public void buildFinished(IAssemblyProject project, boolean success) { /* sooo not interested */ }
}