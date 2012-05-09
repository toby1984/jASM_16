package de.codesourcery.jasm16.ide.ui.viewcontainers;

import java.io.IOException;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.ide.IApplicationConfig;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.ui.views.CPUView;
import de.codesourcery.jasm16.ide.ui.views.DisassemblerView;
import de.codesourcery.jasm16.ide.ui.views.HexDumpView;
import de.codesourcery.jasm16.ide.ui.views.StackView;
import de.codesourcery.jasm16.utils.Misc;

public class DebuggingPerspective extends Perspective
{
    public static final String ID = "debugger";
    
    private final IEmulator emulator;
    
    private IAssemblyProject project;
    
    @SuppressWarnings("unused")
    private IResource executable;
    
    private final IEmulationListener listener = new IEmulationListener() 
    {
        @Override
        public void onReset(Emulator emulator)
        {
        }
        
        @Override
        public void onMemoryLoad(Emulator emulator, Address startAddress, int lengthInBytes)
        {
            setupPerspective();
        }
        
        @Override
        public void beforeExecution(Emulator emulator)
        {
        }
        
        @Override
        public void afterExecution(Emulator emulator, int commandDuration)
        {
        }
    };
    
    
    public DebuggingPerspective(IApplicationConfig appConfig)
    {
        super(ID, appConfig);
        this.emulator = new Emulator();
        this.emulator.addEmulationListener( listener );
    }
    
    public void openExecutable(IAssemblyProject project,IResource executable) throws IOException 
    {
        final byte[] objectCode = Misc.readBytes( executable );
        emulator.loadMemory( Address.wordAddress( 0 ) , objectCode ); // will trigger IEmulationListener
        this.project = project;
        this.executable = executable;
    }
    
    public IAssemblyProject getCurrentProject()
    {
        return project;
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
            DisassemblerView view = new DisassemblerView( emulator );
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
}
