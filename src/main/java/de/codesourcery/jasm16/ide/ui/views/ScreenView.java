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
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultKeyboard;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion;
import de.codesourcery.jasm16.emulator.memory.MemoryRegion;

public class ScreenView extends AbstractView
{
    public static final String VIEW_ID="screen-view";
    
    private volatile JPanel panel;
    
    private final IMemoryRegion videoRAM = new MemoryRegion("video RAM",
    		new AddressRange(Address.wordAddress( 0x8000 ) , 
    				Size.words( 384 ) ) // 32x12 words 
    		) 
    {
    	@Override
    	public void clear() {
    		super.clear();
        	if ( panel != null ) {
        		panel.repaint();        		
        	}    		
    	};
    	
    	@Override
    	public void write(Address address, int value) {
    		super.write( address, value);
        	if ( panel != null ) {
        		panel.repaint();
        	}    		
    	};
    	
    	@Override
    	public void write(int wordAddress, int value) {
    		super.write( wordAddress , value );
        	if ( panel != null ) {
        		panel.repaint();
        	}    		
    	};
    }; 
    
    private final IEmulator emulator;
    private final DefaultKeyboard keyboard = new DefaultKeyboard(); 
    
    public ScreenView(IEmulator emulator) {
        if (emulator == null) {
            throw new IllegalArgumentException("emulator must not be NULL.");
        }
        this.emulator = emulator;
        this.emulator.mapRegion( videoRAM );
		this.emulator.addDevice( keyboard );
    }
    
    @Override
    public void dispose()
    {
        this.emulator.unmapRegion( videoRAM );
        this.emulator.removeDevice( keyboard );
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
    	if ( panel == null ) 
    	{
    		panel=createPanel();
    		keyboard.setInputComponent( panel );
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
                 
                 /*
         The LEM1802 is a 128x96 pixel color display compatible with the DCPU-16.
         The display is made up of 32x12 16 bit cells. Each cell displays one
         monochrome 4x8 pixel character out of 128 available. Each cell has its own
         foreground and background color out of a palette of 16 colors.             
                  */
                 Rectangle2D metrics = g.getFontMetrics().getStringBounds("X" , g );
                 
                 final int maxCharHeight = (int) Math.round( metrics.getHeight() );
                 final int maxCharWidth = (int) Math.round( metrics.getWidth() );
                 
                 int videoRam = 0;
                 for ( int row = 0 ; row < 12 ; row++ ) 
                 {
                     for ( int column = 0 ; column < 32 ; column++ ) 
                     {
                         final int currentMemLocation = videoRam+( 32 * row ) + column;
                         @SuppressWarnings("deprecation")
						final int value = videoRAM.read( currentMemLocation ) & 0x00ff;
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
        panel.setFocusable( true );
        setColors( panel );
        return panel;
    }
}