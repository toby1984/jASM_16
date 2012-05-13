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
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
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
import de.codesourcery.jasm16.ide.ui.views.IView;

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

		public InternalFrameWithView(JInternalFrame frame,IView view) 
		{
			this.view = view;
			this.frame = frame;
		}
		
		public void dispose() 
		{
			final SizeAndLocation sizeAndLoc = new SizeAndLocation( frame.getLocation() , frame.getSize() );
			applicationConfig.storeViewCoordinates( getUniqueID( view ) , sizeAndLoc );
			frame.dispose();
			System.out.println("Disposing "+view);
			view.dispose();			
		}
	}
	
	private final String getUniqueID(IView view) {
	    return getID()+"."+view.getID();
	}
	
	@Override
	public void dispose() 
	{
        final SizeAndLocation sizeAndLoc = new SizeAndLocation( getLocation() , getSize() );
        applicationConfig.storeViewCoordinates( getID() , sizeAndLoc );
        
		final List<InternalFrameWithView> views = new ArrayList<InternalFrameWithView>(this.views);
		for ( InternalFrameWithView v : views) {
			disposeView( v.view );
		}
		
		super.dispose();
		
		helper.fireViewContainerClosed( this );
		
		try {
			this.applicationConfig.saveConfiguration();
		} catch (IOException e) {
			LOG.error("dispose(): Failed to save view coordinates",e);
		}
	}

	public Perspective(String id , IApplicationConfig appConfig) 
	{
		super("jASM16 DCPU emulator V"+de.codesourcery.jasm16.compiler.Compiler.getVersionNumber() );
		if (appConfig == null) {
			throw new IllegalArgumentException("appConfig must not be null");
		}
		
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("ID must not be NULL/blank.");
        }
        
		this.id = id;
		this.applicationConfig = appConfig;
		setPreferredSize( new Dimension(400,200 ) );
		getContentPane().add( desktop );

		addWindowListener( new WindowAdapter() {

			public void windowClosing(java.awt.event.WindowEvent e) {
				dispose();
			};
		} );

		setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		
		setBackground( Color.BLACK );
		setForeground( Color.GREEN );
		
		desktop.setBackground( Color.BLACK );
		desktop.setForeground( Color.GREEN );	
		
		menuManager.addEntry( new MenuEntry("File/Save all") {

			@Override
			public void onClick() {
				System.out.println("Save all!");
			}
			
			public boolean isEnabled() {
				
				final EditorContainer editorContainer = (EditorContainer ) 
						getViewByID( EditorContainer.VIEW_ID );
				
				if ( editorContainer == null ) {
					return false;
				}
				return editorContainer.getViews().size() > 0 ;
			};
			
		} );
		
		menuManager.addEntry( new MenuEntry("File/Quit") {

			@Override
			public void onClick() 
			{
				System.exit(0);
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
	
//	protected SizeAndLocation findLargestFreeSpace() {
//	
//		final Dimension maxSize = desktop.getSize();
//		
//		if ( views.isEmpty() ) {
//			return new SizeAndLocation( new Point(0,0) , maxSize );
//		}
//		
//		/*
//		 * +-----+
//		 * |     |
//		 * |     |
//		 * +-----+    
//		 */
//		final List<InternalFrameWithView > existing = new ArrayList<InternalFrameWithView>( views );
//		final Comparator<InternalFrameWithView> comparator = new Comparator<InternalFrameWithView>() 
//		{
//			
//			@Override
//			public int compare(InternalFrameWithView o1, InternalFrameWithView o2) 
//			{
//				final Point loc1 = o1.frame.getLocation();
//				final Point loc2 = o2.frame.getLocation();
//				
//				if ( loc1.y <= loc2.y )
//				{
//					return Integer.valueOf( loc1.x ).compareTo( loc2.x );
//				}
//				return 1;
//			}
//		};
//		
//		Collections.sort( existing , comparator );
//		
//		Rectangle rect = new Rectangle(0,0,400,400);
//		
//		for (Iterator<InternalFrameWithView> it = existing.iterator(); it.hasNext();) 
//		{
//			final InternalFrameWithView thisFrame =  it.next();
//			if ( ! it.hasNext() ) 
//			{
//				final int x1 = thisFrame.frame.getSize().width;
//				final int y1 = (int) thisFrame.frame.getLocation().getY();
//				final int width = 200;
//				final int height = 200;
//				Rectangle result = new Rectange( x1,y1,width,height );
//				if ( isFullyVisible( result ) ) {
//					return new SizeAndLocation( result );
//				}
//			}
//			final InternalFrameWithView nextFrame =  it.next();
//			if ( thisFrame.frame.getLocation().getY() < nextFrame.frame.getLocation().getY() ) {
//				final Rectangle bounds = new Rectangle( new Point( 0 , 0 ) , frameAndView.frame.getSize() );
//			}
//		}
//	}
	
	@SuppressWarnings("unused")
    private boolean isFullyVisible(Rectangle rect) 
	{
		final int x1 = (int) rect.getX();
		final int y1 = (int) rect.getY();
		
		final int x2 = (int) rect.getMaxX();
		final int y2 = (int) rect.getMaxY();
		
		if ( x1 < desktop.getBounds().getX() || y1 < desktop.getBounds().y ) {
			return false;
		}
		
		if ( x2 > desktop.getBounds().getMaxX() || y2 > desktop.getBounds().getMaxY() ) {
			return false;
		}
		return true;
	}
	
	@Override
	public void addView(final IView view) 
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
			System.out.println("load(): "+view.getID()+" => "+sizeAndLoc);
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
	public MenuManager getMenuManager() {
		return menuManager;
	}

    @Override
    public String getID()
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
