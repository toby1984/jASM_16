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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationOptionsProvider;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultKeyboard;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultScreen;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;

public class ScreenView extends AbstractView
{
    public static final String VIEW_ID="screen-view";
    
    private volatile JPanel panel;

    private final IEmulator emulator;
    
    private volatile DefaultScreen screen = null;    
    private volatile DefaultKeyboard keyboard = null;
    
    private volatile boolean debugCustomFonts = false;
    
    private final IEmulationOptionsProvider optionsProvider;
    
    private final IEmulationListener listener = new EmulationListener() {
        
        public void beforeEmulatorIsDisposed(IEmulator emulator) {
            System.out.println("ScreenView received beforeEmulatorIsDisposed()");
            detach();
        }
        
        public void afterMemoryLoad(IEmulator emulator, de.codesourcery.jasm16.Address startAddress, int lengthInBytes) {
            System.out.println("ScreenView received afterMemoryLoad()");
            detach();
            if ( panel != null ) {
                attach( panel );
            }            
        }
        
        @Override
        public void afterReset(IEmulator emulator)
        {
            System.out.println("ScreenView received afterReset()");
            detach();
            if ( panel != null ) {
                attach( panel );
            }
        }
    };
    
    public ScreenView(IEmulationOptionsProvider optionsProvider, IEmulator emulator) 
    {
        if (emulator == null) {
            throw new IllegalArgumentException("emulator must not be NULL.");
        }
        this.emulator = emulator;
        this.emulator.addEmulationListener( listener );
        this.optionsProvider = optionsProvider;
    }
    
    private void attach(final JPanel panel) 
    {
        detach();
        
        UIUtils.invokeAndWait( new Runnable() {

            @Override
            public void run()
            {
                keyboard = optionsProvider.getEmulationOptions().getKeyboard( emulator );
                screen = optionsProvider.getEmulationOptions().getScreen( emulator );
                keyboard.attach( panel );
                screen.attach( panel );                
            }} );
    }    
    
    private void detach() 
    {
        UIUtils.invokeAndWait( new Runnable() {

            @Override
            public void run()
            {        
                if ( screen != null ) {
                    screen.detach();
                    screen = null;
                }
                if ( keyboard != null ) {
                    keyboard.detach();
                    keyboard = null;
                }    
            }
        });
    }

    @Override
    public void disposeHook()
    {
        this.panel = null;
        detach();
        this.emulator.removeEmulationListener( listener );
    }

    public void setDebugCustomFonts(boolean debugCustomFonts)
    {
        this.debugCustomFonts = debugCustomFonts;
        if ( panel != null ) {
            panel.repaint();
        }
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
    		attach( panel );
    	}
    	return panel;
    }

    protected JPanel createPanel()
    {
        panel = new JPanel() 
        {
        	public void paint(java.awt.Graphics g) 
             {
        		super.paint(g);
        		
        		final int height;
        		if ( debugCustomFonts ) {
        		    height = getHeight() / 2;
        		} else {
        		    height = getHeight();
        		}
        		
        		if ( screen == null ) {
        		    return;
        		}
        		
        		final BufferedImage original1 = screen.getScreenImage();
        		if ( original1 == null ) {
        		    return;
        		}
        		
                final Graphics2D g2 = (Graphics2D) g;
                
        		final Image scaled = original1.getScaledInstance( getWidth() , height  , Image.SCALE_FAST );
                g2.drawImage(  scaled , 0,0, null );
        		
                if ( debugCustomFonts ) {
                    BufferedImage original2 = screen.getFontImage();
                    final Image scaled2 = original2.getScaledInstance( getWidth() , height , Image.SCALE_FAST );
                    ((Graphics2D) g).drawImage(  scaled2 , 0, height , null );
                }
             }        	
        };
        
        panel.setDoubleBuffered( true );
        panel.setPreferredSize( new Dimension(400,200 ) );
        panel.setSize( new Dimension(400,200 ) );       
        panel.setFocusable( true );
        setColors( panel );
        return panel;
    }
}