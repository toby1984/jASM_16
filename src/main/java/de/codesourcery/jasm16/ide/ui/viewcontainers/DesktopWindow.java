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
import java.awt.event.WindowAdapter;
import java.util.ArrayList;
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
	}


	@Override
	public void removeView(IView view) 
	{
		for (Iterator<InternalFrameWithView> it = this.views.iterator(); it.hasNext();) {
			InternalFrameWithView frame = it.next();
			if ( frame.view == view ) 
			{
				frame.frame.dispose();
				it.remove();
				return;
			}
		}
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

		final InternalFrameListener listener = new InternalFrameAdapter() {
			
			@Override
			public void internalFrameClosed(InternalFrameEvent e) {
				view.dispose();
			}
		};
		
		internalFrame.addInternalFrameListener( listener );
		
		views.add( new InternalFrameWithView( internalFrame , view ) );
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
