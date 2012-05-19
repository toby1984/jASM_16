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

import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultKeyboard;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultScreen;
import de.codesourcery.jasm16.ide.EmulatorFactory;

public class ScreenView extends AbstractView
{
    public static final String VIEW_ID="screen-view";
    
    private volatile JPanel panel;

    private volatile DefaultScreen screen = null;
    private final IEmulator emulator;
    private final DefaultKeyboard keyboard;
    private final EmulatorFactory emulatorFactory;
    
    public ScreenView(EmulatorFactory emulatorFactory , IEmulator emulator) 
    {
        if (emulator == null) {
            throw new IllegalArgumentException("emulator must not be NULL.");
        }
        if ( emulatorFactory == null ) {
			throw new IllegalArgumentException("emulatorFactory must not be null");
		}
        this.emulator = emulator;
        this.emulatorFactory = emulatorFactory;
        this.keyboard = emulatorFactory.createKeyboardDevice();
		this.emulator.addDevice( keyboard );
    }
    
    
    public void disposeHook()
    {
    	if ( screen != null ) {
    		this.emulator.removeDevice( screen );
    		this.screen = null;
    	}
        this.emulator.removeDevice( keyboard );
    }

    
    public void refreshDisplay()
    {
    	if ( panel != null ) {
    		panel.repaint();
    	}
    }

    
    public String getTitle()
    {
        return "Screen";
    }

    
    public String getID()
    {
        return VIEW_ID;
    }
    
    
    protected JPanel getPanel()
    {
    	if ( panel == null ) 
    	{
    		panel=createPanel();
    		screen = emulatorFactory.createScreenDevice();
    		this.emulator.addDevice( screen );
    		screen.attach( panel );
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
        		super.paint(g);
        		BufferedImage original = screen.getScreenImage();
        		final Image scaled = original.getScaledInstance( getWidth() , getHeight() , Image.SCALE_FAST );
                ((Graphics2D) g).drawImage(  scaled , 0,0, null );
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