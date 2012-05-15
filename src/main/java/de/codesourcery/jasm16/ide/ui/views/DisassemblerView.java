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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.disassembler.DisassembledLine;
import de.codesourcery.jasm16.disassembler.Disassembler;
import de.codesourcery.jasm16.emulator.Breakpoint;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.ide.ui.utils.PagingKeyAdapter;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.viewcontainers.DebuggingPerspective;
import de.codesourcery.jasm16.utils.Misc;

public class DisassemblerView extends AbstractView
{
    public static final String VIEW_ID = "dissassembly-view";
    
    private JPanel panel;
    private final JTextArea textArea = new JTextArea();
    private final JButton singleStepButton = new JButton("Step");
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");   
    private final JButton resetButton = new JButton("Reset");
    private JCheckBox runAtRealSpeed;
    
    private final DebuggingPerspective perspective;
    private IEmulator emulator;
    
    private boolean showHexDump = true;
    
    private final Disassembler disassembler = new Disassembler();
    
    private final IEmulationListener listener = new EmulationListener() {

    	public void breakpointAdded(IEmulator emulator, Breakpoint breakpoint) {
        	refreshDisplay();
    	};
    	
    	public void breakpointChanged(IEmulator emulator, Breakpoint breakpoint) {
    		refreshDisplay();
    	};
    	
    	public void onBreakpoint(IEmulator emulator, Breakpoint breakpoint) {
    		System.out.println("Breakpoint reached: "+breakpoint);
    	};
    	
    	public void breakpointDeleted(IEmulator emulator, Breakpoint breakpoint) {
    		refreshDisplay();
    	};
    	
        @Override
        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes)
        {
        	if ( ! isFullSpeedMode() ) {
        		refreshDisplay();
        	}
        }
        
        @Override
        public void afterCommandExecution(IEmulator emulator, int commandDuration)
        {
        	if ( ! isFullSpeedMode() ) {
        		refreshDisplay();
        	}
        }

        @Override
        public void afterReset(IEmulator emulator)
        {
        	updateButtonStates( false );          	
       		refreshDisplay();
        }
        
        @Override
        protected void beforeContinuousExecutionHook() {
        	updateButtonStates( true );        	
        };
        
		@Override
		public void afterContinuousExecutionHook() {
			updateButtonStates( false );
			refreshDisplay();
		}        
     };
     
    public DisassemblerView(DebuggingPerspective perspective, IEmulator emulator) {
    	if ( perspective == null ) {
			throw new IllegalArgumentException("perspective must not be null");
		}
    	this.perspective = perspective;
        setEmulator( emulator );
    }
    
    private void updateButtonStates(final boolean emulatorRunningContinously) {

    	final Runnable runnable = new Runnable() {

			@Override
			public void run() {
		   	    singleStepButton.setEnabled( ! emulatorRunningContinously );
		   	    runButton.setEnabled( ! emulatorRunningContinously );
		   	    stopButton.setEnabled( emulatorRunningContinously );
			    resetButton.setEnabled( true );  
			}
		};

    	if ( SwingUtilities.isEventDispatchThread() ) {
    		runnable.run();
    	} else {
    		SwingUtilities.invokeLater( runnable );
    	}
    }
    
    @Override
    public void refreshDisplay() 
    {
        if ( emulator == null ) {
            return;
        }
        
        setViewStartingAddress( emulator.getCPU().getPC() );
    }
    
    public void setViewStartingAddress(Address startingAddress) 
    {
    	// show some context before the actual address so the 
    	// use is not completely lost where in the program he is
        final Address offset = Address.wordAddress( 3 ); 
        final Address realStart = startingAddress.minus( offset );
        
        int rows = calculateVisibleTextRowCount( textArea );
        if ( rows < 5 ) {
        	rows = 5;
        }
        final List<DisassembledLine> lines = disassembler.disassemble( 
        		emulator.getMemory() , 
        		realStart , rows , showHexDump 
        );
        
        renderDisassembly(lines);    	
    }

	private void renderDisassembly(final List<DisassembledLine> lines) 
	{
        final Address pc = emulator.getCPU().getPC(); // used to mark the current PC value
        
		final StringBuilder result = new StringBuilder();
        final Iterator<DisassembledLine> it = lines.iterator();
        while( it.hasNext() ) 
        {
            result.append( toString( pc , it.next() ) );
            if ( it.hasNext() ) {
                result.append("\n");
            }
        }
        
        SwingUtilities.invokeLater( new Runnable() {

            @Override
            public void run()
            {
                textArea.setText( result.toString() );         
            }
        });
	}
    
    private String toString(Address pc , DisassembledLine line) 
    {
        final Address realAddress = line.getAddress();
        return createLinePrefix(pc,realAddress,line)+ Misc.toHexString( realAddress )+": "+line.getContents();
    }
    
    private String createLinePrefix(Address pc , Address realAddress , DisassembledLine line) 
    {
    	/*
    	 * The prefix may contain a flag indicating that 
    	 * a breakpoint is present on this line as well
    	 * as a caret for the current program counter (PC) position.
    	 * 
    	 * Example:
    	 * 
    	 * [B] >> 0000: SET a,1
    	 */
    	
    	final Breakpoint breakpoint = emulator.getBreakPoint( realAddress);
		String prefix1 = breakpoint != null ? breakpoint.isEnabled() ? "[B] " : "[_] " : "    ";
    	String prefix2 = realAddress.equals( pc ) ? ">> " : "   ";
    	return prefix1+prefix2;
    }
    
    private DisassembledLine parseDisassembledLine(String text) {
    	
    	// [B] >> 0000: 
    	final Pattern pattern = Pattern.compile( "^(\\[B\\]){0,1}[ ]*(>>){0,1}[ ]*([0-9a-f]+):(.*?);(.*)");
    	
    	final Matcher m = pattern.matcher( text );
    	if ( ! m.matches() ) {
    		throw new RuntimeException("Unparseable line '"+text+"'");
    	}
    	@SuppressWarnings("unused")
		final String hasBreakpoint = m.group(1);
    	@SuppressWarnings("unused")
		final String isAtCurrentPC = m.group(2);
    	final Address address = Address.wordAddress( Misc.parseHexString( m.group(3) ) );
    	final String disassembly = m.group(4);
    	final String instructionWordsHexDump = m.group(5).trim();
    	final Size instructionSize = Size.words( instructionWordsHexDump.split(" ").length ); 
    	return new DisassembledLine( address , disassembly , instructionSize );
    }
    
    public void setEmulator(IEmulator emulator)
    {
        if (emulator == null) {
            throw new IllegalArgumentException("emulator must not be NULL.");
        }
        if ( this.emulator == emulator ) {
            return;
        }
        
        if ( this.emulator != null ) {
            this.emulator.removeEmulationListener( listener );
        }
        this.emulator = emulator;
        
        emulator.addEmulationListener( listener );
    }
    
    @Override
    public void dispose() 
    {
        if ( this.emulator != null ) {
            this.emulator.removeEmulationListener( listener );
            this.emulator = null;
        }
    }
    
    protected JPanel createPanel()
    {
        textArea.setEditable( false );
        setColors( textArea );
        textArea.setFont( getMonospacedFont() );
        textArea.setEditable( false );
        
        textArea.addMouseListener( new MouseAdapter() 
        {
        	public void mouseClicked(java.awt.event.MouseEvent e) 
        	{
        		if ( e.getButton() != MouseEvent.BUTTON3 ) {
        			return;
        		}
        		String text = getTextAtLocation( textArea , e.getX() , e.getY() );
        		if ( text != null ) {
        			text = text.replaceAll( Pattern.quote("\n" ) , "" );
        			
        			final DisassembledLine line = parseDisassembledLine( text );
        			toggleBreakpoint( line.getAddress() );
        		}
        	}
        } );
        
        textArea.addKeyListener( new PagingKeyAdapter() {
			
			@Override
			protected void onePageUp() {
			}
			
			@Override
			protected void onePageDown() {
			}
			
			@Override
			protected void oneLineUp() {
			}
			
			@Override
			protected void oneLineDown() {
			}
		});        
        
        // setup top panel
        final JPanel buttonBar = new JPanel();
        buttonBar.setLayout( new GridBagLayout() );        

        // =========== "SINGLE STEP" button ============        
        singleStepButton.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.executeOneInstruction();
            }
        });
        
        GridBagConstraints cnstrs = constraints( 0 , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( singleStepButton , cnstrs );
        
        // =========== "RUN" button ============        
        runButton.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.start();
                System.out.println("Simulation started!");
            }
        });
        
        cnstrs = constraints( 1 , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( runButton , cnstrs );   
        
        // =========== "STOP" button ============        
        stopButton.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                emulator.stop();
            }
        });
        
        cnstrs = constraints( 2 , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( stopButton , cnstrs );          
        
        // =========== "RESET" button ============
        resetButton.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
            	perspective.resetEmulator();
            }
        });
        
        cnstrs = constraints( 3 , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( resetButton , cnstrs );
        
        // =========== "Run at full speed" checkbox ============
        runAtRealSpeed = new JCheckBox("Run at real speed",emulator.isRunAtRealSpeed());
        final AtomicBoolean isCalibrating = new AtomicBoolean(false);
        runAtRealSpeed.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
				final boolean isSelected = runAtRealSpeed.isSelected();
				if ( isSelected ) 
				{
					if ( isCalibrating.compareAndSet( false , true ) ) 
					{
						new Thread() {
							@Override
							public void run() 
							{
								final JDialog dialog = UIUtils.createMessageDialog( null, "Calibrating emulation speed" , "Please wait, benchmarking your system...");
								dialog.setModal(true);
								
								new Thread() {
									@Override
									public void run() 
									{
										try {
										    emulator.calibrate();
											emulator.setRunAtRealSpeed( true );
										} 
										finally {
											isCalibrating.set( false );											
											dialog.dispose();
										}
									}
								}.start();
								dialog.setVisible( true );
							}
						}.start();
					}
				} else {
					emulator.setRunAtRealSpeed( isSelected );
				}
            }
        });
        
        cnstrs = constraints( 4 , 0 , true , true , GridBagConstraints.NONE );          
        buttonBar.add( runAtRealSpeed , cnstrs );        
        
        // setup bottom panel        
        final JPanel bottomPanel = new JPanel();
        setColors( bottomPanel );
        bottomPanel.setLayout( new GridBagLayout() );        
        cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
        bottomPanel.add( textArea , cnstrs );
        
        // ======== assemble result panel ===========
        
        final JPanel result = new JPanel();
        result.addComponentListener( new ComponentAdapter() {
        	@Override
        	public void componentResized(ComponentEvent e) {
        		refreshDisplay();
        	}
		});
        setColors( result );
        result.setLayout( new GridBagLayout() );       
        
        cnstrs = constraints( 0 , 0 , true, false , GridBagConstraints.HORIZONTAL );
        result.add( buttonBar  , cnstrs );
        cnstrs = constraints( 0 , 1 , true , true , GridBagConstraints.BOTH);
        result.add( bottomPanel  , cnstrs );        
        
        updateButtonStates( false );
        return result;
    }

    protected void toggleBreakpoint(Address address) {
    	
    	Breakpoint existing = emulator.getBreakPoint( address );
    	if ( existing == null ) {
    		emulator.addBreakpoint( new Breakpoint( address ) );
    	} else {
    		emulator.deleteBreakpoint( existing );
    	}
    }
    
    @Override
    public JPanel getPanel() {
        if ( panel == null ) {
            panel = createPanel();
        }
        return panel;
    }

	@Override
	public String getTitle() {
		return "Disassembly view";
	}

	@Override
	public String getID() {
		return VIEW_ID;
	}
    
}
