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

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.compiler.io.DefaultResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.ide.*;
import de.codesourcery.jasm16.ide.ui.MenuManager;
import de.codesourcery.jasm16.ide.ui.MenuManager.MenuEntry;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.views.*;

public class EditorContainer extends AbstractView implements IViewContainer , IResourceResolver {

	public static final String VIEW_ID = "editor-container";
	private JPanel panel;
	private final String title;
	
	private final ViewContainerHelper helper = new ViewContainerHelper();
	private final EditorFactory editorFactory;
	private final NavigationHistory navigationHistory = new NavigationHistory();
	
	private final IWorkspace workspace;
	private final List<ViewWithPanel> views = new ArrayList<ViewWithPanel>();
	private final JTabbedPane tabbedPane = new JTabbedPane();
	
	private final ChangeListener changeListener = new ChangeListener() {

	    private int previouslySelectedTab = -1;
	    
        @Override
        public void stateChanged(ChangeEvent e)
        {
            final int newIndex = tabbedPane.getSelectedIndex();
            int oldIndex = previouslySelectedTab;
            previouslySelectedTab = newIndex;
            
            if ( oldIndex != newIndex ) { // selected index changed
                
                if ( oldIndex != -1 ) {
                    getViewWithPanelForTabIndex( oldIndex ).tabDeselected();
                }
                getViewWithPanelForTabIndex( newIndex ).tabSelected();
            }
        }
    };
    
	private MenuEntry saveCurrent = new MenuEntry("File/Save") {

		@Override
		public void onClick() {
			System.out.println("Save current editor contents");
		}
		
		public boolean isVisible() {
			return ! mayBeDisposed();
		};
	};	
	
	protected final class ViewWithPanel 
	{
	    public int tabIndex;
	    
		public final IView view;
		public final JPanel panel;
		
		public ViewWithPanel(IView view,int tabIndex) 
		{
			this.view = view;
			this.tabIndex = tabIndex;
			this.panel = view.getPanel( EditorContainer.this );
		}
		
		public void tabSelected()
        {
		    if ( view instanceof IViewStateListener) {
		        ((IViewStateListener) view).viewVisible();
		    }
        }
		
		public void tabDeselected()
		{
		    if ( view instanceof IViewStateListener) {
		        ((IViewStateListener) view).viewHidden();
		    }
		}

        public void toFront() {
		    tabbedPane.setSelectedIndex( tabIndex );
		}
	}
	
	public EditorContainer(String title, IWorkspace workspace,IViewContainer parent,EditorFactory editorFactory) {
		this.title = title;
		this.workspace = workspace;
		this.editorFactory = editorFactory;
	}
	
	@Override
	protected JPanel getPanel() {
		if ( panel == null ) {
			panel = createPanel();
		}
		return panel;
	}
	
    @Override
    public void setBlockAllUserInput(boolean yesNo) 
    {
        if ( panel != null ) 
        {
            Container parent = panel.getParent();
            while( parent != null && !(parent instanceof JFrame ) ) {
                parent = parent.getParent();
            }
            if ( parent != null && parent instanceof JFrame ) {
                UIUtils.setBlockAllUserInput( (JFrame) parent , yesNo );                
            }
        }
    }	
	
	@Override
	public void toFront(IView view) {
	}

	private JPanel createPanel() 
	{
		final JPanel result = new JPanel();
		result.setLayout( new GridBagLayout() );
		
		GridBagConstraints cnstrs = constraints(0 , 0 , true , true , GridBagConstraints.BOTH );
		
		setColors( result );
		tabbedPane.setBackground( Color.WHITE );
		tabbedPane.setForeground( Color.black );
		result.add( tabbedPane ,cnstrs );
		
		if ( getViewContainer().getMenuManager() != null ) {
			getViewContainer().getMenuManager().addEntry( saveCurrent );
		}
		
		tabbedPane.addChangeListener( changeListener );
		
		tabbedPane.addKeyListener( new KeyAdapter() 
        {
            public void keyReleased(KeyEvent e) {
                if ( e.getKeyCode() == KeyEvent.VK_W && ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) != 0 ) 
                {
                    int idx = tabbedPane.getSelectedIndex();
                    if ( idx != -1 ) {
                        disposeView( getViewForTabIndex( idx ) );
                    }
                }
            }
        } );		
		return result;
	}
	
    public static final void addEditorCloseKeyListener(Component comp,final IEditorView view) 
    {
        comp.addKeyListener( new KeyAdapter() 
        {
            public void keyReleased(KeyEvent e) 
            {
                if ( e.getKeyCode() == KeyEvent.VK_W && ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) != 0 ) 
                {
                	System.out.println("*** Closing editor "+view+" ***");
                    if ( view.hasViewContainer() ) {
                        view.getViewContainer().disposeView( view );
                    } else {
                        view.dispose();
                    }
                }
            }
        } );              
    }	
	
	@Override
	public IView addView(IView view) 
	{
	    internalAddView(view);
	    return view;
	}
	
    private ViewWithPanel internalAddView(IView view) 
    {
        final int index = tabbedPane.getTabCount();
        final ViewWithPanel newView = new ViewWithPanel( view , index );
        views.add( newView );
        tabbedPane.add( view.getTitle() , newView.panel );
        return newView;
    }	
	
	protected void selectTab(IView view) 
	{
	    for ( ViewWithPanel v : views ) {
	        if ( v.view == view ) {
	            tabbedPane.setSelectedIndex( v.tabIndex );
	            return;
	        }
	    }
	}
	
    protected ViewWithPanel getViewWithPanelForTabIndex(int tabIndex) 
    {
       for ( ViewWithPanel v : views ) 
       {
            if ( v.tabIndex == tabIndex ) {
                return v;
            }
        }
       throw new IllegalArgumentException("Invalid tab index: "+tabIndex);
    }	
	
	protected IView getViewForTabIndex(int tabIndex) 
	{
	    return getViewWithPanelForTabIndex( tabIndex ).view;
	}
	
	@Override
	public void setTitle(IView view, String title) 
	{
		for ( ViewWithPanel p : this.views ) 
		{
			if ( p.view == view ) {
				final int index = tabbedPane.indexOfComponent( p.panel );
				if ( index != -1 ) {
					tabbedPane.setTitleAt( index , title );
				}
				break;
			}
		}
	}

	@Override
	public void disposeHook() 
	{
		final List<ViewWithPanel> copy = new ArrayList<ViewWithPanel>(this.views);
		for ( ViewWithPanel v : copy ) {
			v.view.dispose();
		}
		this.views.clear();
		
		if ( getViewContainer().getMenuManager() != null ) {
			getViewContainer().getMenuManager().removeEntry( saveCurrent );
		}
		
		helper.fireViewContainerClosed( this );
	}

	@Override
	public List<IView> getViews() 
	{
		final List<IView> result = new ArrayList<IView>();
		for ( ViewWithPanel p : this.views ) {
			result.add( p.view );
		}
		return result;
	}

	@Override
	public void disposeView(IView view) 
	{
		if (view == null) {
			throw new IllegalArgumentException("view must not be NULL");
		}
		
		int disposedTabIndex = -1;
		final List<ViewWithPanel> copy = new ArrayList<ViewWithPanel>(this.views);
		for (Iterator<ViewWithPanel> it = copy.iterator(); it.hasNext();) 
		{
			final ViewWithPanel viewWithPanel = it.next();
			if ( viewWithPanel.view == view ) {
			    this.views.remove( viewWithPanel );
			    disposedTabIndex = viewWithPanel.tabIndex;
				this.tabbedPane.remove( viewWithPanel.panel );
				viewWithPanel.view.dispose();
				break;
			}
		}
		
		if ( disposedTabIndex != -1 ) 
		{
		    // adjust tab indices
            int previousTabToFocus = -1;		    
		    int nextTabToFocus = -1;
		    for ( ViewWithPanel v : this.views ) 
		    {
		        if ( v.tabIndex >= disposedTabIndex ) {
		            v.tabIndex--;
		            if ( nextTabToFocus == -1 ) {
		                nextTabToFocus = v.tabIndex;
		            }
		        } else {
		            previousTabToFocus = v.tabIndex;
		        }
		    }
		    
		    // focus next/previous tab
		    if ( nextTabToFocus != -1 ) {
		        tabbedPane.setSelectedIndex( nextTabToFocus );
		    } else if ( previousTabToFocus != -1 ) {
	            tabbedPane.setSelectedIndex( previousTabToFocus );
		    }
		}
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public void refreshDisplay() 
	{
		for ( ViewWithPanel p : this.views ) {
			p.view.refreshDisplay();
		}
	}

	public IEditorView getEditor(IResource resource) 
	{
		if (resource == null) {
			throw new IllegalArgumentException("resource must not be NULL");
		}
		
		for ( ViewWithPanel p : this.views ) 
		{
			if ( p.view instanceof IEditorView) 
			{
				if ( DefaultResourceMatcher.INSTANCE.isSame( ((IEditorView) p.view).getCurrentResource() , resource ) ) {
					return (IEditorView) p.view;
				}
			}
		}
		return null;
	}

	@Override
	public boolean mayBeDisposed() 
	{
		boolean result = false;
		for ( ViewWithPanel p : this.views ) 
		{
			if ( p.view instanceof IEditorView) {
				result |= ((IEditorView) p.view).hasUnsavedContent();
			}
		}
		return result;
	}

	@Override
	public String getID() {
		return VIEW_ID;
	}

	@Override
	public IView getViewByID(String viewId) 
	{
		if (StringUtils.isBlank(viewId)) {
			throw new IllegalArgumentException("viewId must not be blank/null");
		}
		
		for ( ViewWithPanel p : this.views ) 
		{
			if ( p.view.getID().equals( viewId ) ) {
				return p.view;
			}
		}
		return null;
	}

	@Override
	public MenuManager getMenuManager() {
		return null;
	}

    @Override
    public void addViewContainerListener(IViewContainerListener listener)
    {
        helper.addViewContainerListener( listener );
    }

    @Override
    public void removeViewContainerListener(IViewContainerListener listener)
    {
        helper.removeViewContainerListener( listener );
    }
    
    public IEditorView openResource(IWorkspace workspace , IAssemblyProject project,IResource resource,int caretPosition) throws IOException 
    {
        IEditorView editor = getEditor( resource );
        if ( editor != null ) {
            editor.refreshDisplay();
            selectTab( editor );
            return editor;
        }
        
        editor = editorFactory.createEditor( project , resource , this , navigationHistory );
        
        final ViewWithPanel viewWithPanel = internalAddView( editor );
        
        tabbedPane.setSelectedIndex( viewWithPanel.tabIndex );
        
        // open resource AFTER IView has been added to this container,
        // view may rely on methods of this container
        editor.openResource( project , resource , caretPosition );
        return editor;
    }

    private List<SourceCodeView> getSourceCodeViews() {
    	List<SourceCodeView> result = new ArrayList<SourceCodeView>();
    	for ( ViewWithPanel view : this.views ) {
    		if ( view.view instanceof SourceCodeView) {
    			result.add( (SourceCodeView) view.view );
    		}
    	}
    	return result;
    }
    
	@Override
	public IResource resolve(String identifier) throws ResourceNotFoundException 
	{
        IResource result = tryResolve(identifier);
        if ( result == null ) {
            throw new ResourceNotFoundException("Failed to find resource '"+identifier+"'",identifier);
        }	    
        return result;
	}
	
	private IResource tryResolve(String identifier) 
	{
       for ( SourceCodeView v : getSourceCodeViews() ) {
            if ( v.getSourceFromMemory().getIdentifier().equals( identifier ) ) {
                return v.getSourceFromMemory();
            }
        }   
       return null;
	}
	
	@Override
	public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException 
	{
	    IResource result = tryResolve(identifier);
	    if ( result == null ) {
	        throw new ResourceNotFoundException("Failed to find resource '"+identifier+"'",identifier);
	    }
	    return result;
	}
}