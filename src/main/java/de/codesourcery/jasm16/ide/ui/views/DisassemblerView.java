package de.codesourcery.jasm16.ide.ui.views;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Iterator;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.Disassembler;
import de.codesourcery.jasm16.emulator.Disassembler.DisassembledLine;
import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.utils.Misc;

public class DisassemblerView extends AbstractView
{
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    
    private IEmulator emulator;
    private Address dumpStartAddress = Address.ZERO;
    private int numberOfInstructionsToDump = 10;
    private boolean showHexDump = true;
    
    private final Disassembler disassembler = new Disassembler();
    
    private final IEmulationListener listener = new IEmulationListener() {

        @Override
        public void onMemoryLoad(Emulator emulator, Address startAddress, int lengthInBytes)
        {
            refreshDisplay();
        }
        
        @Override
        public void beforeExecution(Emulator emulator) {  
            refreshDisplay();
        }

        @Override
        public void afterExecution(Emulator emulator, int commandDuration)
        {
            refreshDisplay();            
        }

        @Override
        public void onReset(Emulator emulator)
        {
            refreshDisplay();
        }
     };
     
    public DisassemblerView(IEmulator emulator) {
        setEmulator( emulator );
    }
    
    public DisassemblerView() {
    }
    
    @Override
    public void refreshDisplay() 
    {
        if ( emulator == null ) {
            return;
        }
        
        final Address pc = emulator.getCPU().getPC();
        
        final byte[] data = emulator.getMemory().getBytes( dumpStartAddress , numberOfInstructionsToDump*2*3 ); // assume worst-case: 6 bytes per instruction
        
        final List<DisassembledLine> lines = disassembler.disassemble( Address.ZERO , data , numberOfInstructionsToDump , showHexDump );
        final StringBuilder result = new StringBuilder();
        final Iterator<DisassembledLine> it = lines.iterator();
        while( it.hasNext() ) 
        {
            final DisassembledLine line = it.next();
            final int realAddress = line.getAddress().getValue()+dumpStartAddress.getValue();
            final String prefix = realAddress == pc.getValue() ? " >> " : "    ";
            result.append( prefix ).append( Misc.toHexString( realAddress ) ).append(": ").append( line.getContents() );
            if ( it.hasNext() ) {
                result.append("\n");
            }
        }
        
        SwingUtilities.invokeLater( new Runnable() {

            @Override
            public void run()
            {
                textArea.setText( result.toString() );         
            }
        });
    }
    
    public void setEmulator(IEmulator emulator)
    {
        if (emulator == null) {
            throw new IllegalArgumentException("emulator must not be NULL.");
        }
        if ( this.emulator == emulator ) {
            return;
        }
        
        if ( this.emulator != null ) {
            this.emulator.removeEmulationListener( listener );
        }
        this.emulator = emulator;
        
        emulator.addEmulationListener( listener );
    }
    
    @Override
    public void dispose() 
    {
        if ( this.emulator != null ) {
            this.emulator.removeEmulationListener( listener );
            this.emulator = null;
        }
    }
    
    protected JPanel createPanel()
    {
        textArea.setEditable( false );
        setColors( textArea );
        textArea.setFont( getMonospacedFont() );
        textArea.setEditable( false );
        
        final JPanel panel = new JPanel();
        setColors( panel );
        panel.setLayout( new GridBagLayout() );        
        final GridBagConstraints cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
        panel.add( textArea , cnstrs );
        
        return panel;
    }

    @Override
    public JPanel getPanel() {
        if ( panel == null ) {
            panel = createPanel();
        }
        return panel;
    }
    
}
