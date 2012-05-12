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
package de.codesourcery.jasm16.ide.ui.utils;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public abstract class PagingKeyAdapter extends KeyAdapter 
{
        public void keyPressed(java.awt.event.KeyEvent e) 
        {
            switch( e.getKeyCode() ) 
            {
                case KeyEvent.VK_PAGE_DOWN:
                    onePageDown();
                    break;    
                case KeyEvent.VK_PAGE_UP:
                    onePageUp();
                    break;                         
                case KeyEvent.VK_DOWN:
                case KeyEvent.VK_KP_DOWN:
                    oneLineDown();
                    break;
                case KeyEvent.VK_UP:
                case KeyEvent.VK_KP_UP:
                    oneLineUp();
                    break;                        
            }
        }

		protected abstract void oneLineUp();
		protected abstract void oneLineDown();
		protected abstract void onePageUp();
		protected abstract void onePageDown();
}
