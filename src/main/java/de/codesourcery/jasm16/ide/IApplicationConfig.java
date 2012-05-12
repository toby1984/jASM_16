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
package de.codesourcery.jasm16.ide;

import java.io.File;
import java.io.IOException;

import de.codesourcery.jasm16.ide.ui.utils.SizeAndLocation;
import de.codesourcery.jasm16.ide.ui.views.IView;

/**
 * Global IDE application configuration.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IApplicationConfig {

	public File getWorkspaceDirectory();
	
	public void setWorkspaceDirectory(File dir) throws IOException;

	void saveConfiguration() throws IOException;
	
	/**
	 * Stores view coordinates (position and size) for a given view ID.
	 * @param viewID
	 * @param loc
	 * @throws IOException 
	 * @see IView#getID()
	 */
	public void storeViewCoordinates(String viewID , SizeAndLocation loc);
	
	/**
	 * Returns the view coordinates (position and size) for a given view ID.
	 * 
	 * @param viewId
	 * @return
	 * @see IView#getID()
	 */
	public SizeAndLocation getViewCoordinates(String viewId);
}
