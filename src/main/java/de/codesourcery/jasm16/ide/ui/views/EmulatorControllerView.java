package de.codesourcery.jasm16.ide.ui.views;

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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.IEmulator.EmulationSpeed;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.viewcontainers.DebuggingPerspective;
import de.codesourcery.jasm16.ide.ui.viewcontainers.IViewContainer;

public class EmulatorControllerView extends AbstractView
{
    public static final String VIEW_ID = "emulator-controller-view";

    private static final AtomicBoolean showDialog = new AtomicBoolean(false);
    
    // @GuardedBy( showDialog )
    private static DialogHelper worker = null;
    
    private static final AtomicLong LISTENER_REGISTRATION_COUNT = new AtomicLong(0);
    
    private JPanel panel;
    private final JButton singleStepButton = new JButton("Step");
    private final JButton stepReturnButton = new JButton("Step return");   
    private final JButton skipButton = new JButton("Skip");    
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");   
    private final JButton resetButton = new JButton("Reset");

    private JCheckBox runAtRealSpeed;

    private final DebuggingPerspective perspective;
    private IEmulator emulator;
    
    private final MyEmulationListener listener;    

    private final class DialogHelper 
    {
        private final AtomicReference<JFrame> dialog = new AtomicReference<JFrame>();
        private final IViewContainer parent;
        
        public DialogHelper() 
        {
            parent = getViewContainer();
            if ( parent == null ) {
                throw new IllegalStateException("NULL parent ?");
            }
        }

        public void showDialog() 
        {
            UIUtils.invokeLater( new Runnable() {
                @Override
                public void run()
                {
                    System.out.println("Creating dialog (EDT="+SwingUtilities.isEventDispatchThread()+")");
                    final JFrame tmp = UIUtils.createMessageFrame( "Calibrating emulation speed" , "Please wait, benchmarking your system...");
                    tmp.setVisible( true );
                    parent.setBlockAllUserInput( true );
                    dialog.set(tmp);
                }
            });
        }

        public void closeDialog() 
        {
            UIUtils.invokeLater( new Runnable() 
            {
                @Override
                public void run()
                {
                    System.out.println("Disposing dialog (EDT="+SwingUtilities.isEventDispatchThread()+")");
                    while( dialog.get() == null ) {}
                    dialog.get().dispose();
                    parent.setBlockAllUserInput( false );
                }
            });
        }
    }

    protected final class MyEmulationListener extends EmulationListener 
    {
        // this view may be visible in multiple instances (and thus this register may be registered more than once), this flag
        // is used to make sure only exactly one of the listeners responds to beforeCalibration() / afterCalibration() messages
        private final boolean isFirstListener;
        
        public MyEmulationListener(boolean isFirstListener) {
            this.isFirstListener = isFirstListener;
        }
        
        public void beforeCalibration(IEmulator emulator) 
        {
            if ( ! isFirstListener ) {
                return;
            }
            synchronized( showDialog ) 
            {            
                if ( showDialog.compareAndSet(false,true) && worker == null )
                {
                    worker = new DialogHelper();
                    worker.showDialog();
                }
            }
        }

        public void afterCalibration(IEmulator emulator) 
        {
            if ( ! isFirstListener ) {
                return;
            }            
            synchronized( showDialog ) 
            {  
                if ( showDialog.compareAndSet(true,false) && worker != null ) 
                {
                    worker.closeDialog();
                    worker = null;
                }
            }
        }

        public void onEmulationSpeedChange(EmulationSpeed oldSpeed, EmulationSpeed newSpeed) {

            if ( runAtRealSpeed != null ) {
                runAtRealSpeed.setSelected( newSpeed == EmulationSpeed.REAL_SPEED );
            }
        }

        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes) {
            updateButtonStates(false);
        }

        @Override
        public void afterReset(IEmulator emulator)
        {
            updateButtonStates( false );            
        }

        @Override
        protected void beforeContinuousExecutionHook() {
            updateButtonStates( true );         
        }

        @Override
        public void onStopHook(IEmulator emulator, Address previousPC, Throwable emulationError) {
            updateButtonStates( false );
        }        
    };

    // helper interface for invoking IEmulator methods from a non-EDT thread
    protected abstract class Invoker implements Runnable 
    {
        public abstract void invoke(IEmulator emulator);
        
        @Override
        public final void run()
        {
            invoke(emulator);
        }
    }
    
    public EmulatorControllerView(DebuggingPerspective perspective, IEmulator emulator) {
        if ( perspective == null ) {
            throw new IllegalArgumentException("perspective must not be null");
        }
        this.perspective = perspective;
        this.emulator = emulator;
        listener = new MyEmulationListener( LISTENER_REGISTRATION_COUNT.incrementAndGet() == 1 );
        emulator.addEmulationListener( listener );
    }
    
    private void updateButtonStates(final boolean emulatorRunningContinously) {

        final Runnable runnable = new Runnable() {

            @Override
            public void run() 
            {
                skipButton.setEnabled( ! emulatorRunningContinously );
                singleStepButton.setEnabled( ! emulatorRunningContinously );
                runButton.setEnabled( ! emulatorRunningContinously );
                stopButton.setEnabled( emulatorRunningContinously );
                if ( emulatorRunningContinously ) {
                    stepReturnButton.setEnabled( false );
                } else {
                    stepReturnButton.setEnabled( true );
                }
                resetButton.setEnabled( true );  
            }
        };
        
        UIUtils.invokeLater( runnable );
    }

    @Override
    public void refreshDisplay() 
    {
    }

    @Override
    public void disposeHook() 
    {
        if ( this.emulator != null ) 
        {
            this.emulator.removeEmulationListener( listener );
            this.emulator = null;
        }
    }

    protected JPanel createPanel()
    {
        // setup top panel
        final JPanel buttonBar = new JPanel();
        buttonBar.setLayout( new GridBagLayout() );        

        int x = 0;
        // =========== "SINGLE STEP" button ============        
        singleStepButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                executeAsynchronously( new Invoker() {

                    @Override
                    public void invoke(IEmulator emulator)
                    {
                        emulator.executeOneInstruction();
                        updateButtonStates( false );
                    }
                });
            }
        });

        GridBagConstraints cnstrs = constraints( x++ , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( singleStepButton , cnstrs );

        // =========== "STEP RETURN" button ============        
        stepReturnButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                executeAsynchronously( new Invoker() {

                    @Override
                    public void invoke(IEmulator emulator)
                    {                
                        if ( emulator.canStepReturn() ) {
                            emulator.stepReturn();
                        } else {
                            emulator.executeOneInstruction();
                        }
                    }
                });
            }
        });

        cnstrs = constraints( x++ , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( stepReturnButton , cnstrs );       

        // =========== "Skip" button ============        
        skipButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                executeAsynchronously( new Invoker() {

                    @Override
                    public void invoke(IEmulator emulator)
                    {                
                        emulator.skipCurrentInstruction();
                    }
                });
            }
        });

        cnstrs = constraints( x++ , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( skipButton , cnstrs );              

        // =========== "RUN" button ============        
        runButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                executeAsynchronously( new Invoker() {

                    @Override
                    public void invoke(IEmulator emulator)
                    {
                        emulator.start();
                    }
                });
            }
        });

        cnstrs = constraints( x++ , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( runButton , cnstrs );   

        // =========== "STOP" button ============        
        stopButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                executeAsynchronously( new Invoker() {

                    @Override
                    public void invoke(IEmulator emulator)
                    {                
                        emulator.stop();
                    }
                });
            }
        });

        cnstrs = constraints( x++ , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( stopButton , cnstrs );          

        // =========== "RESET" button ============
        resetButton.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                executeAsynchronously( new Invoker() {

                    @Override
                    public void invoke(IEmulator emulator)
                    {                
                        perspective.resetEmulator();
                    }
                });
            }
        });

        cnstrs = constraints( x++ , 0 , false , true , GridBagConstraints.NONE );          
        buttonBar.add( resetButton , cnstrs );

        // =========== "Run at full speed" checkbox ============
        runAtRealSpeed = new JCheckBox("Run at real speed",emulator.getEmulationSpeed() == IEmulator.EmulationSpeed.REAL_SPEED);
        runAtRealSpeed.addActionListener( new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                final boolean isSelected = runAtRealSpeed.isSelected();                  
                executeAsynchronously( new Invoker() {

                    @Override
                    public void invoke(IEmulator emulator)
                    {
                        if ( isSelected ) 
                        {
                            emulator.setEmulationSpeed( IEmulator.EmulationSpeed.REAL_SPEED );
                        } else {
                            emulator.setEmulationSpeed( isSelected ? EmulationSpeed.REAL_SPEED : EmulationSpeed.MAX_SPEED );
                        }                        
                    }} );
            }
        });

        cnstrs = constraints( x++ , 0 , true , true , GridBagConstraints.NONE );          
        buttonBar.add( runAtRealSpeed , cnstrs );        

        updateButtonStates( false );
        return buttonBar;
    }
    
    @Override
    protected JPanel getPanel() {
        if ( panel == null ) {
            panel = createPanel();
        }
        return panel;
    }

    @Override
    public String getTitle() {
        return "Emulator control";
    }

    @Override
    public String getID() {
        return VIEW_ID;
    }     
}