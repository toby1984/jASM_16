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
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.viewcontainers.IViewContainer;

public abstract class AbstractView implements IView
{
    private static final Logger LOG = Logger.getLogger(AbstractView.class);
    
    public static final Color DEFAULT_TEXT_COLOR = Color.GREEN;
    public static final Color DEFAULT_BACKGROUND_COLOR = Color.BLACK;
    public static final Font DEFAULT_FONT=new Font( "Courier", Font.PLAIN , 13 );
    
	private static final AtomicLong ACTION_ID = new AtomicLong(0);    
    
    private volatile IViewContainer container;
    
    private final List<IView> children = new ArrayList<IView>(); 

    public final JPanel getPanel(IViewContainer container) {
        if (container == null) {
            throw new IllegalArgumentException("container must not be NULL.");
        }
    	this.container = container;
    	return getPanel();
    }
    
	protected final void addMenuEntry(JPopupMenu menu,String title, final ActionListener listener) 
	{
		final JMenuItem menuItem = new JMenuItem(title);
		menuItem.addActionListener( listener );
		menu.add(menuItem);		
	}
	
    @Override
    public final void dispose()
    {
        for (Iterator<IView> it = children.iterator(); it.hasNext();) {
            IView child = it.next();
            it.remove();
            try {
                child.dispose();
            } catch(Exception e) {
                LOG.error("dispose(): child view: "+child,e);
            }
        }
        disposeHook();
    }
    
    protected void disposeHook() {
    }
    
    public final IViewContainer getViewContainer() {
    	return container;
    }
    
    public final boolean hasViewContainer() {
        return container != null;
    }
    
    protected abstract JPanel getPanel();
    
    protected final Font getMonospacedFont() 
    {
        return DEFAULT_FONT;
    }
    
    protected final void addChild(IView child) {
        if (child == null) {
            throw new IllegalArgumentException("child must not be NULL.");
        }
        this.children.add( child );
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
    
    @Override
    public boolean mayBeDisposed() {
    	return true;
    }
    
	protected String addKeyBinding(JComponent editor , KeyStroke key , Action action) 
	{
		final String actionId = Long.toString( ACTION_ID.incrementAndGet() );
		editor.getInputMap().put( key , actionId );
		editor.getActionMap().put( actionId , action );
		return actionId;
	}
	
	protected int calculateVisibleTextRowCount(JTextComponent component) {
		
		final Dimension size = component.getSize();
		final FontMetrics fontMetrics = component.getFontMetrics( component.getFont() );
		final int height = fontMetrics.getHeight();
		return size.height / height;
	}
	
    protected String getTextAtLocation(JTextComponent textArea , int x,int y) 
    {
		final Point pt = new Point( x , y );
        final Position.Bias[] biasRet = new Position.Bias[1];
        final int pos = textArea.getUI().viewToModel(textArea, pt, biasRet);
		final int elementIndex = textArea.getDocument().getDefaultRootElement().getElementIndex( pos );
		
		final Element element = textArea.getDocument().getDefaultRootElement().getElement( elementIndex );
		if ( element != null ) {
			final int startOffset = element.getStartOffset();
			final int endOffset = element.getEndOffset();
			try {
				return textArea.getDocument().getText( startOffset , endOffset - startOffset );
			} 
			catch (BadLocationException e1) {
				e1.printStackTrace();
			}
		}
		return null;
    }
    
    protected final void executeAsynchronously(Runnable r) {
        UIUtils.executeAsynchronously( r );
    }
}
