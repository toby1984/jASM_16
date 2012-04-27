package de.codesourcery.jasm16.ide.ui.views;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.StyleConstants;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.utils.Misc;

public class HexDumpView extends AbstractView
{
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    
    private IEmulator emulator;
    private Address dumpStartAddress = Address.valueOf( 0 );
    private int numberOfBytesToDump = 128;
    private boolean printASCII = true;
    
    private final IEmulationListener listener = new IEmulationListener() {

        @Override
        public void beforeExecution(Emulator emulator) {  }

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

        @Override
        public void onMemoryLoad(Emulator emulator, Address startAddress, int lengthInBytes)
        {
            refreshDisplay();
        }
     };
     
    public HexDumpView(IEmulator emulator) {
        setEmulator( emulator );
    }
    
    public HexDumpView() {
    }
    
    @Override
    public void refreshDisplay() 
    {
        if ( emulator == null ) {
            return;
        }
        
        final byte[] data = emulator.getMemory().getBytes( dumpStartAddress , numberOfBytesToDump );
        SwingUtilities.invokeLater( new Runnable() {

            @Override
            public void run()
            {
                textArea.setText( Misc.toHexDumpWithAddresses(dumpStartAddress.getValue(), data, data.length , 4 , printASCII));                
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
