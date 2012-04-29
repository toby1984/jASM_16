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

import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.CompilationUnit;
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
import de.codesourcery.jasm16.compiler.io.NullObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.SimpleFileObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.Misc.IFileVisitor;

/**
 * DCPU-16 assembly project.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class AssemblyProject implements IAssemblyProject , IResourceListener
{
	private static final Logger LOG = Logger.getLogger(AssemblyProject.class);

	private final ProjectConfiguration projectConfiguration;

	private final List<IResource> allResources = new ArrayList<IResource>();
	private final List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();

	private final IWorkspace workspace;
	
	private final IProjectBuilder builder = new IProjectBuilder() 
	{
		@Override
		public void build() throws IOException 
		{
			build( new CompilationListener() );
		}

		@Override
		public ICompilationUnit parse(IResource source, ICompilationListener listener) throws IOException 
		{
			final ICompiler compiler = createCompiler();
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
			final ICompiler compiler = new de.codesourcery.jasm16.compiler.Compiler();

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
					final IResource resource = new FileResource( outputFile , ResourceType.OBJECT_FILE );
					return new FileObjectCodeWriter( outputFile , false ) 
					{
						protected void closeHook() throws IOException 
						{
							if ( getCurrentWriteOffset().getValue() != 0 ) {
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
		public void build(ICompilationListener listener) throws IOException 
		{
			final ICompiler compiler = createCompiler();

			// set output code writer
			final List<IResource> objectFiles = new ArrayList<IResource>();

			setObjectCodeOutputFactory( compiler , objectFiles );

			// clean output directory
			clean();

			// compile stuff
			final List<ICompilationUnit> compilationUnits = getCompilationUnits();
			compiler.compile( compilationUnits , listener );

			// create executable
			if ( isCompilationSuccessful( compilationUnits ) )
			{
				final IResource executable = link( objectFiles );
				workspace.resourceCreated( AssemblyProject.this , executable );
			}
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

	public AssemblyProject(IWorkspace workspace , ProjectConfiguration config) throws IOException 
	{
		if (config == null) {
			throw new IllegalArgumentException("config must not be NULL");
		}
		if ( workspace == null ) {
			throw new IllegalArgumentException("workspace must not be NULL");
		}
		this.workspace = workspace;
		this.projectConfiguration = config;
		allResources.addAll( scanForResources() );
	}

	protected List<IResource> scanForResources() throws IOException {

		final Map<String,IResource> result = new HashMap<String,IResource> ();

		final IFileVisitor visitor = new IFileVisitor() 
		{
			@Override
			public boolean visit(File file) throws IOException 
			{
				if ( Misc.isSourceFile( file ) ) 
				{
					if ( ! result.containsKey( file.getAbsolutePath() ) ) 
					{
						result.put( file.getAbsolutePath() , new FileResource( file , ResourceType.SOURCE_CODE ) );
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
		return new ArrayList<IResource>( this.allResources );
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
		for ( IResource r : allResources ) {
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
	
	protected void handleResourceCreated(IResource resource) 
	{
		if (resource == null) {
			throw new IllegalArgumentException("resource must not be NULL");
		}
		for (Iterator<IResource> it = getAllResources().iterator(); it.hasNext();) 
		{
			final IResource existing = it.next();
			if ( existing.getIdentifier().equals( resource.getIdentifier() ) ) 
			{
				return; // already known
			}
		}

		allResources.add( resource );
		
		if ( resource.hasType( ResourceType.SOURCE_CODE ) ) {
			units.add( CompilationUnit.createInstance( resource.getIdentifier() , resource  ) );
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resourceCreated(IAssemblyProject project, IResource resource) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resourceDeleted(IAssemblyProject project, IResource resource) {
		// TODO Auto-generated method stub
		
	}
}