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
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.io.*;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.ide.*;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.views.*;
import de.codesourcery.jasm16.utils.Misc;

public class DebuggingPerspective extends Perspective
{
    private static final Logger LOG = Logger.getLogger(DebuggingPerspective.class);

    public static final String VIEW_ID = "debugger";

    private final ProjectWrapper resourceResolver=new ProjectWrapper();
    private final IWorkspace workspace;

    private final EmulatorProxy proxy = new EmulatorProxy();

    private volatile IAssemblyProject project;
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

        public void projectClosed(IAssemblyProject closedProject) {
            if ( closedProject.isSame( project ) ) 
            {
                dispose();
            }
        }

        public void resourceDeleted(IAssemblyProject affectedProject, IResource deletedResource) {

            if ( ! buildRunning && // do not dispose the perspective if executable is deleted as part of the build process 
                    affectedProject.isSame( project ) && 
                    DefaultResourceMatcher.INSTANCE.isSame( deletedResource , executable ) ) 
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
            UIUtils.invokeLater( new Runnable() {

                @Override
                public void run()
                {
                    setupPerspective();                    
                }} );
        }
    };

    private ViewContainerManager viewContainerManager;
    
    private final class ProjectWrapper implements IResourceResolver {
        
        private ProjectWrapper()
        {
        }
        
        private EditorContainer getEditorPerspective() {
            List<IViewContainer> result = viewContainerManager.getPerspectives( EditorContainer.VIEW_ID );
            return result.isEmpty() ? null : (EditorContainer) result.get(0);
        }
        
        @Override
        public IResource resolve(String identifier) throws ResourceNotFoundException
        {
            final EditorContainer perspective = getEditorPerspective();
            if ( perspective != null ) {
                return perspective.resolve( identifier );
            }
            return project.resolve( identifier );
        }

        @Override
        public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException
        {
            final EditorContainer perspective = getEditorPerspective();
            if ( perspective != null ) {
                return perspective.resolveRelative( identifier , parent );
            }            
            if ( parent instanceof InMemorySourceResource) {
                return project.resolveRelative( identifier , ((InMemorySourceResource) parent).getPersistentResource() );
            }
            return project.resolveRelative( identifier , parent );
        }
    }     

    public DebuggingPerspective(IWorkspace workspace , ViewContainerManager viewContainerManager,
            IApplicationConfig appConfig)
    {
        super(VIEW_ID, viewContainerManager , appConfig);
        if ( workspace == null ) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        this.viewContainerManager = viewContainerManager;
        this.workspace = workspace;
        this.workspace.addWorkspaceListener( workspaceListener );
    }

    public IResourceResolver getResourceResolver()
    {
        return resourceResolver;
    }

    protected void disposeHook() 
    {
        try 
        {
            workspace.removeWorkspaceListener( workspaceListener );
        } 
        finally 
        {
            if ( emulator() != null ) {
                try {
                    this.emulator().dispose();
                } finally {
                    this.proxy.setTarget( null );
                }
            }
        }
    }

    private IEmulator emulator() {
        return proxy.hasTarget() ? proxy.getProxyInstance() : null;
    }

    public void openExecutable(IAssemblyProject project,IResource executable) throws IOException 
    {
        if ( emulator() != null ) 
        {
            this.emulator().dispose();
            this.proxy.setTarget( project.getEmulationOptions().createEmulator() );
        } else {
            this.proxy.setTarget( project.getEmulationOptions().createEmulator() );
            this.emulator().addEmulationListener( listener );            
        }

        // set project & executable BEFORE loading object code
        // so any IEmulationListener that implements #afterMemoryLoad() 
        // can query the DebuggingPerspective for the current project
        this.project = project;
        this.executable = executable;

        final byte[] objectCode = Misc.readBytes( executable );
        emulator().loadMemory( Address.wordAddress( 0 ) , objectCode ); // triggers IEmulationListener#afterMemoryLoad()
    }

    public IAssemblyProject getCurrentProject()
    {
        return project;
    }

    public void reloadEmulator() 
    {
        if ( emulator() != null && project != null ) 
        {
            this.emulator().dispose();
            this.proxy.setTarget( project.getEmulationOptions().createEmulator() );
        } 
    }

    public void resetEmulator() 
    {
        if ( emulator() != null ) {
            emulator().reset( true );
        }
    }

    private void setupPerspective() {

    	final List<IView> createdViews = new ArrayList<>();
        // setup CPU view
        if ( getCPUView() == null ) {
            CPUView view = new CPUView( emulator() );
            createdViews.add( addView( view ) );
        }

        // setup disassembler view
        if ( getDisassemblerView() == null ) {
            DisassemblerView view = new DisassemblerView( this, emulator() );
            createdViews.add( addView( view ) );
        }

        // setup stack view
        if ( getStackView() == null ) {
            StackView view = new StackView( emulator() );
            createdViews.add( addView( view ) );
        }        

        // setup hex-dump view
        if ( getHexDumpView() == null ) {
            final HexDumpView view = new HexDumpView( emulator() );
            createdViews.add( addView( view ) );
        }           

        // setup screen view
        if ( getScreenView() == null ) {
            final ScreenView view = new ScreenView( project , emulator() );
            view.setDebugCustomFonts( false );
            createdViews.add( addView( view ) );
        }     
        
        // setup SPED-3 vector display view
        if ( getVectorDisplayView() == null ) {
            final VectorDisplayView view = new VectorDisplayView( project , emulator() );
            createdViews.add( addView( view ) );
        }            

        // setup source level debug view
        if ( getSourceLevelDebugView() == null ) 
        {
            final SourceLevelDebugView view = new SourceLevelDebugView( resourceResolver , workspace , this ,new NavigationHistory(), emulator() );
            createdViews.add( addView( view ) );
        }

        // setup screen view
        if ( getBreakpointView() == null ) {
            final BreakpointView view = new BreakpointView( getDisassemblerView() , getSourceLevelDebugView() , emulator() ) {

				@Override
				protected IAssemblyProject getCurrentProject() {
					return DebuggingPerspective.this.getCurrentProject();
				}
            };
            createdViews.add( addView( view ) );
        }    
        
        // refresh views AFTER
        // all have been created, views
        // may have interdependencies
        for ( IView v : createdViews ) 
        {
        	v.refreshDisplay();
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
    
    private VectorDisplayView getVectorDisplayView() {
        return (VectorDisplayView) getViewByID( VectorDisplayView.VIEW_ID );
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

    protected static final class EmulatorProxy implements InvocationHandler {

        private final List<IEmulationListener> listeners = new ArrayList<>();

        private final IEmulator proxy;
        private volatile IEmulator emulator;
        
        private Address address;
        private byte[] data;
        
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final Lock readLock = lock.readLock();
        private final Lock writeLock = lock.writeLock();

        public EmulatorProxy() {
            this.proxy = (IEmulator) Proxy.newProxyInstance( EmulatorProxy.class.getClassLoader() , new Class<?>[]{IEmulator.class},this);
        }

        public  boolean hasTarget() 
        {
            lock( readLock );
            try {
                return  emulator != null; 
            } finally {
                readLock.unlock();
            }
        }
        
        private void lock(Lock l) {
            try {
                l.lockInterruptibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to aquire lock "+l);
            }
            
        }

        public void setTarget(IEmulator e) 
        {
            lock(writeLock);
            try
            {            
                if ( this.emulator == e ) {
                    return;
                }

                removeEmulationListeners();

                this.emulator=e;
                
                if ( e != null ) {
                    restoreEmulatorState();
                } else {
                    address = null;
                    data = null;
                }
                
            } finally {
                writeLock.unlock();
            }
        }

        private void restoreEmulatorState()
        {
            if ( this.emulator != null ) 
            {
                for ( IEmulationListener l : listeners ) {
                    this.emulator.addEmulationListener( l );
                }           
                if ( address != null ) {
                    this.emulator.loadMemory( address , data );
                }
            }
        }

        private void removeEmulationListeners()
        {
            if ( this.emulator != null ) 
            {
                for ( IEmulationListener l : listeners ) {
                    this.emulator.removeEmulationListener( l );
                }
            }
        }
        
        public IEmulator getTarget() {
            lock(readLock);
            try {
                return emulator;
            } finally {
                readLock.unlock();
            }
        }

        public IEmulator getProxyInstance() {
            return proxy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            final IEmulator emu;
            lock(readLock);
            try
            {
                if ( method.getName().equals("loadMemory" ) ) 
                {
                    final Address adr = (Address) args[0];
                    final byte[] data = (byte[]) args[1];
                    this.data = data;
                    this.address = adr;
                } else if ( method.getName().equals("addEmulationListener" ) ) {
                    IEmulationListener listener = (IEmulationListener) args[0];
                    if ( listener != null && ! listener.belongsToHardwareDevice() ) {
                        listeners.add( listener );
                    }
                } else if ( method.getName().equals("removeEmulationListener" ) ) {
                    IEmulationListener listener = (IEmulationListener) args[0];
                    if ( listener != null ) 
                    {
                        listeners.remove( listener );
                    }                
                } 
                else if ( method.getName().equals("removeAllEmulationListeners" ) ) 
                {
                    listeners.clear();
                }
                emu = this.emulator;
            } finally {
                readLock.unlock();
            }
            // alien method,invoke outside synchronized block
            boolean success = false;
            try 
            {
            	if ( emu != null ) {
            		Object result = method.invoke( emu , args );
                	success = true;
                	return result;
            	} 
            	System.err.println("Cannot invoke method "+method+" on NULL emulator");
            	return null;
            }
            catch(Exception e) 
            {
            	if ( e instanceof InvocationTargetException) 
            	{
                	if ( ((InvocationTargetException) e).getTargetException() != null ) 
                	{
                		((InvocationTargetException) e).getTargetException().printStackTrace();
                		throw ((InvocationTargetException) e).getTargetException();
                	}
            	} 
            	
            	if ( e.getCause() != null ) 
            	{
            		Throwable cause = e.getCause();
            		while ( cause.getCause() != null ) {
            			cause = cause.getCause();
            		}
            		cause.printStackTrace();
            	} else {
            		e.printStackTrace();
            	}
            	throw e;
            }
            finally 
            {
                if ( success && method.getName().equals("reset" ) ) 
                {
                    if ( address != null ) {
                        emu.loadMemory( address , data );
                    }
                }                 
            }
        }
    }
}