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
