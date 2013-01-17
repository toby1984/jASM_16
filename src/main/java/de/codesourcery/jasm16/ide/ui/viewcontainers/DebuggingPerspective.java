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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
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

    private final IResourceResolver resourceResolver;
    private final IWorkspace workspace;

    private final EmulatorProxy proxy = new EmulatorProxy();

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

        public void projectClosed(IAssemblyProject closedProject) {
            if ( closedProject.isSame( project ) ) 
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
        public void onStopHook(IEmulator emulator, Address previousPC, Throwable emulationError) {
        }
    };


    public DebuggingPerspective(IWorkspace workspace ,
            IApplicationConfig appConfig,
            IResourceResolver resourceResolver)
    {
        super(ID, appConfig);
        if ( workspace == null ) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        this.resourceResolver = resourceResolver;
        this.workspace = workspace;
        this.workspace.addWorkspaceListener( workspaceListener );
    }

    public IResourceResolver getResourceResolver()
    {
        return resourceResolver;
    }

    @Override
    public void dispose() 
    {
        try {
            workspace.removeWorkspaceListener( workspaceListener );
            super.dispose();
        } 
        finally {
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

        // setup CPU view
        if ( getCPUView() == null ) {
            CPUView view = new CPUView( emulator() );
            addView( view );
            view.refreshDisplay();
        }

        // setup disassembler view
        if ( getDisassemblerView() == null ) {
            DisassemblerView view = new DisassemblerView( this, emulator() );
            addView( view );
            view.refreshDisplay();
        }

        // setup stack view
        if ( getStackView() == null ) {
            StackView view = new StackView( emulator() );
            addView( view );
            view.refreshDisplay();
        }        

        // setup hex-dump view
        if ( getHexDumpView() == null ) {
            final HexDumpView view = new HexDumpView( emulator() );
            addView( view );
            view.refreshDisplay();
        }           

        // setup screen view
        if ( getScreenView() == null ) {
            final ScreenView view = new ScreenView( project , emulator() );
            view.setDebugCustomFonts( false );
            addView( view );
            view.refreshDisplay();
        }     

        // setup source level debug view
        if ( getSourceLevelDebugView() == null ) {
            final SourceLevelDebugView view = new SourceLevelDebugView( resourceResolver , workspace , this , emulator() );
            addView( view );
            view.refreshDisplay();            
        }

        // setup screen view
        if ( getBreakpointView() == null ) {
            final BreakpointView view = new BreakpointView( getDisassemblerView() , getSourceLevelDebugView() , emulator() );
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
                    System.out.println("Same proxy target, doing nothing.");
                    return;
                }

                System.out.println("Changing proxy target from "+this.emulator+" to "+e);
                
                removeEmulationListeners();

                this.emulator=e;
                
                if ( e != null ) {
                    restoreEmulatorState();
                } else {
                    System.out.println("Proxy discards cached state");
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
                System.out.println("Adding "+listeners.size()+" listeners to emulator "+this.emulator);
                for ( IEmulationListener l : listeners ) {
                    this.emulator.addEmulationListener( l );
                }           
                if ( address != null ) {
                    System.out.println("Restoring memory of emulator "+this.emulator);
                    this.emulator.loadMemory( address , data );
                }
            }
        }

        private void removeEmulationListeners()
        {
            if ( this.emulator != null ) 
            {
                System.out.println("Removing "+listeners.size()+" listeners from emulator "+this.emulator);
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
                if ( method.getName().equals("dispose" ) ) 
                {
                    System.out.println("Intercepted dispose() call.");
                } 
                else if ( method.getName().equals("loadMemory" ) ) 
                {
                    final Address adr = (Address) args[0];
                    final byte[] data = (byte[]) args[1];
                    this.data = data;
                    this.address = adr;
                } else if ( method.getName().equals("addEmulationListener" ) ) {
                    IEmulationListener listener = (IEmulationListener) args[0];
                    if ( listener != null && ! listener.belongsToHardwareDevice() ) {
                        System.out.println("Intercepted addEmulationListener() call");
                        listeners.add( listener );
                    }
                } else if ( method.getName().equals("removeEmulationListener" ) ) {
                    IEmulationListener listener = (IEmulationListener) args[0];
                    if ( listener != null ) 
                    {
                        System.out.println("Intercepted removeEmulationListener() call");
                        listeners.remove( listener );
                    }                
                } 
                else if ( method.getName().equals("removeAllEmulationListeners" ) ) 
                {
                    System.out.println("Intercepted removeAllEmulationListeners() call");
                    listeners.clear();
                }
                emu = this.emulator;
            } finally {
                readLock.unlock();
            }
            // alien method,invoke outside synchronized block
            boolean success = false;
            try {
                Object result = method.invoke( emu , args );
                success = true;
                return result;
            }
            catch(InvocationTargetException e) {
            	if ( e.getTargetException() != null ) {
            		throw e.getTargetException();
            	}
            	throw e;
            }
            finally 
            {
                if ( success && method.getName().equals("reset" ) ) 
                {
                    System.out.println("Intercepted reset() call");
                    if ( address != null ) {
                        System.out.println("Restoring memory");
                        emu.loadMemory( address , data );
                    }
                }                 
            }
        }
    }
}