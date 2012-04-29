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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;

import javax.swing.JPanel;

import de.codesourcery.jasm16.ide.ui.viewcontainers.IViewContainer;

public abstract class AbstractView implements IView
{
    public static final Color DEFAULT_TEXT_COLOR = Color.GREEN;
    
    public static final Color DEFAULT_BACKGROUND_COLOR = Color.BLACK;
    
    public static final Font DEFAULT_FONT=new Font( "Courier", Font.PLAIN , 13 );
    
    private IViewContainer container;

    public final JPanel getPanel(IViewContainer container) {
    	this.container = container;
    	return getPanel();
    }
    
    protected final IViewContainer getViewContainer() {
    	return container;
    }
    
    protected abstract JPanel getPanel();
    
    protected final Font getMonospacedFont() 
    {
        return DEFAULT_FONT;
    }
    
    protected final int getMinimumWidth() {
        return 500;
    }
    
    protected final int getMinimumHeight() {
        return 200;
    }    
    
    protected final Dimension getMinimumSize() {
        return new Dimension( getMinimumWidth() , getMinimumHeight() );
    }        
    
    protected final Color getBackgroundColor() {
        return DEFAULT_BACKGROUND_COLOR;
    }
    
    protected final Color getTextColor() {
        return DEFAULT_TEXT_COLOR;
    }    
    
    protected final void setColors(Component comp) {
        comp.setBackground( getBackgroundColor() );
        comp.setForeground( getTextColor() );
    }
    
    protected final GridBagConstraints constraints(int x,int y,boolean remainderHoriz,boolean remainderVert,int fill) {
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;
        cnstrs.weightx=1.0d;
        cnstrs.weighty =1.0d;
        cnstrs.fill = fill;
        cnstrs.gridheight = remainderVert ? GridBagConstraints.REMAINDER : 1;
        cnstrs.gridwidth = remainderHoriz ? GridBagConstraints.REMAINDER : 1;
        cnstrs.gridx=x;
        cnstrs.gridy=y;
        return cnstrs;
    }
}
