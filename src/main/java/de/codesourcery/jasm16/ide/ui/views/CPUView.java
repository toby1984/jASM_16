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

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.ICPU;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.TextRegion;

public class CPUView extends AbstractView
{
    public static final String VIEW_ID = "cpu-view";
    
    private JPanel panel;
    private final SimpleAttributeSet defaultStyle;      
    private final SimpleAttributeSet errorStyle;  
    private final JTextPane textArea = new JTextPane();
    
    private IEmulator emulator;
    
    private final IEmulationListener listener = new EmulationListener() {

        
        public void afterCommandExecution(IEmulator emulator, int commandDuration)
        {
        	if ( ! isFullSpeedMode() ) {
        		refreshDisplay();
        	}
        }

        
        public void afterReset(IEmulator emulator)
        {
        	if ( ! isFullSpeedMode() ) {
        		refreshDisplay();
        	}
        }

        
        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes)
        {
        	if ( ! isFullSpeedMode() ) {
        		refreshDisplay();
        	}
        }

		
		public void onStopHook(IEmulator emulator, Address previousPC, Throwable emulationError) {
			refreshDisplay();
		}
     };
    
    public CPUView() 
    {
        errorStyle = createStyle(Color.RED);
        defaultStyle = createStyle(Color.GREEN);
    }
    
    public CPUView(IEmulator emulator) 
    {
        this();
        setEmulator( emulator );
    }    
    
    protected final static SimpleAttributeSet createStyle(Color color) 
    {
        SimpleAttributeSet result = new SimpleAttributeSet();
        StyleConstants.setForeground( result , color );
        return result;
    }    
    
    
    public void refreshDisplay() 
    {
        if ( emulator == null ) {
            return;
        }
        final ICPU cpu = emulator.getCPU();

        final StringBuilder builder = new StringBuilder();
        final List<ITextRegion> redRegions = new ArrayList<ITextRegion>();
        
        Throwable lastError = emulator.getLastEmulationError();
        if ( lastError != null ) 
        {
            final String msg = StringUtils.isBlank( lastError.getMessage() ) ? lastError.getClass().getName() : lastError.getMessage();
            builder.append("Emulation stopped with an error: "+msg+"\n");
            redRegions.add( new TextRegion( 0 , builder.length() ) );
        }        
        
        int itemsInLine = 0;
        for ( int i = 0 ; i < ICPU.COMMON_REGISTER_NAMES.length ; i++ ) {
            builder.append( ICPU.COMMON_REGISTER_NAMES[i]+": "+Misc.toHexString( cpu.getCommonRegisters()[i] )+"    ");
            itemsInLine++;
            if ( itemsInLine == 4 ) {
                itemsInLine = 0;
                builder.append("\n");
            }
        }
        builder.append("\nPC: "+Misc.toHexString( cpu.getPC().getValue() ) );
        builder.append(" (elapsed cycles: "+cpu.getCurrentCycleCount()).append(")");
        builder.append("\n");
        
        builder.append("EX: "+Misc.toHexString( cpu.getEX() )).append("\n");
        builder.append("IA: "+Misc.toHexString( cpu.getInterruptAddress() )).append("\n");
        
        builder.append("IQ: Interrupt queueing is ");
        if ( cpu.isQueueInterrupts() ) 
        {
            int start = builder.length();
            builder.append("ON");
            redRegions.add( new TextRegion( start , builder.length() - start ) );
        } else {
            builder.append("OFF");
        }        
        builder.append("\n");   
        builder.append("IRQs: "+StringUtils.join( cpu.getInterruptQueue() , "," )).append("\n");
        builder.append("SP: "+Misc.toHexString( cpu.getSP().getValue() )).append("\n");
        
        SwingUtilities.invokeLater( new Runnable() {
            
            public void run()
            {
                final StyledDocument doc = textArea.getStyledDocument();
                doc.putProperty(Document.StreamDescriptionProperty, null);                
                
                textArea.setText( builder.toString() );
                for ( ITextRegion region : redRegions ) {
                    doc.setCharacterAttributes( region.getStartingOffset() , region.getLength() , errorStyle  , true );
                }
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
    
    
    public void disposeHook() 
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

    
    public JPanel getPanel() 
    {
        if ( panel == null ) {
            panel = createPanel();
        }
        return panel;
    }

	
	public String getTitle() {
		return "CPU view";
	}

	
	public String getID() {
		return VIEW_ID;
	}
    
}