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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.memory.MemUtils;
import de.codesourcery.jasm16.ide.ui.utils.PagingKeyAdapter;
import de.codesourcery.jasm16.utils.Misc;

public class HexDumpView extends AbstractView
{
    public static final String VIEW_ID = "hexdump-view";
    
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    
    private IEmulator emulator;
    private Address dumpStartAddress = Address.wordAddress( 0x0 );
    private int numberOfWordsPerLine = 8;
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
		public void onStopHook(IEmulator emulator, Address previousPC, Throwable emulationError) {
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
        
        SwingUtilities.invokeLater( new Runnable() {

            @Override
            public void run()
            {
            	final byte[] data = MemUtils.getBytes( emulator.getMemory() , 
            		dumpStartAddress ,
            		calcSizeOfVisibleMemory(),
            		true
            	);
            	
                textArea.setText( 
                		Misc.toHexDumpWithAddresses(dumpStartAddress, 
                				data, 
                				data.length , 
                				numberOfWordsPerLine , 
                				printASCII,
                				true)
                );                
            }
        });
    }
    
    protected Size calcSizeOfVisibleMemory() 
    {
    	int rows = calculateVisibleTextRowCount( textArea );
    	if ( rows < 1 ) {
    		rows = 1;
    	}
    	return Size.words( rows * numberOfWordsPerLine );    	
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
    public void disposeHook() 
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
        
        textArea.addKeyListener( new PagingKeyAdapter() {
			
			@Override
			protected void onePageUp() {
				HexDumpView.this.onePageUp();
			}
			
			@Override
			protected void onePageDown() {
				HexDumpView.this.onePageDown();				
			}
			
			@Override
			protected void oneLineUp() {
				HexDumpView.this.oneLineUp();
			}
			
			@Override
			protected void oneLineDown() {
				HexDumpView.this.oneLineDown();				
			}
		});
        
        result.addComponentListener( new ComponentAdapter() {
			
			@Override
			public void componentResized(ComponentEvent e) {
				refreshDisplay();
			}
		});
        
        return result;
    }
    
    private void onePageDown() {
        dumpStartAddress = dumpStartAddress.plus( calcSizeOfVisibleMemory() , true );
        refreshDisplay();        
    }
    
    private void oneLineDown() {
        dumpStartAddress = dumpStartAddress.plus( Size.words( numberOfWordsPerLine ) , true );
        refreshDisplay();        
    }    
    
    private void onePageUp() {
        dumpStartAddress = dumpStartAddress.minus( calcSizeOfVisibleMemory() );
        refreshDisplay();        
    }    
    
    private void oneLineUp() {
        dumpStartAddress = dumpStartAddress.minus( Size.words( numberOfWordsPerLine ) );
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
