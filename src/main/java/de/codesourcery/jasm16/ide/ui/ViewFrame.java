package de.codesourcery.jasm16.ide.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class ViewFrame extends JFrame
{
    @SuppressWarnings("unused")
    private final IView component;
    
    public ViewFrame(String title,final IView component) 
    {
        super(title);
        if ( component == null ) {
            throw new IllegalArgumentException("component must not be NULL.");
        }
        this.component = component;
        
        addWindowListener( new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e)
            {
                component.dispose();                
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
        panel.add( component.getPanel() , cnstrs );
        getContentPane().add( panel );
        pack();
    }
    
}
