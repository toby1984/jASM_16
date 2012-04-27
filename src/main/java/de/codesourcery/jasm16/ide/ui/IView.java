package de.codesourcery.jasm16.ide.ui;

import javax.swing.JPanel;

public interface IView
{
    public JPanel getPanel();
    
    public void dispose();
    
    public void refreshDisplay();
}
