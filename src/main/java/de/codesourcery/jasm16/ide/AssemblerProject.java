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

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.SimpleFileObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.utils.Misc;

public class AssemblerProject implements IAssemblerProject
{
	private static final Logger LOG = Logger.getLogger(AssemblerProject.class);
	
    private final ProjectConfiguration projectConfiguration;
    
    private final List<IResource> allResources = new ArrayList<IResource>();
    private final List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
    
    public AssemblerProject(ProjectConfiguration config) throws IOException 
    {
    	if (config == null) {
			throw new IllegalArgumentException("config must not be NULL");
		}
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
    			visit( srcFolder , visitor );
    		} else {
    			LOG.warn("scanForResources(): Missing source folder: "+srcFolder.getAbsolutePath());
    		}
    	}
    	return new ArrayList<IResource>( result.values() );
    }
    
    private boolean visit(File currentDir,IFileVisitor visitor) throws IOException {
    
    	final boolean cont = visitor.visit( currentDir );
    	if ( ! cont ) {
    		return false;
    	}
    	
    	for ( File f : currentDir.listFiles() ) 
    	{
    		if ( ! visit( f , visitor ) ) {
    			return false;
    		}
    	}
    	return true;
    }
    
    private interface IFileVisitor 
    {
    	public boolean visit(File file) throws IOException;
    }
    
    @Override
    public String getName()
    {
        return projectConfiguration.getProjectName();
    }
    
    @Override
    public List<ICompilationUnit> getCompilationUnits()
    {
        return new ArrayList<ICompilationUnit>( units );
    }
    
    @Override
    public void registerResource(IResource resource)
    {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be NULL.");
        }
        for ( IResource existing : allResources ) {
            if ( existing.getIdentifier().equals( resource.getIdentifier() ) ) {
                throw new IllegalArgumentException("Resource "+resource+" already registered.");
            }
        }
        allResources.add( resource );
        if ( resource.hasType( ResourceType.SOURCE_CODE ) ) {
        	units.add( CompilationUnit.createInstance( resource.getIdentifier() , resource ) );
        }
    }

    @Override
    public void unregisterResource(IResource resource)
    {
        for (Iterator<IResource> it = allResources.iterator(); it.hasNext();) {
            final IResource existing = it.next();
            if ( existing.getIdentifier().equals( resource.getIdentifier() ) ) {
                it.remove();
                if ( resource.hasType( ResourceType.SOURCE_CODE ) ) {
                	removeCompilationUnitFor( resource );
                }
                return;
            }
        }        
    }
    
    private void removeCompilationUnitFor(IResource r) 
    {
        for (Iterator<ICompilationUnit> it = units.iterator(); it.hasNext();) {
            final ICompilationUnit existing = it.next();
            if ( existing.getResource().getIdentifier().equals( r.getIdentifier() ) ) 
            {
                it.remove();
                return;
            }
        }      	
    }

    @Override
    public List<IResource> getAllResources()
    {
        return new ArrayList<IResource>( this.allResources );
    }

	@Override
	public IObjectCodeWriterFactory getObjectCodeWriterFactory() 
	{
		final File outputFile = new File( projectConfiguration.getOutputFolder() , "a.out" );
		return new SimpleFileObjectCodeWriterFactory( outputFile , false );
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
	public List<IResource> getResources(ResourceType type) {
		
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

}
