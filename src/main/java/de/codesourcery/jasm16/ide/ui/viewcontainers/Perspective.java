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
package de.codesourcery.jasm16.ide.ui.viewcontainers;

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ide.IApplicationConfig;
import de.codesourcery.jasm16.ide.ui.MenuManager;
import de.codesourcery.jasm16.ide.ui.MenuManager.MenuEntry;
import de.codesourcery.jasm16.ide.ui.utils.SizeAndLocation;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.views.IView;
import de.codesourcery.jasm16.ide.ui.views.IViewStateListener;

/**
 * A view container that inherits from {@link JFrame} and uses {@link JInternalFrame}s to display
 * it's children.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Perspective extends JFrame implements IViewContainer {

    private static final Logger LOG = Logger.getLogger(Perspective.class);

    private final JDesktopPane desktop = new JDesktopPane();

    private final List<InternalFrameWithView> views = new ArrayList<InternalFrameWithView>();

    private final String id;

    private final ViewContainerHelper helper = new ViewContainerHelper();

    private final ViewContainerManager viewContainerManager;
    
    private final IApplicationConfig applicationConfig;

    private final MenuManager menuManager = new MenuManager() {

        @Override
        public void menuBarChanged() 
        {
            setJMenuBar( menuManager.getMenuBar() );
        }
    };

    protected final class InternalFrameWithView 
    {
        public final JInternalFrame frame;
        public final IView view;
        private final InternalFrameListener frameListener;

        public InternalFrameWithView(JInternalFrame frame,final IView view) {
            this.view = view;
            this.frame = frame;
            
            if ( view instanceof IViewStateListener ) {
                frameListener = new InternalFrameAdapter() {
                    @Override
                    public void internalFrameActivated(InternalFrameEvent e)
                    {
                        ((IViewStateListener) view).viewVisible();
                    }
                    @Override
                    public void internalFrameDeactivated(InternalFrameEvent e)
                    {
                        ((IViewStateListener) view).viewHidden();
                    }                    
                };
                frame.addInternalFrameListener( frameListener );
            } else {
                frameListener = null;
            }
        }

        public void dispose() 
        {
            final SizeAndLocation sizeAndLoc = new SizeAndLocation( frame.getLocation() , frame.getSize() );
            applicationConfig.storeViewCoordinates( getUniqueID( view ) , sizeAndLoc );
            frame.dispose();
            if ( frameListener != null ) {
                frame.removeInternalFrameListener( frameListener );
            }            
            
            LOG.debug("dispose(): Disposing "+view);
            view.dispose();			
        }
    }

    private final String getUniqueID(IView view) {
        return getID()+"."+view.getID();
    }

    @Override
    public void setBlockAllUserInput(boolean yesNo) 
    {
        UIUtils.setBlockAllUserInput( this , yesNo );
    }

    @Override
    public final void dispose() 
    {
    	disposeHook();
    	
        final SizeAndLocation sizeAndLoc = new SizeAndLocation( getLocation() , getSize() );
        applicationConfig.storeViewCoordinates( getID() , sizeAndLoc );

        final List<InternalFrameWithView> views = new ArrayList<InternalFrameWithView>(this.views);
        for ( InternalFrameWithView v : views) {
            disposeView( v.view );
        }

        super.dispose();

        viewContainerManager.disposeAllExcept( this );
        
        helper.fireViewContainerClosed( this );

        try {
            this.applicationConfig.saveConfiguration();
        } catch (IOException e) {
            LOG.error("dispose(): Failed to save view coordinates",e);
        }
    }
    
    protected void disposeHook() {
    	
    }

    public Perspective(String id , final ViewContainerManager viewContainerManager , IApplicationConfig appConfig) 
    {
        super("jASM16 DCPU emulator V"+de.codesourcery.jasm16.compiler.Compiler.getVersionNumber() );

        if ( viewContainerManager == null ) {
			throw new IllegalArgumentException("viewContainerManager must not be null");
		}
        
        if (appConfig == null) {
            throw new IllegalArgumentException("appConfig must not be null");
        }

        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("ID must not be NULL/blank.");
        }

        this.viewContainerManager = viewContainerManager;
        this.id = id;
        this.applicationConfig = appConfig;
        setPreferredSize( new Dimension(400,200 ) );
        getContentPane().add( desktop );

        setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );

        setBackground( Color.BLACK );
        setForeground( Color.GREEN );

        desktop.setBackground( Color.BLACK );
        desktop.setForeground( Color.GREEN );	

        menuManager.addEntry( new MenuEntry("File/Quit") {

            @Override
            public void onClick() 
            {
            	dispose();
            	try {
            		applicationConfig.saveConfiguration();
            	} catch (IOException e) {
            		e.printStackTrace();
				} finally {
            		System.exit(0);
            	}
            }

        } );		

        final SizeAndLocation sizeAndLoc = applicationConfig.getViewCoordinates( getID() );
        if ( sizeAndLoc != null ) {
            setLocation( sizeAndLoc.getLocation() );
            setSize( sizeAndLoc.getSize() );
            setPreferredSize( sizeAndLoc.getSize() );
        } else {
            setPreferredSize( new Dimension(600,800 ) );
            pack();
        }
        setJMenuBar( menuManager.getMenuBar() );
    }

    @Override
    public void disposeView(IView view) 
    {
        for (Iterator<InternalFrameWithView> it = this.views.iterator(); it.hasNext();) 
        {
            InternalFrameWithView frame = it.next();
            if ( frame.view == view ) 
            {
                frame.dispose();
                it.remove();
                return;
            }
        }
    }

    @Override
    public IView addView(final IView view) 
    {
        if (view == null) {
            throw new IllegalArgumentException("view must not be NULL");
        }
        final JInternalFrame internalFrame = new JInternalFrame( view.getTitle(),true, true, true, true);

        internalFrame.setBackground(Color.BLACK);
        internalFrame.setForeground( Color.GREEN );

        internalFrame.getContentPane().add( view.getPanel(this) );

        SizeAndLocation sizeAndLoc = applicationConfig.getViewCoordinates( getUniqueID( view ) );
        if ( sizeAndLoc != null ) 
        {
            internalFrame.setSize( sizeAndLoc.getSize() );
            internalFrame.setLocation( sizeAndLoc.getLocation() );
        } else {
            internalFrame.setSize(200, 150);
            internalFrame.setLocation( 0 , 0 );
            internalFrame.pack();
        }

        internalFrame.setVisible( true );

        final InternalFrameWithView frameAndView = new InternalFrameWithView( internalFrame , view );

        final InternalFrameListener listener = new InternalFrameAdapter() {

            @Override
            public void internalFrameClosing(InternalFrameEvent e) {
                disposeView( view );
            }
        };

        internalFrame.setDefaultCloseOperation( JInternalFrame.DO_NOTHING_ON_CLOSE );
        internalFrame.addInternalFrameListener( listener );

        views.add( frameAndView );
        desktop.add(internalFrame);
        return view;
    }

    @Override
    public List<IView> getViews() 
    {
        final List<IView> result = new ArrayList<IView>();
        for (InternalFrameWithView frame : this.views) {
            result.add( frame.view );
        }
        return result;
    }

    @Override
    public void setTitle(IView view, String title) 
    {
        for (InternalFrameWithView frame : this.views) 
        {
            if ( frame.view == view ) {
                frame.frame.setTitle( title );
                break;
            }
        }
    }

    @Override
    public IView getViewByID(String viewId) 
    {
        if (StringUtils.isBlank(viewId)) {
            throw new IllegalArgumentException("viewId must not be blank/null");
        }

        for (InternalFrameWithView frame : this.views) 
        {
            if ( frame.view.getID().equals( viewId ) ) {
                return frame.view;
            }
        }		
        return null;
    }

    @Override
    public final void toFront(IView view) 
    {
        for (InternalFrameWithView frame : this.views) 
        {
            if ( frame.view.getID().equals( view.getID() ) ) 
            {
                frame.frame.toFront();
                return;
            }
        }	
    }

    @Override
    public MenuManager getMenuManager() {
        return menuManager;
    }

    @Override
    public final String getID()
    {
        return id;
    }

    @Override
    public void addViewContainerListener(IViewContainerListener listener)
    {
        helper.addViewContainerListener( listener );
    }

    @Override
    public void removeViewContainerListener(IViewContainerListener listener)
    {
        helper.removeViewContainerListener( listener );
    }
}
