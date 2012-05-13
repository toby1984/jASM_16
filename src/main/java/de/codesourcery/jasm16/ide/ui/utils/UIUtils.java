package de.codesourcery.jasm16.ide.ui.utils;

import java.awt.Component;

import javax.swing.JOptionPane;

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
}
