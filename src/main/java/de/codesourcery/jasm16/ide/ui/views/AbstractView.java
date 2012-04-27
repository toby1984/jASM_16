package de.codesourcery.jasm16.ide.ui.views;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;

import de.codesourcery.jasm16.ide.ui.IView;

public abstract class AbstractView implements IView
{
    public static final Color DEFAULT_TEXT_COLOR = Color.GREEN;
    
    public static final Color DEFAULT_BACKGROUND_COLOR = Color.BLACK;
    
    public static final Font DEFAULT_FONT=new Font( "Courier", Font.PLAIN , 13 );
    
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
