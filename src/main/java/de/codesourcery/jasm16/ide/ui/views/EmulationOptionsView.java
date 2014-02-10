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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.emulator.EmulationOptions;
import de.codesourcery.jasm16.emulator.EmulationOptions.InsertedDisk;
import de.codesourcery.jasm16.emulator.IEmulator.EmulationSpeed;

public abstract class EmulationOptionsView extends AbstractView {

	public static final String ID = "emulation_options";
	
	private JPanel panel;

	// general emulation properties
	private final JCheckBox box1 = new JCheckBox("Write-protected memory of executed instructions (slow) ?");
	private final JCheckBox box2 = new JCheckBox("Enable debug output ?");
	private final JCheckBox box3 = new JCheckBox("Ignore access to unused device slots ?");
	private final JCheckBox box8 = new JCheckBox("Halt on store to immediate value ?");	
	
	// keyboard emulation
	private final JCheckBox box4 = new JCheckBox("Use legacy key buffer ?");
	
	// video emulation
	private final JCheckBox box5 = new JCheckBox("Map video ram to 0x8000 on startup ?");
	private final JCheckBox box6 = new JCheckBox("Map font ram to 0x8180 on startup ?");

	// emulator options
	private JPanel emulatorPanel = new JPanel();
	private final DefaultComboBoxModel<EmulationSpeed> speedModel = new DefaultComboBoxModel<EmulationSpeed>(EmulationSpeed.values());
	private final JComboBox<EmulationSpeed> speedBox = new JComboBox<EmulationSpeed>(speedModel);	
	
	// disk drive
	private final JPanel diskDrivePanel = new JPanel();
	private final JCheckBox box7 = new JCheckBox("Run floppy emulation at max speed ?");
	private final JTextField selectedFileField = new JTextField();
	private final JButton fileChooserButton = new JButton("Choose image...");
	private final JCheckBox writeProtected = new JCheckBox("write-protected");
	
	public EmulationOptionsView() 
	{
		emulatorPanel.setLayout( new GridBagLayout() );
		
		GridBagConstraints cnstrs = constraints( 0, 0 , false, false , GridBagConstraints.NONE );
		emulatorPanel.add( new JLabel("Emulation speed"), cnstrs );
		
		cnstrs = constraints( 1, 0 , true , true , GridBagConstraints.NONE );
		cnstrs.anchor=GridBagConstraints.WEST;
		emulatorPanel.setBorder( BorderFactory.createTitledBorder("General options") );
		
		speedBox.setRenderer( new DefaultListCellRenderer() {
			
			public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) 
			{
				final java.awt.Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				
				if ( value != null ) {
					switch( (EmulationSpeed) value ) {
						case MAX_SPEED:
							setText("Max.");
							break;
						case REAL_SPEED:
							setText("100 kHz");
							break;
						default:
							setText( value.toString() );
							break;
					}
				}
				return result;
			};
		} );
		
		emulatorPanel.add( speedBox , cnstrs );
		
		// disk drive panel
		selectedFileField.setColumns( 25 );
		
		diskDrivePanel.setLayout( new GridBagLayout() );
		cnstrs = constraints( 0, 0 , false , true , GridBagConstraints.NONE );
		cnstrs.anchor = GridBagConstraints.CENTER;
		
		diskDrivePanel.setBorder( BorderFactory.createTitledBorder("Disk drive") );
		diskDrivePanel.add( selectedFileField , cnstrs );
		
		cnstrs = constraints( 1, 0 , false , true , GridBagConstraints.NONE );
		cnstrs.anchor = GridBagConstraints.CENTER;
        diskDrivePanel.add( fileChooserButton , cnstrs );	
        
        fileChooserButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                final JFileChooser chooser;
                if ( getSelectedFile() != null ) 
                {
                    chooser = new JFileChooser( getSelectedFile().getParentFile() );
                } else {
                    chooser = new JFileChooser();
                }
                final int result = chooser.showOpenDialog(null);
                if ( result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().isFile() ) 
                {
                    selectedFileField.setText( chooser.getSelectedFile().getAbsolutePath() );
                }
            }
        } );

        cnstrs = constraints( 2, 0 , false , true , GridBagConstraints.NONE );
        cnstrs.anchor = GridBagConstraints.CENTER;
        diskDrivePanel.add( writeProtected , cnstrs );        
	}
	
	public void setData(EmulationOptions options) 
	{
		if (options == null) {
			throw new IllegalArgumentException("options must not be null");
		}
		box1.setSelected( options.isMemoryProtectionEnabled() );
		box2.setSelected( options.isEnableDebugOutput() );
		box3.setSelected( options.isIgnoreAccessToUnknownDevices() );
		box4.setSelected( options.isUseLegacyKeyboardBuffer() );
		box8.setSelected( options.isCrashOnStoreWithImmediate() );
		
		// video emulation
		box5.setSelected( options.isMapVideoRamUponAddDevice() );
		box6.setSelected( options.isMapFontRamUponAddDevice() );
		
		// emulation speed panel
		speedModel.setSelectedItem( options.getEmulationSpeed() );
		box7.setSelected( options.isRunFloppyAtFullSpeed() ); // floppy emulation speed		
		
		final InsertedDisk disk = options.getInsertedDisk();
		if ( disk == null ) 
		{
		    selectedFileField.setText( null );
		    writeProtected.setSelected( false );
		} else {
	        selectedFileField.setText( disk.getFile().getAbsolutePath() );
	        writeProtected.setSelected( disk.isWriteProtected() );
		}		
	}
	
	@Override
	public final void refreshDisplay()
	{
	}
	
	protected abstract void onSave(EmulationOptions options);
	
	protected abstract void onCancel();

	@Override
	public final String getTitle() {
		return "Emulation options";
	}

	private void saveChangesTo(EmulationOptions options) 
	{
		options.setMemoryProtectionEnabled( box1.isSelected() );
		options.setEnableDebugOutput( box2.isSelected() );
		options.setIgnoreAccessToUnknownDevices( box3.isSelected() );
		options.setCrashOnStoreWithImmediate( box8.isSelected() );
		options.setUseLegacyKeyboardBuffer( box4.isSelected() );
		options.setMapVideoRamUponAddDevice( box5.isSelected() );
		options.setMapFontRamUponAddDevice( box6.isSelected() );
		options.setRunFloppyAtFullSpeed( box7.isSelected() );
		options.setEmulationSpeed( (EmulationSpeed) speedBox.getSelectedItem() );
		
		if ( getSelectedFile() != null ) {
		    options.setInsertedDisk( new InsertedDisk(getSelectedFile(),writeProtected.isSelected() ) );
		} else {
		    options.setInsertedDisk( null );
		}
	}
	
	private File getSelectedFile() {
	    final String path = selectedFileField.getText();
	    return StringUtils.isBlank( path ) ? null : new File(path);
	}
	
	@Override
	public final String getID() {
		return ID;
	}

	@Override
	protected final JPanel getPanel() {
		if ( panel == null ) {
			panel = createPanel();
		}
		return panel;
	}
	
	private JPanel createPanel() {
		
		final JPanel result = new JPanel();
		
		result.setLayout( new GridBagLayout() );

		// 'Memory protection' checkbox
		int y = 0;
		GridBagConstraints cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth=2;
		result.add( box1 , cnstrs );
		
		cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth=2;
		result.add( box2 , cnstrs );
		
		cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth=2;
		result.add( box3 , cnstrs );
		
		cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth=2;
		result.add( box8 , cnstrs );		
		
		cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth=2;
		result.add( box4 , cnstrs );
		
		cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth=2;
		result.add( box5 , cnstrs );
		
		cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth=2;
		result.add( box6 , cnstrs );
		
		cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth=2;
		result.add( box7 , cnstrs );		
		
        cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
        cnstrs.gridwidth=2;
        result.add( diskDrivePanel , cnstrs );   
        
        cnstrs = constraints( 0 , y++ , true , false , GridBagConstraints.HORIZONTAL );
        cnstrs.gridwidth=2;
        result.add( emulatorPanel , cnstrs );    	        
        
		// cancel button
		cnstrs = constraints( 0 , y , false , false , GridBagConstraints.NONE );
		cnstrs.weightx = 0.33;
		cnstrs.anchor = GridBagConstraints.CENTER;
		
		final JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) 
			{
				onCancel();
			}
		} );
		result.add( cancelButton , cnstrs );
		
		// save button
		cnstrs = constraints( 1 , y , true , false , GridBagConstraints.NONE );
		cnstrs.weightx = 0.33;
		cnstrs.anchor = GridBagConstraints.CENTER;
		
		final JButton saveButton = new JButton("Save");
		saveButton.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) 
			{
				final EmulationOptions options = new EmulationOptions();
				saveChangesTo( options );
				onSave( options );
			}
			
		} );
		result.add( saveButton , cnstrs );		

		return result;
	}
}