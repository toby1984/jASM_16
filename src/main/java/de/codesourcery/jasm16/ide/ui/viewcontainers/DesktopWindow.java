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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import de.codesourcery.jasm16.ide.ui.views.IView;

/**
 * A view container that inherits from {@link JFrame} and uses {@link JInternalFrame}s to display
 * it's children.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class DesktopWindow extends JFrame implements IViewContainer {

	private final JDesktopPane desktop = new JDesktopPane();

	private final List<InternalFrameWithView> views = new ArrayList<InternalFrameWithView>();

	protected static final class InternalFrameWithView 
	{
		public final JInternalFrame frame;
		public final IView view;

		public InternalFrameWithView(JInternalFrame frame,IView view) 
		{
			this.view = view;
			this.frame = frame;
		}
	}
	
	@Override
	public void dispose() 
	{
		final List<InternalFrameWithView> views = new ArrayList<InternalFrameWithView>(this.views);
		for ( InternalFrameWithView v : views) {
			v.frame.dispose();
			v.view.dispose();
		}
		
		super.dispose();
	}

	public DesktopWindow() 
	{
		super("jASM16 DCPU emulator V"+de.codesourcery.jasm16.compiler.Compiler.getVersionNumber() );
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
	}


	@Override
	public void removeView(IView view) 
	{
		System.out.println("View disposed: "+view.getTitle());
		
		for (Iterator<InternalFrameWithView> it = this.views.iterator(); it.hasNext();) 
		{
			InternalFrameWithView frame = it.next();
			if ( frame.view == view ) 
			{
				frame.frame.dispose();
				it.remove();
				return;
			}
		}
	}
	
	private static class SizeAndLocation 
	{
		public Point location;
		public Dimension dimension;
		
		public SizeAndLocation(Point location, Dimension dimension) {
			this.location = location;
			this.dimension = dimension;
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
		internalFrame.setSize(200, 150);
		internalFrame.setLocation( 0 , 0 );
		internalFrame.setBackground(Color.BLACK);
		internalFrame.setForeground( Color.GREEN );
		internalFrame.getContentPane().add( view.getPanel(this) );
		internalFrame.pack();
		internalFrame.setVisible( true );

		final InternalFrameWithView frameAndView = new InternalFrameWithView( internalFrame , view );
		
		final InternalFrameListener listener = new InternalFrameAdapter() {
			
			@Override
			public void internalFrameClosing(InternalFrameEvent e) {
				removeView( view );
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
}
