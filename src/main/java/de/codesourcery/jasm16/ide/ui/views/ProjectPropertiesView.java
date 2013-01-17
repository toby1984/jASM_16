package de.codesourcery.jasm16.ide.ui.views;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.ide.BuildOptions;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.ProjectConfiguration;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;

public abstract class ProjectPropertiesView extends AbstractView
{
    public static final String ID = "project_properties";
    
    private volatile IAssemblyProject project;
    
    private final JTextField projectName = new JTextField();
    
    private final JCheckBox generateSelfRelocatingCode = new JCheckBox();
    
    private final JButton saveButton = new JButton("Save");
    private final JButton cancelButton = new JButton("Cancel");
    
    public ProjectPropertiesView() {
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
        
        // build options
        cnstrs = constraints(0, y, false, false, GridBagConstraints.NONE );
        result.add( new JLabel("Generate self-relocating code?") , cnstrs );
        
        cnstrs = constraints(1, y++, true, true, GridBagConstraints.NONE );
        result.add( generateSelfRelocatingCode , cnstrs );
        
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