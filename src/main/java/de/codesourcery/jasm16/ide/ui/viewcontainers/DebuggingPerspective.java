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

import java.io.IOException;
import java.util.List;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.ide.EmulatorFactory;
import de.codesourcery.jasm16.ide.IApplicationConfig;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.IWorkspaceListener;
import de.codesourcery.jasm16.ide.WorkspaceListener;
import de.codesourcery.jasm16.ide.ui.views.BreakpointView;
import de.codesourcery.jasm16.ide.ui.views.CPUView;
import de.codesourcery.jasm16.ide.ui.views.DisassemblerView;
import de.codesourcery.jasm16.ide.ui.views.HexDumpView;
import de.codesourcery.jasm16.ide.ui.views.ScreenView;
import de.codesourcery.jasm16.ide.ui.views.SourceLevelDebugView;
import de.codesourcery.jasm16.ide.ui.views.StackView;
import de.codesourcery.jasm16.utils.Misc;

public class DebuggingPerspective extends Perspective
{
    private static final Logger LOG = Logger.getLogger(DebuggingPerspective.class);

    public static final String ID = "debugger";

    private final EmulatorFactory emulatorFactory;
    private final IWorkspace workspace;
    private final IEmulator emulator;

    private IAssemblyProject project;
    private IResource executable;

    private final IWorkspaceListener workspaceListener = new WorkspaceListener() {

        private volatile boolean buildRunning = false;

        public void projectDeleted(IAssemblyProject deletedProject) 
        {
            if ( deletedProject.isSame( project ) ) 
            {
                dispose();
            }
        }

        public void resourceDeleted(IAssemblyProject affectedProject, IResource deletedResource) {

            if ( ! buildRunning && // do not dispose the perspective if executable is deleted as part of the build process 
                    affectedProject.isSame( project ) && 
                    deletedResource.isSame( executable ) ) 
            {
                dispose();
            }
        }

        public void buildStarted(IAssemblyProject p) {
            if ( p.isSame( project ) ) {
                buildRunning = true;
            }
        }

        public void buildFinished(IAssemblyProject p , boolean success) 
        {
            if ( ! p.isSame( project ) ) 
            {
                return;
            }
            buildRunning = false;
            final IResource objectFile;
            if ( success ) 
            {
                final List<IResource> objectFiles = project.getResources( ResourceType.EXECUTABLE );
                if ( objectFiles.size() == 1 ) {
                    objectFile = objectFiles.get(0);
                } else if ( objectFiles.isEmpty() ) {
                    objectFile = null;
                } else {
                    throw new RuntimeException("Project "+p+" has more than one executable?");
                }
            } else {
                objectFile = null;
            }

            if ( objectFile != null ) 
            {
                try {
                    openExecutable( p , objectFile );
                } catch (IOException e) {
                    LOG.error("buildFinished()",e);
                }
            } else {
                dispose();
            }
        };


    };

    private final IEmulationListener listener = new EmulationListener() 
    {
        @Override
        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes)
        {
            setupPerspective();
        }

        @Override
        public void afterContinuousExecutionHook() {
        }
    };


    public DebuggingPerspective(EmulatorFactory emulatorFactory , IWorkspace workspace ,IApplicationConfig appConfig)
    {
        super(ID, appConfig);
        if ( workspace == null ) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        if ( emulatorFactory == null ) {
			throw new IllegalArgumentException("emulatorFactory must not be null");
		}
        this.emulatorFactory = emulatorFactory;
        this.workspace = workspace;
        this.workspace.addWorkspaceListener( workspaceListener );
        this.emulator = emulatorFactory.createEmulator();
        this.emulator.addEmulationListener( listener );
    }

    @Override
    public void dispose() 
    {
        try {
            workspace.removeWorkspaceListener( workspaceListener );
            super.dispose();
        } finally {
            this.emulator.dispose();
        }
    }

    public void openExecutable(IAssemblyProject project,IResource executable) throws IOException 
    {
        emulator.reset( true );

        // set project & executable BEFORE loading object code
        // so any IEmulationListener that implements #afterMemoryLoad() 
        // can query the DebuggingPerspective for the current project

        this.project = project;
        this.executable = executable;

        final byte[] objectCode = Misc.readBytes( executable );
        emulator.loadMemory( Address.wordAddress( 0 ) , objectCode ); // triggers IEmulationListener#afterMemoryLoad()
    }

    public IAssemblyProject getCurrentProject()
    {
        return project;
    }

    public void resetEmulator() 
    {
        try {
            openExecutable( this.project , this.executable );
        } 
        catch(Exception e) {
            LOG.error("resetEmulator(): ",e);
        }
    }

    private void setupPerspective() {

        // setup CPU view
        if ( getCPUView() == null ) {
            CPUView view = new CPUView( emulator );
            addView( view );
            view.refreshDisplay();
        }

        // setup disassembler view
        if ( getDisassemblerView() == null ) {
            DisassemblerView view = new DisassemblerView( this, emulator );
            addView( view );
            view.refreshDisplay();
        }

        // setup stack view
        if ( getStackView() == null ) {
            StackView view = new StackView( emulator );
            addView( view );
            view.refreshDisplay();
        }        

        // setup hex-dump view
        if ( getHexDumpView() == null ) {
            final HexDumpView view = new HexDumpView( emulator );
            addView( view );
            view.refreshDisplay();
        }           

        // setup screen view
        if ( getScreenView() == null ) {
            final ScreenView view = new ScreenView( emulatorFactory , emulator );
            addView( view );
            view.refreshDisplay();
        }     

        // setup source level debug view
        if ( getSourceLevelDebugView() == null ) {
            final SourceLevelDebugView view = new SourceLevelDebugView( workspace , this , emulator );
            addView( view );
            view.refreshDisplay();            
        }

        // setup screen view
        if ( getBreakpointView() == null ) {
            final BreakpointView view = new BreakpointView( getDisassemblerView() , getSourceLevelDebugView() , emulator );
            addView( view );
            view.refreshDisplay();
        }    
    }

    private SourceLevelDebugView getSourceLevelDebugView() {
        return (SourceLevelDebugView) getViewByID( SourceLevelDebugView.VIEW_ID );
    }      

    private BreakpointView getBreakpointView() {
        return (BreakpointView) getViewByID( BreakpointView.VIEW_ID );
    }  

    private ScreenView getScreenView() {
        return (ScreenView) getViewByID( ScreenView.VIEW_ID );
    }    

    private DisassemblerView getDisassemblerView() {
        return (DisassemblerView) getViewByID( DisassemblerView.VIEW_ID );
    }

    private StackView getStackView() {
        return (StackView) getViewByID( StackView.VIEW_ID );
    } 

    private HexDumpView getHexDumpView() {
        return (HexDumpView) getViewByID( HexDumpView.VIEW_ID );
    }     

    private CPUView getCPUView() {
        return (CPUView) getViewByID( CPUView.VIEW_ID );
    }      

    @Override
    public String getID()
    {
        return ID;
    }
}
