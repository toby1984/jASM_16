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

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.ICPU;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.utils.Misc;

public class CPUView extends AbstractView
{
    public static final String VIEW_ID = "cpu-view";
    
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    
    private IEmulator emulator;
    
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
        builder.append("\nPC: "+Misc.toHexString( cpu.getPC().getValue() ) );
        builder.append(" (elapsed cycles: "+cpu.getCurrentCycleCount()).append(")");
        builder.append("\n");
        
        builder.append("EX: "+Misc.toHexString( cpu.getEX() )).append("\n");
        builder.append("IA: "+Misc.toHexString( cpu.getInterruptAddress() )).append("\n");
        
        String text="Interrupt queueing is ";
        if ( cpu.isQueueInterrupts() ) {
        	text += "ON";
        } else {
        	text += "OFF";
        }
        builder.append("IQ: "+text+"\n");   
        builder.append("IRQs: "+StringUtils.join( cpu.getInterruptQueue() , "," )).append("\n");
        builder.append("SP: "+Misc.toHexString( cpu.getSP().getValue() )).append("\n");
        
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

    @Override
    public JPanel getPanel() 
    {
        if ( panel == null ) {
            panel = createPanel();
        }
        return panel;
    }

	@Override
	public String getTitle() {
		return "CPU view";
	}

	@Override
	public String getID() {
		return VIEW_ID;
	}
    
}