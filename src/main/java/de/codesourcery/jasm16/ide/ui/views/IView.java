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

import javax.swing.JPanel;

import de.codesourcery.jasm16.ide.ui.viewcontainers.IViewContainer;

public interface IView
{
    public IViewContainer getViewContainer();
    
    public boolean hasViewContainer();
    
    public JPanel getPanel(IViewContainer container);
    
    public boolean mayBeDisposed();
    
    public void dispose();
    
    public void refreshDisplay();
    
    public String getTitle();
    
    /**
     * Returns an immutable ID that identifies this view.
     * 
     * <p>Note that other code RELIES on the fact
     * that the ID returned by this method never changes!</p> 
     * @return
     */
    public String getID();
}
