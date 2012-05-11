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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.disassembler.DisassembledLine;
import de.codesourcery.jasm16.disassembler.Disassembler;
import de.codesourcery.jasm16.emulator.BreakPoint;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.utils.Misc;

public class DisassemblerView extends AbstractView
{
    public static final String VIEW_ID = "dissassembly-view";
    
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    
    private IEmulator emulator;
    private Address dumpStartAddress = Address.ZERO;
    private int numberOfInstructionsToDump = 10;
    private boolean showHexDump = true;
    
    private final Disassembler disassembler = new Disassembler();
    
    private final IEmulationListener listener = new IEmulationListener() {

        @Override
        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes)
        {
            refreshDisplay();
        }
        
        @Override
        public void beforeExecution(IEmulator emulator) {  
        }

        @Override
        public void afterExecution(IEmulator emulator, int commandDuration)
        {
            refreshDisplay();            
        }

        @Override
        public void afterReset(IEmulator emulator)
        {
            refreshDisplay();
        }

        @Override
        public void onBreakpoint(IEmulator emulator, BreakPoint breakpoint)
        {
            // TODO Auto-generated method stub
            
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
        
        final Address offset = Address.wordAddress( 3 );
        final List<DisassembledLine> lines = disassembler.disassemble( emulator.getMemory() , pc.minus( offset ) , numberOfInstructionsToDump , showHexDump );
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
        
        // setup top panel
        final JPanel buttonBar = new JPanel();
        buttonBar.setLayout( new GridBagLayout() );        

        // =========== "SINGLE STEP" button ============        
        final JButton singleStepButton = new JButton("Step");
        singleStepButton.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.executeOneInstruction();
            }
        });
        
        GridBagConstraints cnstrs = constraints( 0 , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( singleStepButton , cnstrs );
        
        // =========== "RUN" button ============        
        final JButton runButton = new JButton("Run");
        runButton.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.start();
            }
        });
        
        cnstrs = constraints( 1 , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( runButton , cnstrs );   
        
        // =========== "STOP" button ============        
        final JButton stopButton = new JButton("Stop");
        stopButton.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.stop();
            }
        });
        
        cnstrs = constraints( 2 , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( stopButton , cnstrs );          
        
        // =========== "RESET" button ============
        final JButton resetButton = new JButton("Reset");
        resetButton.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.reset(false);
            }
        });
        
        cnstrs = constraints( 3 , 0 , true , true , GridBagConstraints.NONE );          
        buttonBar.add( resetButton , cnstrs );
        
        // setup bottom panel        
        final JPanel bottomPanel = new JPanel();
        setColors( bottomPanel );
        bottomPanel.setLayout( new GridBagLayout() );        
        cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
        bottomPanel.add( textArea , cnstrs );
        
        // ======== assemble result panel ===========
        final JPanel result = new JPanel();
        setColors( result );
        result.setLayout( new GridBagLayout() );       
        
        cnstrs = constraints( 0 , 0 , true, false , GridBagConstraints.HORIZONTAL );
        result.add( buttonBar  , cnstrs );
        cnstrs = constraints( 0 , 1 , true , true , GridBagConstraints.BOTH);
        result.add( bottomPanel  , cnstrs );        
        
        return result;
    }

    @Override
    public JPanel getPanel() {
        if ( panel == null ) {
            panel = createPanel();
        }
        return panel;
    }

	@Override
	public String getTitle() {
		return "Disassembly view";
	}

	@Override
	public String getID() {
		return VIEW_ID;
	}
    
}
