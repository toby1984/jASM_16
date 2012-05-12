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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.emulator.BreakPoint;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.MemUtils;
import de.codesourcery.jasm16.utils.Misc;

public class HexDumpView extends AbstractView
{
    public static final String VIEW_ID = "hexdump-view";
    
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    
    private IEmulator emulator;
    private Address dumpStartAddress = Address.wordAddress( 0x0 );
    private int numberOfWordsPerLine = 8;
    private int numberOfBytesToDump = 256;
    private boolean printASCII = true;
    
    private final IEmulationListener listener = new EmulationListener() {

        @Override
        public void afterCommandExecution(IEmulator emulator, int commandDuration)
        {
        	if ( ! isFullSpeedMode() ) {
        		refreshDisplay();
        	}
        }

        @Override
        public void afterReset(IEmulator emulator)
        {
        	if ( ! isFullSpeedMode() ) {
        		refreshDisplay();
        	}
        }

        @Override
        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes)
        {
        	if ( ! isFullSpeedMode() ) {
        		refreshDisplay();
        	}
        }

		@Override
		public void afterContinuousExecutionHook() {
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
        
        final byte[] data = MemUtils.getBytes( emulator.getMemory() , dumpStartAddress , numberOfBytesToDump );
        SwingUtilities.invokeLater( new Runnable() {

            @Override
            public void run()
            {
                textArea.setText( Misc.toHexDumpWithAddresses(dumpStartAddress, data, data.length , numberOfWordsPerLine , printASCII,true));                
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
        textArea.setRows( numberOfBytesToDump / ( numberOfWordsPerLine*2 ) );
        
        // dump panel
        final JPanel dumpPanel = new JPanel();
        setColors( dumpPanel );
        dumpPanel.setLayout( new GridBagLayout() );        
        GridBagConstraints cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
        dumpPanel.add( textArea , cnstrs );
        
        // toolbar panel
        final JPanel toolbarPanel = new JPanel();
        setColors( toolbarPanel );
        toolbarPanel.setLayout( new GridBagLayout() );       
        
        cnstrs = constraints( 0 , 0 , false , false , GridBagConstraints.NONE);
        toolbarPanel.add( new JLabel("Goto") , cnstrs );
        
        final JTextField gotoTextfield = new JTextField();
        gotoTextfield.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                final String val = gotoTextfield.getText();
                Address adr;
                if ( StringUtils.isBlank( val ) ) {
                    gotoTextfield.setText("0000");
                    adr = Address.wordAddress( 0 );
                } 
                else 
                {
                    try {
                        adr = Address.wordAddress( Misc.parseHexString( val ) );
                    }
                    catch (NumberFormatException e1) {
                        gotoTextfield.setText("0000");
                        adr = Address.wordAddress( 0 );                        
                    }
                }
                dumpStartAddress = adr;
                refreshDisplay();
            }
        });
        
        cnstrs = constraints(  0 , 1 , true , true , GridBagConstraints.HORIZONTAL );
        toolbarPanel.add( gotoTextfield , cnstrs );        

        // create result panel
        final JPanel result = new JPanel();
        setColors( result );
        result.setLayout( new GridBagLayout() );        
        cnstrs = constraints( 0 , 0 , false , true , GridBagConstraints.BOTH );
        result.add( dumpPanel , cnstrs );
        cnstrs = constraints( 1 , 0 , true , true , GridBagConstraints.VERTICAL );
        result.add( toolbarPanel , cnstrs );        
        
        textArea.addKeyListener( new KeyAdapter() 
        {
            public void keyPressed(java.awt.event.KeyEvent e) 
            {
                switch( e.getKeyCode() ) 
                {
                    case KeyEvent.VK_PAGE_DOWN:
                        onePageDown();
                        break;    
                    case KeyEvent.VK_PAGE_UP:
                        onePageUp();
                        break;                         
                    case KeyEvent.VK_DOWN:
                    case KeyEvent.VK_KP_DOWN:
                        oneLineDown();
                        break;
                    case KeyEvent.VK_UP:
                    case KeyEvent.VK_KP_UP:
                        oneLineUp();
                        break;                        
                }
            }
        } );
        
        return result;
    }
    
    private void onePageDown() {
        dumpStartAddress = dumpStartAddress.plus( Size.sizeInBytes( numberOfBytesToDump ) , true );
        refreshDisplay();        
    }
    
    private void oneLineDown() {
        dumpStartAddress = dumpStartAddress.plus( Size.sizeInWords( numberOfWordsPerLine ) , true );
        refreshDisplay();        
    }    
    
    private void onePageUp() {
        dumpStartAddress = dumpStartAddress.minus( Size.sizeInBytes( numberOfBytesToDump ) );
        refreshDisplay();        
    }    
    
    private void oneLineUp() {
        dumpStartAddress = dumpStartAddress.minus( Size.sizeInWords( numberOfWordsPerLine ) );
        refreshDisplay();           
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
		return "memory view";
	}

	@Override
	public String getID() {
		return VIEW_ID;
	}
}
