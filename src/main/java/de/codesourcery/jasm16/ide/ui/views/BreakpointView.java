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
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;

import de.codesourcery.jasm16.emulator.Breakpoint;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.ide.DebuggerOptions;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.utils.Misc;

public abstract class BreakpointView extends AbstractView {

	private static final String UNCONDITIONAL_BREAKPOINT = "<unconditional>";

	public static final String VIEW_ID = "breakpoints-view";
	
	private final SourceLevelDebugView sourceLevelDebugView;
	private final DisassemblerView disView;	
	
	
	private final IEmulator emulator;
	private JPanel panel;

	private static final int COL_BP_ENABLED = 0;	
	private static final int COL_BP_ADDRESS = 1;
	private static final int COL_BP_EXPRESSION = 2;
	
	private final MyTableModel tableModel = new MyTableModel();
	private final JTable table = new JTable( tableModel );
	
	private final IEmulationListener listener = new EmulationListener() 
	{
		public void breakpointAdded(IEmulator emulator, final Breakpoint breakpoint) 
		{
        	if ( getCurrentProject() != null ) {
        		getCurrentProject().getConfiguration().getDebuggerOptions().addBreakpoint( breakpoint );
        	}			
            UIUtils.invokeLater( new Runnable() {
                public void run() 
                {		    
                    tableModel.addBreakpoint( breakpoint );
                }
            });
		}
		
		public void onBreakpoint(IEmulator emulator, final Breakpoint breakpoint) 
		{
            UIUtils.invokeLater( new Runnable() {
                public void run() { 		    
                    int row = tableModel.getRow( breakpoint );
                    int viewRow = table.convertRowIndexToView( row );
                    table.getSelectionModel().setSelectionInterval( viewRow , viewRow );
                }
            });
		};
		
		public void breakpointChanged(IEmulator emulator, final Breakpoint breakpoint) 
		{
        	if ( getCurrentProject() != null ) {
        		getCurrentProject().getConfiguration().getDebuggerOptions().breakpointChanged(breakpoint);
        	}			
            UIUtils.invokeLater( new Runnable() {
                public void run() { 
                    tableModel.breakpointChanged( breakpoint );
                }
            });
		}
		
		public void breakpointDeleted(IEmulator emulator, final Breakpoint breakpoint) 
		{
        	if ( getCurrentProject() != null ) {
        		getCurrentProject().getConfiguration().getDebuggerOptions().deleteBreakpoint(breakpoint );
        	}				
            UIUtils.invokeLater( new Runnable() {
                public void run() { 
                    tableModel.deleteBreakpoint( breakpoint );
                }
            });
		}
	};
	
	protected final class MyTableModel extends AbstractTableModel {

		private final List<Breakpoint> breakPoints = new ArrayList<Breakpoint>();
		
		@Override
		public String getColumnName(int column) 
		{
			switch( column  ) 
			{
			case COL_BP_ENABLED:
				return "Enabled?";
			case COL_BP_ADDRESS:
				return "Address";
			case COL_BP_EXPRESSION:
				return "Expression";
			default:
				return "<unknown column>";
			}
		}
		
		public void clear() 
		{
			if ( ! breakPoints.isEmpty() ) {
				int size = breakPoints.size();
				breakPoints.clear();
				fireTableRowsDeleted(0, size-1 );
			}
		}
		
		public int getRow(Breakpoint breakpoint) 
		{
			for ( int index = 0 ; index < breakPoints.size() ; index++) {
				if ( breakPoints.get( index ).getAddress().equals( breakpoint.getAddress() ) ) {
					return index;
				}
			}
			throw new IllegalArgumentException("Unknown breakpoint: "+breakpoint);
		}
		
		public Breakpoint getBreakpoint(int modelRow) 
		{
			return breakPoints.get( modelRow );
		}		

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) 
		{
			final Breakpoint bp = getBreakpoint( rowIndex );

			switch( columnIndex ) 
			{
				case COL_BP_ENABLED:
					bp.setEnabled((Boolean) aValue);
					if ( getCurrentProject() != null ) {
						getCurrentProject().getConfiguration().getDebuggerOptions().breakpointChanged( bp );
					}					
					break;
				case COL_BP_EXPRESSION:
					if ( UNCONDITIONAL_BREAKPOINT.equals( aValue ) ) {
						return;
					}
					try {
						bp.setCondition((String) aValue);
						if ( getCurrentProject() != null ) {
							getCurrentProject().getConfiguration().getDebuggerOptions().breakpointChanged( bp );
						}
					} 
					catch (ParseException e) 
					{
						e.printStackTrace();
						return;
					}
					break;
				default: 
					throw new UnsupportedOperationException("Unhandled column edited: "+columnIndex);
			}
			emulator.breakpointChanged( bp );
		}
		
		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) 
		{
			switch( columnIndex ) {
				case COL_BP_ENABLED:
					/* fall-through */
				case COL_BP_EXPRESSION:
					return true;
				default: 
					return false;
			}
		}
		
		@Override
		public int getRowCount() {
			return breakPoints.size();
		}
		
		public void deleteBreakpoint(Breakpoint bp) 
		{
			final int idx = breakPoints.indexOf( bp );
			if ( idx != -1 ) {
				breakPoints.remove( bp );
				fireTableRowsDeleted( idx , idx );
			}
		}
		
		public void breakpointChanged(Breakpoint bp) 
		{
			final int idx = breakPoints.indexOf( bp );
			if ( idx != -1 ) {
				fireTableRowsUpdated( idx , idx );
			}
		}		
		
		public void addBreakpoint(Breakpoint bp) 
		{
			int pos = 0;
			for ( ; pos < breakPoints.size() ; pos++) 
			{
				if ( breakPoints.get( pos ).getAddress().isGreaterThan( bp.getAddress() ) ) {
					breakPoints.add( pos , bp );
					fireTableRowsInserted( pos , pos );
					return;
				}
			}
			pos = breakPoints.size();
			breakPoints.add( bp );
			fireTableRowsInserted( pos , pos );			
		}		

		@Override
		public int getColumnCount() {
			return 3;
		}
		
		@Override
		public Class<?> getColumnClass(int columnIndex) {
			switch( columnIndex ) 
			{
			case COL_BP_ENABLED:
				return Boolean.class;
			case COL_BP_ADDRESS:
				return String.class;
			case COL_BP_EXPRESSION:
				return String.class;
			default:
				return String.class;
			}
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) 
		{
			final Breakpoint bp = breakPoints.get( rowIndex );
			switch( columnIndex ) 
			{
			case COL_BP_ENABLED:
				return bp.isEnabled();
			case COL_BP_ADDRESS:
				return Misc.toHexString( bp.getAddress() );
			case COL_BP_EXPRESSION:
				return bp.hasCondition() ? bp.getCondition() : UNCONDITIONAL_BREAKPOINT;
			default:
				return "<unknown column>";
			}
		}
	};	

	public BreakpointView(DisassemblerView disView , SourceLevelDebugView sourceLevelDebugView, IEmulator emulator) {
		if ( emulator == null ) {
			throw new IllegalArgumentException("emulator must not be null");
		}
		if ( disView == null ) {
			throw new IllegalArgumentException("disView must not be null");
		}
		this.sourceLevelDebugView = sourceLevelDebugView; // may be NULL if we're debugging a plain object file without the source
		this.disView = disView;
		this.emulator = emulator;
		emulator.addEmulationListener( listener );
	}
	
	protected abstract IAssemblyProject getCurrentProject();
	
	@Override
	public void disposeHook() 
	{
		emulator.removeEmulationListener( listener );
	}

	@Override
	public void refreshDisplay() 
	{
		if ( getCurrentProject() != null ) 
		{
			emulator.deleteAllBreakpoints();
			tableModel.clear();
			
			for ( Breakpoint bp : getCurrentProject().getConfiguration().getDebuggerOptions().getBreakpoints() ) 
			{
				emulator.addBreakpoint( bp );
			}
		}			
	}

	@Override
	public String getTitle() {
		return "Breakpoints";
	}

	@Override
	public String getID() {
		return VIEW_ID;
	}

	@Override
	protected JPanel getPanel() 
	{
		if ( panel == null ) {
			panel = createPanel();
		}
		return panel;
	}

	private JPanel createPanel() 
	{
		table.setFillsViewportHeight( true );
		table.setFont( getMonospacedFont() );
		
		table.addMouseListener( new MouseAdapter() {
			
			public void mouseClicked(java.awt.event.MouseEvent e) {
				
				if ( e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 ) {
					
					final int viewRow = table.rowAtPoint( e.getPoint() );
					if ( viewRow != -1 ) {
						final int modelRow = table.convertRowIndexToModel( viewRow );
						final Breakpoint breakPoint = tableModel.getBreakpoint( modelRow );
						
						disView.setViewStartingAddress( breakPoint.getAddress() );
						if ( sourceLevelDebugView != null ) { // may be NULL when debugging a plain object file without attached source
						    sourceLevelDebugView.scrollToVisible( breakPoint.getAddress() );
						}
					}
				}
			};
		} );
		
		table.getActionMap().put("deleteRow" , new AbstractAction("deleteRow") {

			@Override
			public void actionPerformed(ActionEvent e) 
			{
				final int[] viewRows = table.getSelectedRows();
				final List<Breakpoint> toDelete = new ArrayList<>();
				for ( int viewRow : viewRows ) {
					final int modelRow = table.convertRowIndexToModel( viewRow );
					Breakpoint bp = tableModel.getBreakpoint( modelRow );
					toDelete.add( bp );
				}
				
				if ( getCurrentProject() != null ) 
				{
					final DebuggerOptions options = getCurrentProject().getConfiguration().getDebuggerOptions();
					for ( Breakpoint bp : toDelete ) 
					{
						options.deleteBreakpoint( bp );
					}
				}
				
				for ( Breakpoint bp : toDelete ) 
				{
					emulator.deleteBreakpoint( bp );					
				}
			}
		} );
		
		final KeyStroke stroke = KeyStroke.getKeyStroke( KeyEvent.VK_DELETE  , 0 );
		table.getInputMap().put( stroke , "deleteRow" );
		
		setColors( table );
		
		// setup scrollpane
		final JScrollPane pane = new JScrollPane( table );
        setColors( pane );        
        
        // setup result panel
        final JPanel panel = new JPanel();
        setColors( panel );
        panel.setLayout( new GridBagLayout() );        
        final GridBagConstraints cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
        panel.add( pane , cnstrs );
        return panel;		
	}
}
