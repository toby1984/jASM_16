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

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.ide.IAssemblyProject;

public class UIUtils {

	public static enum DialogResult {
		YES,
		NO,
		CANCEL;
	}

	public static DialogResult showConfirmationDialog(Component parent, String title,String message) {

		final Object[] options = {"Yes","No","Cancel"};
		final int outcome = JOptionPane.showOptionDialog(parent,message,
						title,
						JOptionPane.YES_NO_CANCEL_OPTION,
						JOptionPane.QUESTION_MESSAGE,
						null,
						options,
						options[2]);
		switch( outcome ) {
		case 0:
			return DialogResult.YES;
		case 1:
			return DialogResult.NO;
		case 2:
		case JOptionPane.CLOSED_OPTION:
			return DialogResult.CANCEL;
		default:
			throw new RuntimeException("Internal error, unexpected outcome "+outcome);
		}
	}
	
	/**
	 * 
	 * @param parent
	 * @param title
	 * @param message
	 * @return <code>true</code> if project should also be physically deleted,
	 * <code>false</code> is project should be deleted but all files should be left alone,
	 * <code>null</code> if user cancelled the dialog/project should not be deleted
	 */
	public static Boolean showDeleteProjectDialog(IAssemblyProject project) 
	{
		final JDialog dialog = new JDialog( (Window) null, "Delete project "+project.getName());
		
		dialog.setModal( true );
		
		final JTextArea message = createMultiLineLabel( "Do you really want to delete project '"+project.getName()+" ?");
		final JCheckBox checkbox = new JCheckBox("Delete project files");

		final DialogResult[] outcome = { DialogResult.CANCEL };
		
		final JButton yesButton = new JButton("Yes");
		yesButton.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				outcome[0] = DialogResult.YES;
				dialog.dispose();
			}
		});
		
		final JButton noButton = new JButton("No");
		noButton.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				outcome[0] = DialogResult.NO;
				dialog.dispose();
			}
		});		
		final JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				outcome[0] = DialogResult.CANCEL;
				dialog.dispose();
			}
		});			

		final JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout( new FlowLayout() );
		buttonPanel.add( yesButton );
		buttonPanel.add( noButton );
		buttonPanel.add( cancelButton );
		
		final JPanel messagePanel = new JPanel();
		messagePanel.setLayout( new GridBagLayout() );
		
		GridBagConstraints cnstrs = constraints(0,0,true,false, GridBagConstraints.NONE );
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.weighty=0;
		cnstrs.gridheight = 1;
		messagePanel.add( message , cnstrs );
		
		cnstrs = constraints(0,1,true,true, GridBagConstraints.NONE );		
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.gridheight = 1;
		cnstrs.weighty=0;
		messagePanel.add( checkbox , cnstrs );		
		
		final JPanel panel = new JPanel();
		panel.setLayout( new GridBagLayout() );
		
		cnstrs = constraints(0,0,true,false , GridBagConstraints.NONE );	
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.gridheight = 1;
		cnstrs.weighty=0;
        cnstrs.insets = new Insets(5,2,5,2); // top,left,bottom,right			
		panel.add( messagePanel , cnstrs );
		
		cnstrs = constraints(0,1,true,true, GridBagConstraints.HORIZONTAL );	
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.gridheight = 1;
		cnstrs.weighty=0;
        cnstrs.insets = new Insets(0,2,10,2); // top,left,bottom,right		
		panel.add( buttonPanel , cnstrs );
		
		dialog.getContentPane().add( panel );
		dialog.pack();
		dialog.setVisible( true );
		
		if ( outcome[0] != DialogResult.YES ) {
			return null;
		}
		return checkbox.isSelected();
	}	
	
	public static JDialog createMessageDialog(Window parent,String title,String msg) {

		final JDialog dialog = new JDialog( parent , title );
		
		final JTextArea message = createMultiLineLabel( msg );

		final JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout( new FlowLayout() );
		
		final JPanel messagePanel = new JPanel();
		messagePanel.setLayout( new GridBagLayout() );
		
		GridBagConstraints cnstrs = constraints(0,0,true,false, GridBagConstraints.NONE );
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.weighty=0;
		cnstrs.gridheight = 1;
		messagePanel.add( message , cnstrs );
		
		final JPanel panel = new JPanel();
		panel.setLayout( new GridBagLayout() );
		
		cnstrs = constraints(0,0,true,false , GridBagConstraints.NONE );	
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.gridheight = 1;
		cnstrs.weighty=0;
        cnstrs.insets = new Insets(5,2,5,2); // top,left,bottom,right			
		panel.add( messagePanel , cnstrs );
		
		cnstrs = constraints(0,1,true,true, GridBagConstraints.HORIZONTAL );	
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.gridheight = 1;
		cnstrs.weighty=0;
        cnstrs.insets = new Insets(0,2,10,2); // top,left,bottom,right		
		panel.add( buttonPanel , cnstrs );
		
		dialog.getContentPane().add( panel );
		dialog.pack();
		return dialog;
	}
	
	public static String showInputDialog(Component parent,String title,String message) {
		Object[] possibilities = null;
		String s = (String) JOptionPane.showInputDialog(
		                    parent,
		                    message,
		                    title,
		                    JOptionPane.PLAIN_MESSAGE,
		                    null,
		                    possibilities,
		                    null);

		return StringUtils.isNotBlank( s ) ? s : null;
	}
	
	public static void showErrorDialog(Component parent,String title,String message) {
		JOptionPane.showMessageDialog(parent,
			    message,
			    title,
			    JOptionPane.ERROR_MESSAGE);		
	}

    protected static final GridBagConstraints constraints(int x,int y,boolean remainderHoriz,boolean remainderVert,int fill) {
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;
        cnstrs.weightx=1.0d;
        cnstrs.weighty =1.0d;
        cnstrs.fill = fill;
        cnstrs.gridheight = remainderVert ? GridBagConstraints.REMAINDER : 1;
        cnstrs.gridwidth = remainderHoriz ? GridBagConstraints.REMAINDER : 1;
        cnstrs.gridx=x;
        cnstrs.gridy=y;
        cnstrs.insets = new Insets(10,2,10,2); // top,left,bottom,right
        return cnstrs;
    }
    
    public static JTextArea createMultiLineLabel(String text) {
    	return new TextNote( text );
    }
    
    public static final class TextNote extends JTextArea 
    {
        public TextNote(String text) {
            super(text);
            setColumns( text.length() );
            setBackground(null);
            setEditable(false);
            setBorder(null);
            setLineWrap(true);
            setWrapStyleWord(true);
            setFocusable(false);
        }
    }
    
}
