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

import java.util.List;

import de.codesourcery.jasm16.ide.ui.MenuManager;
import de.codesourcery.jasm16.ide.ui.views.IView;

/**
 * A view container is a top-level component that may contain
 * one or multiple {@link IView}s.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IViewContainer {
    
    public void addViewContainerListener(IViewContainerListener listener);
    
    public void removeViewContainerListener(IViewContainerListener listener);

	/**
	 * Dispose this container with all view's 
	 * that are currently part of it.
	 * 
	 * <p>Invoking this method invokes
	 * <code>dispose()</code> on all child views.
	 * </p>
	 * @see IView#dispose()
	 */
	public void dispose();
	
	/**
	 * Brings a view to front.
	 * 
	 * @param view
	 */
	public void toFront(IView view);
	
	public void setBlockAllUserInput(boolean yesNo);
	
	/**
	 * Adds a view to this container.
	 * 
	 * @param view
	 * @return the input view (for method chaining)
	 */
	public IView addView(IView view);
	
	/**
	 * Removes a view from this container.
	 * 
	 * @param view
	 */
	public void disposeView(IView view);
	
	/**
	 * Sets the title for a given view (if this view container supports displaying
	 * titles for views).
	 * 
	 * <p>If this view container does not display titles for views, this
	 * method does nothing.</p>
	 *  
	 * @param view
	 * @param title
	 */
	public void setTitle(IView view,String title);

	/**
	 * Returns all views that are currently
	 * children of this container.
	 * 
	 * @return
	 */
	public List<IView> getViews();
	
	/**
	 * Returns the view with the given ID.
	 * 
	 * @param viewId
	 * @return view with the given ID or <code>null</code> if this container
	 * does not contain the specified view 
	 * @see IView#getID()
	 */
	public IView getViewByID(String viewId);
	
	/**
	 * Returns this container's menu manager or <code>null</code> if this
	 * container does not support menues.
	 * 
	 * @return
	 */
	public MenuManager getMenuManager();
	
    /**
     * Returns an immutable ID that identifies this view container.
     * 
     * <p>Note that other code RELIES on the fact
     * that the ID returned by this method never changes!</p> 
     * @return
	 */
	public String getID();
}
