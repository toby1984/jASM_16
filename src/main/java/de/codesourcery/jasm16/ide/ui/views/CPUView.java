package de.codesourcery.jasm16.ide.ui.views;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.ICPU;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.utils.Misc;

public class CPUView extends AbstractView
{
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    
    private IEmulator emulator;
    
    private final IEmulationListener listener = new IEmulationListener() {

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

        @Override
        public void onMemoryLoad(Emulator emulator, Address startAddress, int lengthInBytes)
        {
        }
     };
    
    public CPUView() 
    {
    }
    
    public CPUView(IEmulator emulator) 
    {
        setEmulator( emulator );
    }    
    
    @Override
    public void refreshDisplay() 
    {
        if ( emulator == null ) {
            return;
        }
        final ICPU cpu = emulator.getCPU();

        final StringBuilder builder = new StringBuilder();
        
        int itemsInLine = 0;
        for ( int i = 0 ; i < ICPU.COMMON_REGISTER_NAMES.length ; i++ ) {
            builder.append( ICPU.COMMON_REGISTER_NAMES[i]+": "+Misc.toHexString( cpu.getCommonRegisters()[i] )+"    ");
            itemsInLine++;
            if ( itemsInLine == 4 ) {
                itemsInLine = 0;
                builder.append("\n");
            }
        }
        builder.append("\nPC: "+Misc.toHexString( cpu.getPC().getValue() )).append("\n");
        builder.append("EX: "+Misc.toHexString( cpu.getEX() )).append("\n");
        builder.append("IA: "+Misc.toHexString( cpu.getInterruptAddress() )).append("\n");        
        builder.append("SP: "+Misc.toHexString( cpu.getSP().getValue() ));
        
        SwingUtilities.invokeLater( new Runnable() {
            @Override
            public void run()
            {
                textArea.setText( builder.toString() );
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
        textArea.setFont( getMonospacedFont() );        
        textArea.setEditable( false );
        setColors( textArea );

        final JPanel panel = new JPanel();
        setColors( panel );
        panel.setLayout( new GridBagLayout() );        
        final GridBagConstraints cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
        panel.add( textArea , cnstrs );
        return panel;
    }

    @Override
    public JPanel getPanel() 
    {
        if ( panel == null ) {
            panel = createPanel();
        }
        return panel;
    }
    
}
