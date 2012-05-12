package de.codesourcery.jasm16.ide.ui.views;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.font.LineMetrics;

import javax.swing.JPanel;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.IMemoryRegion;
import de.codesourcery.jasm16.emulator.IReadOnlyMemory;
import de.codesourcery.jasm16.emulator.MemoryRegion;

public class ScreenView extends AbstractView
{
    public static final String VIEW_ID="screen-view";
    
    private JPanel panel;
    
    private final IMemoryRegion videoRAM = new MemoryRegion("video RAM",
    		new AddressRange(Address.wordAddress( 0x4000 ) , 
    				Size.words( 384 ) ) // 32x12 words 
    		); 
    
    private final IEmulationListener listener = new EmulationListener() {
        
        @Override
        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes)
        {
            refreshDisplay();            
        }
        
        @Override
        public void afterReset(IEmulator emulator)
        {
            refreshDisplay();            
        }
        
        @Override
        public void afterCommandExecution(IEmulator emulator, int commandDuration)
        {
            refreshDisplay();
        }
        
    	@Override
    	public boolean isInvokeAfterAndBeforeCommandExecutionInContinuousMode() {
    		return true;
    	}        
        
		@Override
		public void afterContinuousExecutionHook() {
			refreshDisplay();
		}        
    };
    
    private final IEmulator emulator;
    
    public ScreenView(IEmulator emulator) {
        if (emulator == null) {
            throw new IllegalArgumentException("emulator must not be NULL.");
        }
        this.emulator = emulator;
        this.emulator.addEmulationListener(listener);
        this.emulator.mapRegion( videoRAM );
    }
    
    @Override
    public void dispose()
    {
        this.emulator.removeEmulationListener( listener );
        this.emulator.unmapRegion( videoRAM );
    }

    @Override
    public void refreshDisplay()
    {
    	if ( panel != null ) {
    		panel.repaint();
    	}
    }

    @Override
    public String getTitle()
    {
        return "Screen";
    }

    @Override
    public String getID()
    {
        return VIEW_ID;
    }
    
    @Override
    protected JPanel getPanel()
    {
    	if ( panel == null ) {
    		panel=createPanel();
    	}
    	return panel;
    }

    protected JPanel createPanel()
    {
        panel = new JPanel() 
        {
        
        	public void paint(java.awt.Graphics g) 
             {
                 g.setColor( Color.BLACK );
                 super.paint( g );
                 g.setColor( Color.GREEN );
                 
                 final IReadOnlyMemory memory = emulator.getMemory();
                 
                 /*
         The LEM1802 is a 128x96 pixel color display compatible with the DCPU-16.
         The display is made up of 32x12 16 bit cells. Each cell displays one
         monochrome 4x8 pixel character out of 128 available. Each cell has its own
         foreground and background color out of a palette of 16 colors.             
                  */
                 final LineMetrics metrics = g.getFontMetrics().getLineMetrics("X" , g );
                 
                 final int maxCharHeight = Math.round( metrics.getHeight() );
                 final int maxCharWidth = g.getFontMetrics().getMaxAdvance();
                 
                 int videoRam = Address.wordAddress( 0x8000 ).getValue();
                 for ( int row = 0 ; row < 12 ; row++ ) 
                 {
                     for ( int column = 0 ; column < 32 ; column++ ) 
                     {
                         final int currentMemLocation = videoRam+( 32 * row ) + column;
                         @SuppressWarnings("deprecation")
						final int value = memory.read( currentMemLocation ) & 0x00ff;
                         final char c = (char) value;
                         
                         final int x = 15+(maxCharWidth * column);
                         final int y = 15+(maxCharHeight * row);
                         g.drawString( ""+c , x , y );
                     }
                 }
                 
             };        	
        };
        
        panel.setDoubleBuffered( true );
        panel.setPreferredSize( new Dimension(400,200 ) );
        panel.setSize( new Dimension(400,200 ) );        
        setColors( panel );
        return panel;
    }
}