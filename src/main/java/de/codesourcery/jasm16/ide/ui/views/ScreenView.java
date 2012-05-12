package de.codesourcery.jasm16.ide.ui.views;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.font.LineMetrics;

import javax.swing.JPanel;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.BreakPoint;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.IReadOnlyMemory;

public class ScreenView extends AbstractView
{
    public static final String VIEW_ID="screen-view";
    
    private final Canvas canvas = new Canvas() {
        
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
                    final int value = memory.read( currentMemLocation ) & 0x00ff;
                    final char c = (char) value;
                    
                    final int x = 15+(maxCharWidth * column);
                    final int y = 15+(maxCharHeight * row);
                    g.drawString( ""+c , x , y );
                }
            }
            
        };
    };
    
    private final IEmulationListener listener = new EmulationListener() {
        
        @Override
        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes)
        {
            refreshDisplay();            
        }
        
        @Override
        public void onBreakpoint(IEmulator emulator, BreakPoint breakpoint)
        {
        }
        
        @Override
        public void beforeCommandExecution(IEmulator emulator)
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
    }
    
    @Override
    public void dispose()
    {
        this.emulator.removeEmulationListener( listener );
    }

    @Override
    public void refreshDisplay()
    {
        canvas.repaint();
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
        canvas.setPreferredSize( new Dimension(400,200 ) );
        canvas.setSize( new Dimension(400,200 ) );
        
        final JPanel panel = new JPanel();
        setColors( panel );
        panel.setLayout( new GridBagLayout() );        
        final GridBagConstraints cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
        panel.add( canvas , cnstrs );
        return panel;
    }

}
