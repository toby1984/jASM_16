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

public class ProjectConfigurationTest extends TestHelper {

	private File file;
	
	
	protected void tearDown() throws Exception {
		super.tearDown();
		if ( file != null ) {
//			file.delete();
			file = null;
		}
	}
	
	public void testCreateNewProjectConfiguration() throws IOException {
		
		file = new File( getTempDir() , "project1" );
		
		ProjectConfiguration config = new ProjectConfiguration( file );
		config.setProjectName("project1" );
		config.create();
		
        config = new ProjectConfiguration( file ); 
        
        config.load();
        
        assertEquals( "project1" ,config.getProjectName() );
        assertEquals( ProjectConfiguration.DEFAULT_EXECUTABLE_NAME , config.getExecutableName() );
        assertEquals( new File( file , "bin" ).getAbsolutePath() , config.getOutputFolder().getAbsolutePath() );
        
        assertEquals( 1 , config.getSourceFolders().size() );
        assertTrue( config.getSourceFolders().contains( new File( file , "src" ) ) );		
	}
	
	public void testLoadConfiguration() throws IOException 
	{
        final String xml = "<project>\n" + 
			               "  <name>myProject</name>\n" + 
			               "  <sourceFolders>\n" + 
			               "    <sourceFolder>src1</sourceFolder>\n" +
			               "    <sourceFolder>src2</sourceFolder>\n" + 			               
			               "  </sourceFolders>\n" + 
			               "  <outputFolder>bin</outputFolder>\n" + 
			               "  <executableName>test.exe</executableName>\n"+
			               "</project>";
        
        final File baseDir = getTempDir();
        file = new File( baseDir , ProjectConfiguration.PROJECT_CONFIG_FILE );
        
        Misc.writeFile( file  , xml );
        
        final ProjectConfiguration config = new ProjectConfiguration( baseDir ); 
        
        config.load();
        
        assertEquals( "myProject" ,config.getProjectName() );
        assertEquals("test.exe" , config.getExecutableName() );
        assertEquals( new File( baseDir , "bin" ).getAbsolutePath() , config.getOutputFolder().getAbsolutePath() );
        
        assertEquals( 2 , config.getSourceFolders().size() );
        assertTrue( config.getSourceFolders().contains( new File( baseDir , "src1" ) ) );
        assertTrue( config.getSourceFolders().contains( new File( baseDir , "src2" ) ) );
	}
	
	public void testSaveConfiguration() throws IOException 
	{
        final File baseDir = getTempDir();
        file = new File( baseDir , ProjectConfiguration.PROJECT_CONFIG_FILE );
        if ( file.exists() ) {
        	file.delete();
        }
        assertFalse( file.exists() );
        
        final ProjectConfiguration toSave = new ProjectConfiguration( baseDir );
        toSave.setOutputFolder( new File("/test/bin" ) );
        toSave.setProjectName( "myProject" );
        toSave.addSourceFolder( new File("/blubb/src1" ) );
        toSave.addSourceFolder( new File("/src2" ) );
        toSave.setExecutableName("a.out");

        toSave.save();
        
        assertTrue( file.exists() );
        final ProjectConfiguration config = new ProjectConfiguration( baseDir );
        config.load();
        
        assertEquals( "myProject" ,config.getProjectName() );
        assertEquals( new File( baseDir , "bin" ).getAbsolutePath() , config.getOutputFolder().getAbsolutePath() );
        
        assertEquals( 2 , config.getSourceFolders().size() );
        assertTrue( config.getSourceFolders().contains( new File( baseDir , "src1" ) ) );
        assertTrue( config.getSourceFolders().contains( new File( baseDir , "src2" ) ) );        
	}
}
