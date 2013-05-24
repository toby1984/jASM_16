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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.ide.BuildOptions;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.ProjectConfiguration;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;

public abstract class ProjectConfigurationView extends AbstractView
{
    public static final String ID = "project_properties";
    
    private volatile IAssemblyProject project;
    
    private final JTextField projectName = new JTextField();
    
    private final JTextField compilationRootName = new JTextField();  
    private final JButton compilationRootButton = new JButton("Choose");    
    
    private final JCheckBox inlineShortLiterals = new JCheckBox();    
    private final JCheckBox generateSelfRelocatingCode = new JCheckBox();
    
    private final JButton saveButton = new JButton("Save");
    private final JButton cancelButton = new JButton("Cancel");
    
    public ProjectConfigurationView() {
    }
    
    public void setProject(IAssemblyProject project)
    {
        if (project == null) {
            throw new IllegalArgumentException("project must not be NULL.");
        }
        this.project = project;
        refreshDisplay();
    }
    
    @Override
    public void refreshDisplay()
    {
        UIUtils.invokeLater( new Runnable() {

            @Override
            public void run()
            {
                if ( project == null ) {
                    return;
                }
                projectName.setText( project.getName() );
                generateSelfRelocatingCode.setSelected( project.getConfiguration().getBuildOptions().isGenerateSelfRelocatingCode() );
                inlineShortLiterals.setSelected( project.getConfiguration().getBuildOptions().isInlineShortLiterals() );
                final File f = project.getConfiguration().getCompilationRoot();
                compilationRootName.setText( f != null ? f.getAbsolutePath() : null );
            }
        });
    }
    
    protected boolean hasValidInput() {
        return StringUtils.isNotEmpty( projectName.getText() );
    }

    protected final void apply(ProjectConfiguration config) 
    {
        if ( hasValidInput() ) {
            final BuildOptions buildOptions = config.getBuildOptions();
            buildOptions.setGenerateSelfRelocatingCode( generateSelfRelocatingCode.isSelected() );
            buildOptions.setInlineShortLiterals( inlineShortLiterals.isSelected() );
            if ( StringUtils.isNotEmpty( compilationRootName.getText() ) ) {
                config.setCompilationRoot( new File(compilationRootName.getText()) );
            } else {
                config.setCompilationRoot(null);
            }
            config.setProjectName( projectName.getText() );
            config.setBuildOptions( buildOptions );
        }
    }
    
    @Override
    public String getTitle()
    {
        return "Project properties";
    }

    @Override
    public String getID()
    {
        return ID;
    }
    
    protected abstract void onSave();
    
    protected abstract void onCancel();

    @Override
    protected JPanel getPanel()
    {
        final JPanel result = new JPanel();
        result.setLayout( new GridBagLayout() );
        
        // project name
        int y = 0;
        GridBagConstraints cnstrs = constraints(0, y, false, false, GridBagConstraints.NONE );
        result.add( new JLabel("Project name") , cnstrs );
        
        cnstrs = constraints(1, y++, true, false, GridBagConstraints.NONE );
        result.add( projectName , cnstrs );
        
        // build options panel
        final JPanel buildOptionsPanel = new JPanel();
        buildOptionsPanel.setLayout( new GridBagLayout() );
        
        buildOptionsPanel.setBorder(BorderFactory.createTitledBorder("Build options" ) );
        
        cnstrs = constraints(0, 0, false, false, GridBagConstraints.NONE );
        buildOptionsPanel.add( new JLabel("Compilation root") , cnstrs );
        
        cnstrs = constraints(1, 0, false, false, GridBagConstraints.NONE );
        compilationRootName.setEditable( false );
        compilationRootName.setColumns( 25 );
        buildOptionsPanel.add( compilationRootName , cnstrs );
        
        cnstrs = constraints(2, 0, true, false, GridBagConstraints.NONE );
        buildOptionsPanel.add( compilationRootButton , cnstrs );    
        
        compilationRootButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                final JFileChooser chooser;
                File baseDir = null;
                if ( project != null ) {
                    baseDir = project.getConfiguration().getBaseDirectory();
                }
                if ( StringUtils.isNotBlank( compilationRootName.getText() ) ) 
                {
                    File tmp = new File(compilationRootName.getText()).getParentFile();
                    if ( tmp.exists() && tmp.isDirectory() ) {
                        baseDir = tmp;
                    }
                 }
                if ( baseDir != null ) 
                {
                    chooser = new JFileChooser( baseDir );
                } else {
                    chooser = new JFileChooser();
                }
                final int result = chooser.showOpenDialog(null);
                if ( result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile().isFile() ) 
                {
                    compilationRootName.setText( chooser.getSelectedFile().getAbsolutePath() );
                }
            }
        });
        
        // generate self-relocating code ?
        cnstrs = constraints(0, 1, false, false, GridBagConstraints.NONE );
        buildOptionsPanel.add( new JLabel("Generate self-relocating code?") , cnstrs );
        
        cnstrs = constraints(1, 1, true, true, GridBagConstraints.NONE );
        cnstrs.gridwidth=2;       
        buildOptionsPanel.add( generateSelfRelocatingCode , cnstrs );      
        
        // inline short literals ?
        cnstrs = constraints(0, 2, false, false, GridBagConstraints.NONE );
        buildOptionsPanel.add( new JLabel("Inline short literals?") , cnstrs );
        
        cnstrs = constraints(1, 2, true, true, GridBagConstraints.NONE );
        cnstrs.gridwidth=2;       
        buildOptionsPanel.add( inlineShortLiterals , cnstrs );               

        // add build options panel to parent
        cnstrs = constraints(0, y++, true, false , GridBagConstraints.BOTH);
        cnstrs.gridwidth=2;
        result.add( buildOptionsPanel, cnstrs );
        
        // buttons
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout( new GridBagLayout() );
        
        cnstrs = constraints(0, 0, false, false, GridBagConstraints.NONE );
        buttonPanel.add( saveButton, cnstrs );
        saveButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                if ( hasValidInput() ) {
                    onSave();
                }
            }
        });
        
        cnstrs = constraints(1, 0, true, true, GridBagConstraints.NONE );
        buttonPanel.add( cancelButton, cnstrs );    
        cancelButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                onCancel();
            }
        });        
        
        // button panel
        cnstrs = constraints(0, y++, true, true, GridBagConstraints.NONE );
        cnstrs.gridwidth = 2;
        result.add( buttonPanel , cnstrs );        
        
        return result;
    }
}