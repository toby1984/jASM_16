package de.codesourcery.jasm16.ide;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.ByteArrayObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.StringResource;
import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.ide.ui.views.AbstractView;
import de.codesourcery.jasm16.ide.ui.views.CPUView;
import de.codesourcery.jasm16.ide.ui.views.DisassemblerView;
import de.codesourcery.jasm16.ide.ui.views.HexDumpView;
import de.codesourcery.jasm16.ide.ui.views.SourceEditorView;
import de.codesourcery.jasm16.ide.ui.views.StackView;
import de.codesourcery.jasm16.utils.Misc;

public class IDEMain
{
    private final JFrame frame = new JFrame("jASM16 DCPU emulator V"+de.codesourcery.jasm16.compiler.Compiler.getVersionNumber() );

    private final IEmulator emulator = new Emulator();
    
    private final ICompiler compiler = new de.codesourcery.jasm16.compiler.Compiler();
    
    private final HexDumpView memoryDumpView = new HexDumpView( emulator );
    private final CPUView cpuView = new CPUView( emulator );    
    private final DisassemblerView disassemblerView = new DisassemblerView( emulator );        
    private final StackView stackView = new StackView( emulator );
    
    private final IWorkspace workspace = new DefaultWorkspace();
    private final SourceEditorView sourceEditorView = new SourceEditorView(this,workspace);
    
    private IAssemblerProject currentProject;
    private ICompilationUnit currentCompilationUnit;
    
    private final IWorkspaceListener workspaceListener = new IWorkspaceListener() {

        @Override
        public void resourceChanged(IResource resource)
        {
            if ( currentProject == null ) {
                return;
            }
            for ( IResource r : currentProject.getAllResources() ) 
            {
                if ( r.getIdentifier().equals( resource.getIdentifier() ) ) {
                    resetEmulator(true);
                }
            }
        }
     };
    
    public static void main(String[] args)
    {
        new IDEMain().run();
    }
    
    public IDEMain() 
    {
        this.workspace.addWorkspaceListener( workspaceListener );
    }
    
    public ICompiler getCompiler() {
        return compiler;
    }
    
    public JFrame getMainApplicationFrame() {
        return frame;
    }

    private void run()
    {
        // setup dummy project
        setupUnnamedProject();
        setupCompiler();
        setupUI();        
        
        SwingUtilities.invokeLater( new Runnable() {

            @Override
            public void run()
            {
                frame.pack();
                frame.setVisible( true );
                
                try {
                    sourceEditorView.showCompilationUnit( currentCompilationUnit );
                } 
                catch (IOException e) {
                    e.printStackTrace();
                }
                
                resetEmulator(true);    
                
                sourceEditorView.refreshDisplay();
                memoryDumpView.refreshDisplay();
                cpuView.refreshDisplay();     
                disassemblerView.refreshDisplay();
                stackView.refreshDisplay();                
            }
            
        } );
    }
    
    private void setupUnnamedProject()
    {
        currentProject = workspace.createNewProject("unnamed project");
        
        final String source = ":label SET a,1\n"+
                "       ADD b ,1\n"+
                "       ADD [stuff],1\n"+
                "       SET c , [stuff]\n"+
                "       SET PC,label\n"+
                ":stuff .dat 0x000";
        
        final IResource file = new StringResource("file1" , source );
        currentProject.registerResource( file );
        
        final List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
        for ( IResource resource : currentProject.getSourceFiles() ) 
        {
            units.add( CompilationUnit.createInstance( resource.getIdentifier() , resource ) );
        }
        this.currentCompilationUnit = units.isEmpty() ? null : units.get(0);
        currentProject.setCompilationUnits( units );
    }

    private void setupCompiler()
    {
        compiler.setResourceResolver( currentProject );
        compiler.setCompilerOption( CompilerOption.DEBUG_MODE, true );        
        compiler.setCompilerOption( CompilerOption.RELAXED_PARSING , true );        
    }

    public void resetEmulator(boolean clearMemory) 
    {
        emulator.reset( clearMemory );
        
        if ( clearMemory ) 
        {
            final ByteArrayObjectCodeWriterFactory factory = compileCurrentProject();
            final byte[] objectCode = factory.getBytes();
            if ( objectCode != null ) {
                // note that ByteArrayObjectCodeWriterFactory returns the address in BYTES
                emulator.loadMemory( Address.valueOf( factory.getFirstWriteOffset() >> 1) , objectCode);
            }
        }
    }
    
    public ByteArrayObjectCodeWriterFactory compileCurrentProject() 
    {
        final List<ICompilationUnit> units = currentProject == null ? Collections.<ICompilationUnit>emptyList() : currentProject.getCompilationUnits();
        return compile(new CompilationListener() , units );
    }
    
    public ByteArrayObjectCodeWriterFactory compile(ICompilationListener listener,List<ICompilationUnit> units) 
    {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be NULL.");
        }
        
        final ByteArrayObjectCodeWriterFactory factory = new ByteArrayObjectCodeWriterFactory();
        compiler.setObjectCodeWriterFactory( factory );
        
        compiler.compile( units , listener );
        
        for (ICompilationUnit unit : units ) 
        {
            if ( unit.hasErrors() ) {
                try {
                    Misc.printCompilationErrors( unit , unit.getResource() , true );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return factory;
    }
    
    public void dispose() 
    {
        cpuView.dispose();
        sourceEditorView.dispose();
        memoryDumpView.dispose(); 
        disassemblerView.dispose();
        stackView.dispose();
        
        frame.dispose();
    }
    
    private JButton createButton(String label,ActionListener listener) {
        final JButton result = new JButton(label);
        result.setForeground( AbstractView.DEFAULT_TEXT_COLOR );
        result.setBackground( AbstractView.DEFAULT_BACKGROUND_COLOR );        
        result.addActionListener( listener );
        return result;
    }
    
    protected void setupUI() {

        // ========== button toolbar ==========
        
        final JPanel toolBar = new JPanel();
        
        toolBar.setForeground( AbstractView.DEFAULT_TEXT_COLOR );
        toolBar.setBackground( Color.DARK_GRAY );
        
        toolBar.add( createButton("START" , new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.start();
            }
        }));
        
        toolBar.add( createButton("STOP" , new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.start();
            }
        }));      
        
        toolBar.add( createButton("STEP" , new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.executeOneInstruction();
            }
        }));     
        
        GridBagConstraints cnstrs = new GridBagConstraints();
        
        // ============= disassembly and CPU ============
        
        final JPanel disassemblerAndCPU = new JPanel();
        
        disassemblerAndCPU.setForeground( AbstractView.DEFAULT_TEXT_COLOR );
        disassemblerAndCPU.setBackground( AbstractView.DEFAULT_BACKGROUND_COLOR );        
        
        disassemblerAndCPU.setLayout( new GridBagLayout() );
        
        // add dissassembler view
        cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;
        cnstrs.weightx=0.5d;
        cnstrs.weighty =1.0d;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.gridheight = 1;
        cnstrs.gridwidth = 1;
        cnstrs.gridx=0;
        cnstrs.gridy=0;
        disassemblerAndCPU.add( addBorder("Disassembly" , disassemblerView.getPanel() ) , cnstrs );       
        
        // add CPU view
        cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;        
        cnstrs.weightx=0.5d;
        cnstrs.weighty =1.0d;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.gridheight = 1;
        cnstrs.gridwidth = 1;
        cnstrs.gridx=1;
        cnstrs.gridy=0;
        disassemblerAndCPU.add( addBorder("CPU" , cpuView.getPanel() ) , cnstrs );  
        
        // =========== memory and stack ==============
        
        final JPanel memoryAndStack = new JPanel();
        
        memoryAndStack.setForeground( AbstractView.DEFAULT_TEXT_COLOR );
        memoryAndStack.setBackground( AbstractView.DEFAULT_BACKGROUND_COLOR );        
        
        memoryAndStack.setLayout( new GridBagLayout() );        
        
        cnstrs = new GridBagConstraints();
        
        // add memory hex-dump view
        cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;        
        cnstrs.weightx=0.5d;
        cnstrs.weighty =1.0d;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.gridheight = 1;
        cnstrs.gridwidth = 1;
        cnstrs.gridx= 0;
        cnstrs.gridy=0;
        memoryAndStack.add( addBorder("Memory" , memoryDumpView.getPanel() ) , cnstrs );
        
        // add stack hex-dump view
        cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;        
        cnstrs.weightx=0.5d;
        cnstrs.weighty =1.0d;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.gridheight = 1;
        cnstrs.gridwidth = 1;
        cnstrs.gridx=1;
        cnstrs.gridy=0;
        memoryAndStack.add( addBorder("Stack" , stackView.getPanel() ) , cnstrs );
        
        // =============== add stuff to frame ===============
        
        final JPanel panel = new JPanel();
        panel.setBackground( Color.BLACK );
        panel.setLayout( new GridBagLayout() );
        
        int y = 0;
        
        // add toolbar
        
        cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;
        cnstrs.weightx=1.0d;
        cnstrs.weighty =0.0d;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.gridheight = 1;
        cnstrs.gridwidth = GridBagConstraints.REMAINDER;
        cnstrs.gridx=GridBagConstraints.REMAINDER;
        cnstrs.gridy=y++;
        panel.add( toolBar , cnstrs );       
        
        // add disassembly + CPU
        cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;
        cnstrs.weightx=1.0d;
        cnstrs.weighty =0.0d;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.gridheight = 1;
        cnstrs.gridwidth = GridBagConstraints.REMAINDER;
        cnstrs.gridx=GridBagConstraints.REMAINDER;
        cnstrs.gridy=y++;
        panel.add( disassemblerAndCPU , cnstrs );      
        
        // add memory + stack dump
        cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;
        cnstrs.weightx=1.0d;
        cnstrs.weighty =0.0d;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.gridheight = 1;
        cnstrs.gridwidth = GridBagConstraints.REMAINDER;
        cnstrs.gridx=GridBagConstraints.REMAINDER;
        cnstrs.gridy=y++;
        panel.add( memoryAndStack , cnstrs );         
        
        // add source editor
        cnstrs = new GridBagConstraints();
        cnstrs.anchor = GridBagConstraints.NORTHWEST;
        cnstrs.weightx=1.0d;
        cnstrs.weighty =1.0d;
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.gridheight = GridBagConstraints.REMAINDER;
        cnstrs.gridwidth = GridBagConstraints.REMAINDER;
        cnstrs.gridx=GridBagConstraints.REMAINDER;
        cnstrs.gridy=y++;
        panel.add( sourceEditorView.getPanel() , cnstrs );          
        
        // add window close listener
        frame.addWindowListener( new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e)
            {
                dispose();                
            } 
        } );
        
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE );    
        
        frame.setBackground( Color.BLACK );
        frame.getContentPane().setBackground( Color.BLACK );
        frame.getContentPane().add( panel );
        frame.setPreferredSize( new Dimension(1024,600 ) );
        frame.pack();
    }
    
    private JComponent addBorder(String text,JComponent toWrap) 
    {
        final Border b = BorderFactory.createLineBorder( Color.GREEN );
        
        final TitledBorder border = BorderFactory.createTitledBorder(b, text, TitledBorder.LEADING, TitledBorder.TOP, AbstractView.DEFAULT_FONT, 
                AbstractView.DEFAULT_TEXT_COLOR );
        toWrap.setBorder( border );
        return toWrap;
    }
}
