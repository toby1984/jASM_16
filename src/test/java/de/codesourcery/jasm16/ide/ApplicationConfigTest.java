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

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.io.IOException;

import de.codesourcery.jasm16.ide.ui.utils.SizeAndLocation;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.Misc;

public class ApplicationConfigTest extends TestHelper {

	private File file;
	
	@Override
	protected void tearDown() throws Exception {
		if ( file != null ) {
			file.delete();
			file = null;
		}
	}
	
	public void testLoad() throws IOException {
		
		final File workspaceDir = getTempDir();
		final String contents = ApplicationConfig.KEY_WORKSPACE_DIRECTORY+"="+workspaceDir.getAbsolutePath();
		
		file = new File( workspaceDir , ApplicationConfig.FILE_NAME );
		
		Misc.writeFile( file , contents );
		
		ApplicationConfig config = new ApplicationConfig( file );
		assertEquals( workspaceDir.getAbsolutePath() , config.getWorkspaceDirectory().getAbsolutePath() );
	}
	
	public void testSave() throws IOException {
		
		final File workspaceDir = getTempDir();
		final String contents = ApplicationConfig.KEY_WORKSPACE_DIRECTORY+"="+workspaceDir.getAbsolutePath();
		
		file = new File( workspaceDir , ApplicationConfig.FILE_NAME );
		
		Misc.writeFile( file , contents );
		
		ApplicationConfig config = new ApplicationConfig( file );
		assertEquals( workspaceDir.getAbsolutePath() , config.getWorkspaceDirectory().getAbsolutePath() );
		
		final File workspaceDir2 = getTempDir();
		config.setWorkspaceDirectory( workspaceDir2 );
		config.saveConfiguration();
		
		config = new ApplicationConfig( file );
		assertEquals( workspaceDir2.getAbsolutePath() , config.getWorkspaceDirectory().getAbsolutePath() );
	}	
	
	public void testLoadAndStore() throws IOException {
		
		final File workspaceDir = getTempDir();
		final String contents = ApplicationConfig.KEY_WORKSPACE_DIRECTORY+"="+workspaceDir.getAbsolutePath();
		
		file = new File( workspaceDir , ApplicationConfig.FILE_NAME );
		
		Misc.writeFile( file , contents );
		
		ApplicationConfig config = new ApplicationConfig( file );
		assertEquals( workspaceDir.getAbsolutePath() , config.getWorkspaceDirectory().getAbsolutePath() );
		
		SizeAndLocation sizeAndLoc1 = new SizeAndLocation( new Point(1,2) , new Dimension(2,3) );
		config.storeViewCoordinates( "test-view" , sizeAndLoc1 );

		SizeAndLocation sizeAndLoc2 = config.getViewCoordinates( "test-view" );
		assertNotNull( sizeAndLoc2 );
		assertEquals( sizeAndLoc1.getSize() , sizeAndLoc2.getSize() );
		assertEquals( sizeAndLoc1.getLocation() , sizeAndLoc2.getLocation() );
		
		final File workspaceDir2 = new File( getTempDir() , "dummy" );
		config.setWorkspaceDirectory( workspaceDir2 );
		config.saveConfiguration();
		
		config = new ApplicationConfig( file );
		assertEquals( workspaceDir2.getAbsolutePath() , config.getWorkspaceDirectory().getAbsolutePath() );
		
		SizeAndLocation sizeAndLoc3 = config.getViewCoordinates( "test-view" );
		assertNotNull( sizeAndLoc3 );
		assertEquals( sizeAndLoc1.getSize() , sizeAndLoc3.getSize() );
		assertEquals( sizeAndLoc1.getLocation() , sizeAndLoc3.getLocation() );	
		
		assertNull( config.getViewCoordinates( "test-view2" ) ); 
	}	
}
