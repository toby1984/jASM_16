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
package de.codesourcery.jasm16.ide.ui.viewcontainers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.ide.EmulatorFactory;
import de.codesourcery.jasm16.ide.IApplicationConfig;
import de.codesourcery.jasm16.ide.IWorkspace;

/**
 * 
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ViewContainerManager implements IViewContainerListener
{
    // @GuardedBy( containers )
    private final List<IViewContainer> containers = new ArrayList<IViewContainer>();
    
    private final EmulatorFactory emulatorFactory;
    private final IWorkspace workspace;
    private final IApplicationConfig applicationConfig;
    
    public ViewContainerManager(EmulatorFactory emulatorFactory,IWorkspace workspace,IApplicationConfig applicationConfig) {
    	this.emulatorFactory = emulatorFactory;
    	this.workspace = workspace;
    	this.applicationConfig = applicationConfig;
    }
    
    public void addViewContainer(Perspective p) 
    {
        if (p == null) {
            throw new IllegalArgumentException("p must not be NULL.");
        }
        
        synchronized(containers) 
        {
            p.addViewContainerListener( this );
            containers.add( p );
        }
    }
    
    public void removeViewContainer(IViewContainer p) 
    {
        if (p == null) {
            throw new IllegalArgumentException("p must not be NULL.");
        }
        
        synchronized(containers) {
            p.removeViewContainerListener( this );
            containers.remove( p );
        }
    }    
    
    public List<IViewContainer> getPerspectives(String id) {
        
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("id must not be NULL/blank.");
        }
        
        final List<IViewContainer> result = new ArrayList<>();
        
        synchronized(containers) {
            for ( IViewContainer p : containers ) {
                if ( p.getID().equals( id ) ) {
                    result.add( p );
                }
            }
        }
        return result;
    }

    @Override
    public void viewContainerClosed(IViewContainer container)
    {
        removeViewContainer( container );
    }
    
    public DebuggingPerspective getOrCreateDebuggingPerspective() {

		final List<? extends IViewContainer> perspectives = 
				getPerspectives( DebuggingPerspective.ID );

		for ( IViewContainer existing : perspectives ) {
			if ( existing instanceof DebuggingPerspective) 
			{
				final DebuggingPerspective p = (DebuggingPerspective) existing;
				p.setVisible( true );
				p.toFront();
				return p;
			} 
		}

	    final List<IViewContainer> editorContainer = 
	    		getPerspectives( EditorContainer.VIEW_ID );
	      
		// perspective not visible yet, create it
		final DebuggingPerspective p=new DebuggingPerspective( emulatorFactory , 
				workspace , 
				applicationConfig , 
		        editorContainer.isEmpty() ? null : (EditorContainer) editorContainer.get(0) );
		addViewContainer( p );
		p.setVisible( true );
	    p.toFront();	
    	return p;
    }
}
