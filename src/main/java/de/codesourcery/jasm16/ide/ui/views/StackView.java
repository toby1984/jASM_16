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
package de.codesourcery.jasm16.ide.ui.views;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.utils.Misc;

public class StackView extends AbstractView
{
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    
    private IEmulator emulator;
    private Address startOfStack = Address.valueOf( 0 );
    private int numberOfWordsToDump = 16;
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
     
    public StackView(IEmulator emulator) {
        setEmulator( emulator );
    }
    
    public StackView() {
    }
    
    @Override
    public void refreshDisplay() 
    {
        if ( emulator == null ) {
            return;
        }
        
        
        final int realStart;
        if ( startOfStack.getValue() - numberOfWordsToDump < 0 ) {
            realStart = ((int) ( Address.MAX_ADDRESS - numberOfWordsToDump) ) & 0xffff;            
        } else {
            realStart = (startOfStack.getValue() - numberOfWordsToDump) & 0xffff;
        }
                
        final byte[] data = emulator.getMemory().getBytes( Address.valueOf( realStart ) , numberOfWordsToDump*2);
        SwingUtilities.invokeLater( new Runnable() {

            @Override
            public void run()
            {
                final List<String> lines = Misc.toHexDumpLines( realStart*2 , data, data.length , 1 , printASCII , true );
                Collections.reverse( lines ); // reverse lines => print stack from highest to lowest address
                
                StringBuilder result = new StringBuilder();
                for (Iterator<String> iterator = lines.iterator(); iterator.hasNext();) {
                    String line = iterator.next();
                    result.append( line );
                    if ( iterator.hasNext() ) {
                        result.append("\n");
                    }
                }
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
