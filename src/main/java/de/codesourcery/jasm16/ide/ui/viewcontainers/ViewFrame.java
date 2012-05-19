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
package de.codesourcery.jasm16.ide.ui.viewcontainers;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.ide.ui.MenuManager;
import de.codesourcery.jasm16.ide.ui.views.IView;

/**
 * A view container that inherits from {@link JFrame} and 
 * holds at most a single view.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ViewFrame extends JFrame implements IViewContainer
{
    private final IView component;
    
    private final ViewContainerHelper helper = new ViewContainerHelper();
    
    private final MenuManager menuManager = new MenuManager() {

		
		public void menuBarChanged() 
		{
			setJMenuBar( menuManager.getMenuBar() );
		}
	};
    
    private JMenuBar menuBar;
    
    public ViewFrame(String title,final IView component) 
    {
        super(title);
        if ( component == null ) {
            throw new IllegalArgumentException("component must not be NULL.");
        }
        this.component = component;
        
        addWindowListener( new WindowAdapter() {

            
            public void windowClosing(WindowEvent e)
            {
            	disposeView( component );
                helper.fireViewContainerClosed( ViewFrame.this );
            } 
        } );
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        final JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );
        
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx=1.0d;
        cnstrs.weighty =1.0d;
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.gridheight = GridBagConstraints.REMAINDER;
        cnstrs.gridwidth = GridBagConstraints.REMAINDER;
        cnstrs.gridx=0;
        cnstrs.gridy=0;
        panel.add( component.getPanel(this) , cnstrs );
        getContentPane().add( panel );
        pack();
    }

	
	public void disposeView(IView view) 
	{
		if ( view == component ) {
			component.dispose();
			dispose();
		}
	}

	
	public void addView(IView view) {
		throw new UnsupportedOperationException("showView()");
	}

	
	public List<IView> getViews() {
		return Collections.singletonList( component );
	}

	
	public void setTitle(IView view, String title) {
		setTitle( title );
	}

	
	public IView getViewByID(String viewId) 
	{
		if (StringUtils.isBlank(viewId)) {
			throw new IllegalArgumentException("viewId must not be blank/null");
		}
		
		if ( component == null ) {
			return null;
		}
		return component.getID().equals( viewId ) ? component : null;
	}

	
	public MenuManager getMenuManager() 
	{
		if ( menuBar == null ) {
			menuBar = menuManager.getMenuBar();
			setJMenuBar( menuBar );
		}
		return menuManager;
	}

    
    public String getID()
    {
        return component.getID();
    }

    
    public void addViewContainerListener(IViewContainerListener listener)
    {
        helper.addViewContainerListener( listener );
    }

    
    public void removeViewContainerListener(IViewContainerListener listener)
    {
        helper.removeViewContainerListener( listener );
    }
}
