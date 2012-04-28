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
import java.util.List;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.utils.Misc;


public class IDEMain
{
	private static final Logger LOG = Logger.getLogger(IDEMain.class);
	
	private MainFrame mainFrame;
    private IWorkspace workspace;
    
    public static void main(String[] args) throws IOException
    {
        new IDEMain().run();
    }
    
    private void run() throws IOException
    {
    	final File appConfigFile = new File( Misc.getUserHomeDirectory() , ApplicationConfig.FILE_NAME );
		final IApplicationConfig appConfig = new ApplicationConfig( appConfigFile );
		
		workspace = new DefaultWorkspace( appConfig );
		
		createSampleProject( "sample-project" );
		
    	mainFrame = new MainFrame( workspace );
    	mainFrame.setVisible( true );
    }
    
    private void createSampleProject(String projectName) throws IOException
    {
    	if ( workspace.doesProjectExist( projectName ) ) 
    	{
    		return;
    	}
    	
    	final IAssemblerProject project = workspace.createNewProject( projectName );
    	
    	final List<File> sourceFolders = project.getConfiguration().getSourceFolders();
    	if ( sourceFolders.isEmpty() ) {
    		LOG.error("createNewProject(): Unable to create new project '"+projectName+"'");
    		throw new IOException("Unable to create new project '"+projectName+"'");
    	}
    	
    	final File sourceFile = new File( sourceFolders.get(0) , "sample.dasm16" );

        final String source = ":label SET a,1\n"+
                "       ADD b ,1\n"+
                "       ADD [stuff],1\n"+
                "       SET c , [stuff]\n"+
                "       SET PC,label\n"+
                ":stuff .dat 0x000";
        
        Misc.writeFile( sourceFile , source );        
        
        final IResource file = new FileResource( sourceFile ,ResourceType.SOURCE_CODE );
        project.registerResource( file );
    }    
}