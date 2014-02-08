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

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.Linker;
import de.codesourcery.jasm16.compiler.io.ClassPathResource;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.exceptions.ProjectAlreadyExistsException;
import de.codesourcery.jasm16.ide.ui.viewcontainers.IViewContainer;
import de.codesourcery.jasm16.ide.ui.viewcontainers.IViewContainerListener;
import de.codesourcery.jasm16.ide.ui.viewcontainers.Perspective;
import de.codesourcery.jasm16.ide.ui.viewcontainers.ViewContainerManager;
import de.codesourcery.jasm16.ide.ui.views.WorkspaceExplorer;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Entry point (command-line executable) of the IDE application.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class IDEMain
{
	private static final Logger LOG = Logger.getLogger(IDEMain.class);

	private final ViewContainerManager viewContainerManager;
    private final IWorkspace workspace;
    
    public static void main(String[] args) throws IOException, ProjectAlreadyExistsException
    {
        new IDEMain();
    }
    
    public IDEMain() throws IOException, ProjectAlreadyExistsException
    {
        new Linker();
        
    	final File appConfigFile = new File( Misc.getUserHomeDirectory() , ApplicationConfig.FILE_NAME );
    	
		final IApplicationConfig appConfig = new ApplicationConfig( appConfigFile );
		
		workspace = new DefaultWorkspace( appConfig );
		
		final BuildManager buildManager = new BuildManager(workspace);
		workspace.addResourceListener( buildManager );
		
		workspace.open();
		
		// TODO: Remove creation of sample project
		createSampleProject( "sample-project" );

		viewContainerManager = new ViewContainerManager(workspace,appConfig);
		
		final Perspective desktop = new Perspective( "desktop" , viewContainerManager , appConfig );
		
		desktop.addViewContainerListener( new IViewContainerListener() {

			@Override
			public void viewContainerClosed(IViewContainer container) 
			{
				try 
				{
					workspace.close();
				} 
				catch (IOException e) 
				{
					e.printStackTrace();
				}
			}
		});
		
		EditorFactory editorFactory = new EditorFactory( workspace , viewContainerManager );
		desktop.addView( new WorkspaceExplorer( workspace , viewContainerManager , editorFactory ) );
    	desktop.setVisible( true );
    	
    	viewContainerManager.addViewContainer( desktop );
    }
    
    private void createSampleProject(String projectName) throws IOException, ProjectAlreadyExistsException
    {
    	if ( workspace.doesProjectExist( projectName ) ) 
    	{
    		return;
    	}
    	
    	if ( new File( workspace.getBaseDirectory() , projectName ).exists() ) {
    		return;
    	}
    	
    	final IAssemblyProject project = workspace.createNewProject( projectName );
    	
    	final List<File> sourceFolders = project.getConfiguration().getSourceFolders();
    	if ( sourceFolders.isEmpty() ) {
    		LOG.error("createNewProject(): Unable to create new project '"+projectName+"'");
    		throw new IOException("Unable to create new project '"+projectName+"'");
    	}
    	
    	final ClassPathResource r = new ClassPathResource("tetris.dasm16",ResourceType.SOURCE_CODE);
    	
    	final File sourceFile = new File( sourceFolders.get(0) , "sample.dasm16" );
        final IResource file = new FileResource( sourceFile ,ResourceType.SOURCE_CODE );
        
    	Misc.copyResource( r , file ); 
    
        workspace.resourceCreated( project , file );
    }    
}