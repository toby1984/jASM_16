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
import java.util.List;
import java.util.NoSuchElementException;

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.ide.exceptions.ProjectAlreadyExistsException;
import de.codesourcery.jasm16.ide.ui.utils.SizeAndLocation;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.Misc;

public class DefaultWorkspaceTest extends TestHelper {

	private File workspaceDir;
	
	@Override
	protected void tearDown() throws Exception 
	{
		if ( workspaceDir != null ) {
			Misc.deleteRecursively(  workspaceDir );
			workspaceDir = null;
		}
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		final File tempDir = getTempDir();
		workspaceDir = new File(tempDir,"workspace");
		workspaceDir.mkdirs();		
	}
	
	public void testLoadWorkspaceWithOneProject() throws IOException, ProjectAlreadyExistsException {
		
		final IApplicationConfig config = new IApplicationConfig() {
			@Override
			public void setWorkspaceDirectory(File dir) throws IOException { }
			@Override
			public void saveConfiguration() { }
			@Override
			public File getWorkspaceDirectory() {
				return workspaceDir;
			}
			@Override
			public void storeViewCoordinates(String viewID, SizeAndLocation loc) {
			}
			@Override
			public SizeAndLocation getViewCoordinates(String viewId) {
				return null;
			}
		};

		DefaultWorkspace workspace = new DefaultWorkspace( config );
		assertEquals( workspaceDir , workspace.getBaseDirectory() );
		
		workspace.open();
		workspace.createNewProject("project1");
		workspace.close();
		
		workspace = new DefaultWorkspace( config );
		workspace.open();
		
		final List<IAssemblyProject> projects = workspace.getAllProjects();
		assertEquals( 1 , projects.size() );
		
		assertTrue( workspace.doesProjectExist( "project1" ) );
		assertFalse( workspace.doesProjectExist( "project2" ) );
		
		final IAssemblyProject project = projects.get(0);
		assertEquals( "project1" , project.getName() );
		
		final List<ICompilationUnit> units = workspace.getBuildManager().getProjectBuilder( project ).getCompilationUnits();
		assertEquals( 0 , units.size() );
		
		assertSame( project , workspace.getProjectByName("project1" ) );
		
		try {
			workspace.getProjectByName("project2" );
			fail("Should've failed");
		} catch(NoSuchElementException e) {
			// ok
		}
	}
	
	public void testLoadWorkspaceWithTwoProjects() throws IOException, ProjectAlreadyExistsException {
		
		final IApplicationConfig config = new IApplicationConfig() {
			@Override
			public void setWorkspaceDirectory(File dir) throws IOException { }
			@Override
			public void saveConfiguration() { }
			@Override
			public File getWorkspaceDirectory() {
				return workspaceDir;
			}
			@Override
			public void storeViewCoordinates(String viewID, SizeAndLocation loc) { }
			
			@Override
			public SizeAndLocation getViewCoordinates(String viewId) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		DefaultWorkspace workspace = new DefaultWorkspace( config );
		assertEquals( workspaceDir , workspace.getBaseDirectory() );
		
		workspace.open();
		workspace.createNewProject("project1");
		workspace.createNewProject("project2");
		workspace.close();		
		
		workspace = new DefaultWorkspace( config );
		workspace.open();
		
		final List<IAssemblyProject> projects = workspace.getAllProjects();
		assertEquals( 2 , projects.size() );
		
		assertTrue( workspace.doesProjectExist( "project1" ) );
		assertTrue( workspace.doesProjectExist( "project2" ) );

		IAssemblyProject project1 = projects.get(0);
		IAssemblyProject project2 = projects.get(1);
		
		if ( "project1".equals( project2.getName() ) ) {
			final IAssemblyProject tmp = project1;
			project1 = project2;
			project2 = tmp;
		}
		
		assertEquals( "project1" , project1.getName() );
		final List<ICompilationUnit> units = workspace.getBuildManager().getProjectBuilder( project1 ).getCompilationUnits();
		assertEquals( 0 , units.size() );
		assertSame( project1 , workspace.getProjectByName("project1" ) );
		
		assertEquals( "project2" , project2.getName() );
		final List<ICompilationUnit> units2 = workspace.getBuildManager().getProjectBuilder( project1 ).getCompilationUnits();
		assertEquals( 0 , units2.size() );
		assertSame( project2 , workspace.getProjectByName("project2" ) );		
	}
	
	public void testDeleteProject() throws IOException, ProjectAlreadyExistsException {
		
		final IApplicationConfig config = new IApplicationConfig() {
			@Override
			public void setWorkspaceDirectory(File dir) throws IOException { }
			@Override
			public void saveConfiguration() { }
			@Override
			public File getWorkspaceDirectory() {
				return workspaceDir;
			}
			
			@Override
			public void storeViewCoordinates(String viewID, SizeAndLocation loc) { }
			
			@Override
			public SizeAndLocation getViewCoordinates(String viewId) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		DefaultWorkspace workspace = new DefaultWorkspace( config );

		workspace.open();
		workspace.createNewProject("project1");
		workspace.close();
		
		assertEquals( workspaceDir , workspace.getBaseDirectory() );
		
		workspace = new DefaultWorkspace( config );
		workspace.open();
		
		final List<IAssemblyProject> projects = workspace.getAllProjects();
		assertEquals( 1 , projects.size() );
		assertTrue( workspace.doesProjectExist( "project1" ) );

		final IAssemblyProject project = projects.get(0);
		assertEquals( "project1" , project.getName() );
		final List<ICompilationUnit> units = workspace.getBuildManager().getProjectBuilder( project ).getCompilationUnits();
		assertEquals( 0 , units.size() );
		assertSame( project , workspace.getProjectByName("project1" ) );
		
		workspace.deleteProject( project , false );
		
		assertEquals( 0 , workspace.getAllProjects().size() );
		assertFalse( workspace.doesProjectExist( "project1" ) );
		
		assertTrue( new File( workspaceDir, "project1" ).isDirectory() );
		workspace = new DefaultWorkspace( config );
		workspace.open();		
		assertEquals( 0 , workspace.getAllProjects().size() );		
	}	
	
	public void testDeleteProjectPhysically() throws IOException, ProjectAlreadyExistsException {
		
		final IApplicationConfig config = new IApplicationConfig() {
			@Override
			public void setWorkspaceDirectory(File dir) throws IOException { }
			@Override
			public void saveConfiguration() { }
			@Override
			public File getWorkspaceDirectory() {
				return workspaceDir;
			}
			
			@Override
			public void storeViewCoordinates(String viewID, SizeAndLocation loc) { }
			
			@Override
			public SizeAndLocation getViewCoordinates(String viewId) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		DefaultWorkspace workspace = new DefaultWorkspace( config );

		workspace.open();
		workspace.createNewProject("project1");
		workspace.close();
		
		assertEquals( workspaceDir , workspace.getBaseDirectory() );
		
		workspace = new DefaultWorkspace( config );
		workspace.open();
		
		final List<IAssemblyProject> projects = workspace.getAllProjects();
		assertEquals( 1 , projects.size() );
		assertTrue( workspace.doesProjectExist( "project1" ) );

		final IAssemblyProject project = projects.get(0);
		assertEquals( "project1" , project.getName() );
		final List<ICompilationUnit> units = workspace.getBuildManager().getProjectBuilder( project ).getCompilationUnits();
		assertEquals( 0 , units.size() );
		assertSame( project , workspace.getProjectByName("project1" ) );
		
		workspace.deleteProject( project , true );
		
		assertEquals( 0 , workspace.getAllProjects().size() );
		assertFalse( workspace.doesProjectExist( "project1" ) );
		
		assertFalse( new File( workspaceDir, "project1" ).exists() );
		workspace = new DefaultWorkspace( config );
		workspace.open();		
		assertEquals( 0 , workspace.getAllProjects().size() );		
	}	
}
