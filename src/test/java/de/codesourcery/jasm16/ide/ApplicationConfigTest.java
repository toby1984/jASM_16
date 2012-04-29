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
		
		final File workspaceDir = new File("/home/tobi");
		final String contents = ApplicationConfig.KEY_WORKSPACE_DIRECTORY+"="+workspaceDir.getAbsolutePath();
		
		file = new File( workspaceDir , ApplicationConfig.FILE_NAME );
		
		Misc.writeFile( file , contents );
		
		ApplicationConfig config = new ApplicationConfig( file );
		assertEquals( workspaceDir.getAbsolutePath() , config.getWorkspaceDirectory().getAbsolutePath() );
	}
	
	public void testSave() throws IOException {
		
		final File workspaceDir = new File("/home/tobi");
		final String contents = ApplicationConfig.KEY_WORKSPACE_DIRECTORY+"="+workspaceDir.getAbsolutePath();
		
		file = new File( workspaceDir , ApplicationConfig.FILE_NAME );
		
		Misc.writeFile( file , contents );
		
		ApplicationConfig config = new ApplicationConfig( file );
		assertEquals( workspaceDir.getAbsolutePath() , config.getWorkspaceDirectory().getAbsolutePath() );
		
		final File workspaceDir2 = new File("/home/tobi");
		config.setWorkspaceDirectory( workspaceDir2 );
		config.saveConfiguration();
		
		config = new ApplicationConfig( file );
		assertEquals( workspaceDir2.getAbsolutePath() , config.getWorkspaceDirectory().getAbsolutePath() );
				
		
	}	
}
