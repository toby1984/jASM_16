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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.event.CaretEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.NumberNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.Severity;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.IWorkspaceListener;
import de.codesourcery.jasm16.ide.WorkspaceListener;
import de.codesourcery.jasm16.ide.ui.utils.ASTTableModelWrapper;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Crude editor to test the compiler's inner workings.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class SourceEditorView extends SourceCodeView {

	// UI widgets

	private volatile JPanel panel;
	
	private JFrame astInspector;
	private final JTree astTree = new JTree();
	
	private final JTable statusArea = new JTable();
	private final StatusModel statusModel = new StatusModel();
	
	// compiler
	private final IWorkspaceListener workspaceListener = new WorkspaceListener() {
		
		public void projectDeleted(IAssemblyProject deletedProject) 
		{
			if ( deletedProject.isSame( getCurrentProject() ) )
			{
				dispose();
			}
		}
		
		private void dispose() {
			if ( getViewContainer() != null ) {
				getViewContainer().disposeView( SourceEditorView.this );
			} else {
				SourceEditorView.this.dispose();
			}
		}
		
		public void resourceDeleted(IAssemblyProject project, IResource deletedResource) 
		{
			if ( deletedResource.isSame( fileResource ) ) 
			{
				dispose();
			}
		}
	};
	
	protected  static final class StatusMessage 
	{
		private final Severity severity;
		private final ITextRegion location;
		private final String message;
		@SuppressWarnings("unused")
		private final Throwable cause;
		private final ICompilationError error;

		public StatusMessage(Severity severity, String message)
		{
			this( severity , null , message , null ,null );
		}       

		public StatusMessage(Severity severity, ITextRegion location, String message)
		{
			this( severity , location , message , null ,null);
		}

		public StatusMessage(Severity severity, ICompilationError error)
		{
			this(severity,error.getLocation(),error.getMessage(),error,error.getCause());
		}

		public StatusMessage(Severity severity, ITextRegion location, String message, ICompilationError error,Throwable cause)
		{
			if ( severity == null ) {
				throw new IllegalArgumentException("severity must not be NULL.");
			}

			if (StringUtils.isBlank(message) ) {
				throw new IllegalArgumentException("message must not be NULL/blank.");
			}

			this.severity = severity;
			this.location = location;
			this.message = message;
			if ( cause == null ) {
				this.cause = error != null ? error.getCause() : null;  
			}  else {
				this.cause = cause;
			}
			this.error = error;
		}

		public StatusMessage(Severity severity, String message, Throwable e)
		{
			this(severity, null , message , null , e );
		}

		public Severity getSeverity()
		{
			return severity;
		}

		public ITextRegion getLocation()
		{
			return location;
		}

		public String getMessage()
		{
			return message;
		}

		public ICompilationError getError()
		{
			return error;
		}
	}


	protected class StatusModel extends AbstractTableModel 
	{
		private final List<StatusMessage> messages = new ArrayList<StatusMessage>();

		private final int COL_SEVERITY = 0;
		private final int COL_LOCATION = 1;
		private final int COL_MESSAGE = 2;

		public StatusModel() {
			super();
		}

		@Override
		public int getRowCount()
		{
			return messages.size();
		}

		public StatusMessage getMessage(int row) {
			return messages.get(row);
		}

		public void addMessage(StatusMessage msg) {
			if ( msg == null ) {
				throw new IllegalArgumentException("msg must not be NULL.");
			}
			int index = messages.size();
			messages.add( msg );
			fireTableRowsInserted( index, index );
		}

		public void setMessage(StatusMessage msg) 
		{
			if ( msg == null ) {
				throw new IllegalArgumentException("msg must not be NULL.");
			}
			messages.clear();
			messages.add( msg );
			fireTableDataChanged();
		}

		@Override
		public int getColumnCount()
		{
			return 3;
		}

		@Override
		public String getColumnName(int columnIndex)
		{
			switch(columnIndex) {
			case COL_SEVERITY:
				return "Severity";
			case COL_LOCATION:
				return "Location";
			case COL_MESSAGE:
				return "Message";
			default:
				return "no column name?";
			}
		}

		@Override
		public Class<?> getColumnClass(int columnIndex)
		{
			return String.class;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex)
		{
			return false;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			final StatusMessage msg = messages.get( rowIndex );
			switch(columnIndex) {
			case COL_SEVERITY:
				return msg.getSeverity().toString(); 
			case COL_LOCATION:
				if ( msg.getLocation() != null ) {
					SourceLocation location;
					try {
						location = getSourceLocation(msg.getLocation());
						return "Line "+location.getLineNumber()+" , column "+location.getColumnNumber();
					} catch (NoSuchElementException e) {
						// ok, can't help it
					}
				} 
				return "<unknown>";
			case COL_MESSAGE:
				return msg.getMessage();
			default:
				return "no column name?";
			}
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
			throw new UnsupportedOperationException("");
		}

		public void addError(String message, IOException e1)
		{
			addMessage( new StatusMessage(Severity.ERROR , message , e1 ) );
		}

		public void addInfo(String message)
		{
			addMessage( new StatusMessage(Severity.INFO , message ) );
		}        

		public void clearMessages()
		{
			messages.clear();
			fireTableDataChanged();
		}

	}

	protected void onCaretUpdate(CaretEvent e) 
	{
        ASTNode n = getCurrentCompilationUnit().getAST().getNodeInRange( e.getDot() );
        if ( n != null && astInspector != null && astInspector.isVisible() ) {
            TreePath path = new TreePath( n.getPathToRoot() );
            astTree.setSelectionPath( path );
            astTree.scrollPathToVisible( path );
        }
	}
	
	public SourceEditorView(IWorkspace workspace) 
	{
	    super( workspace , true );
		workspace.addWorkspaceListener( workspaceListener );
	}

	protected void setStatusMessage(String message) {

	}

	private void showASTInspector() 
	{
		if ( astInspector == null ) {
			setupASTInspector();
		}

		if ( ! astInspector.isVisible() ) {
			astInspector.setVisible( true );
		}
	}

	private void setupASTInspector() 
	{
		astInspector = new JFrame("AST");

		astTree.setCellRenderer( new ASTTreeCellRenderer() ); 
		final JScrollPane pane = new JScrollPane( astTree );
		setColors( pane );
		pane.setPreferredSize( new Dimension(400,600) );
		astInspector.getContentPane().add( pane );
		setColors( astInspector.getContentPane() );
		astInspector.setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
		astInspector.pack();
	}

	private class ASTTreeCellRenderer extends DefaultTreeCellRenderer {

		public ASTTreeCellRenderer() {
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree,
				Object value, boolean selected, boolean expanded,
				boolean leaf, int row, boolean hasFocus) 
		{
			final Component result = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
			if ( ! (value instanceof ASTNode)) {
				return result;
			}
			final ASTNode n = (ASTNode) value;
			String txt;
			try {
				txt = getLabelFor( n );
			} catch (IOException e) {
				txt = e.getMessage(); 
			}
			setText( txt );
			return result;
		}

		private String getLabelFor(ASTNode n) throws IOException 
		{
			String name = n.getClass().getSimpleName();
			ITextRegion range = n.getTextRegion();
			String source = range == null ? "<no source location>" : getCurrentCompilationUnit().getSource( range );
			String txt = name+" "+source+" ( "+n.getTextRegion()+" )";  

			final Address address  = ASTUtils.getEarliestMemoryLocation( n );		
			final String sAddress = address == null ? "" : "0x"+Misc.toHexString( address );
			if ( n instanceof StatementNode ) 
			{
				final List<Line> linesForRange = getCurrentCompilationUnit().getLinesForRange( n.getTextRegion() );
				return sAddress+": Statement "+StringUtils.join( linesForRange , ",");
			} else if ( n instanceof AST ) {
				return "AST";
			} else if ( n instanceof OperatorNode ) {
				return "Operator "+((OperatorNode) n).getOperator();
			} else if ( n instanceof NumberNode ) {
				return "Number ("+((NumberNode) n).getValue()+")";
			} 
			return txt;
		}
	} 

	@Override
	protected void onSourceCodeValidation()
	{
	    statusModel.clearMessages();
	}

	@Override
	protected void onHighlightingStart()
	{
        final ASTTableModelWrapper astModel = new ASTTableModelWrapper( getCurrentCompilationUnit().getAST() ) ;
        astTree.setModel( astModel );
	}

	
	
	@Override
	protected void onCompilationError(ICompilationError error)
	{
       statusModel.addMessage( new StatusMessage( Severity.ERROR , error ) );
	}

	// ============= view creation ===================

	@Override
	public JPanel getPanel()
	{
		if ( panel == null ) {
			panel = createPanel();
		}
		return panel;
	}

	protected JPanel createPanel() 
	{
		// button panel
		final JToolBar toolbar = new JToolBar();
        toolbar.setLayout( new GridBagLayout() );
		setColors( toolbar );

		final JButton showASTButton = new JButton("Show AST" );
		setColors( showASTButton );

		showASTButton.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) 
			{
				boolean currentlyVisible = astInspector != null ? astInspector.isVisible() : false;
				if ( currentlyVisible ) {
					showASTButton.setText("Show AST");
				} else {
					showASTButton.setText("Hide AST");
				}
				if ( currentlyVisible ) {
					astInspector.setVisible( false );
				} else {
					showASTInspector();
				}

			}
		} );

        GridBagConstraints cnstrs = constraints( 0, 0 , GridBagConstraints.NONE );		
		toolbar.add( showASTButton , cnstrs );

		statusArea.setPreferredSize( new Dimension(400, 100 ) );
		statusArea.setModel( statusModel );
		setColors( statusArea ); 

		/**
		 * TOOLBAR
		 * SOURCE
		 * cursor position
		 * status area 
		 */
        final JPanel topPanel = new JPanel();		
		topPanel.setLayout( new GridBagLayout() );

		cnstrs = constraints( 0, 0 , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.weighty = 0;
		topPanel.add( toolbar , cnstrs );
		
        cnstrs = constraints( 0, 1 , GridBagConstraints.BOTH );
        cnstrs.gridwidth = GridBagConstraints.REMAINDER;
        cnstrs.weighty=1.0;
        topPanel.add( super.getPanel() , cnstrs );
        

		final JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout( new GridBagLayout() );

		statusArea.setAutoResizeMode( JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS );

		statusArea.addMouseListener( new MouseAdapter() {

			public void mouseClicked(java.awt.event.MouseEvent e) 
			{
				if ( e.getButton() == MouseEvent.BUTTON1 ) {
					final int row = statusArea.rowAtPoint( e.getPoint() );
					StatusMessage message = statusModel.getMessage( row );
					if ( message.getLocation() != null ) 
					{
						moveCursorTo( message.getLocation() );
					}
				}
			};
		} );

		statusArea.setFillsViewportHeight( true );
		statusArea.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		final JScrollPane statusPane = new JScrollPane( statusArea );
		setColors( statusPane ); 

		statusPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		statusPane.setPreferredSize( new Dimension(400,100 ) );
		statusPane.setMinimumSize(new Dimension(100, 20));      

		cnstrs = constraints( 0, 0 , GridBagConstraints.BOTH );
		cnstrs.weightx=1;
		cnstrs.weighty=1;
		cnstrs.gridwidth = GridBagConstraints.REMAINDER; 
		cnstrs.gridheight = GridBagConstraints.REMAINDER; 

		bottomPanel.add( statusPane , cnstrs );

		// setup result panel
		final JSplitPane splitPane = new JSplitPane( JSplitPane.VERTICAL_SPLIT , topPanel , bottomPanel );
		setColors( splitPane );

		final JPanel panel = new JPanel();
		panel.setLayout( new GridBagLayout() );
		setColors( panel );  
		cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
		panel.add( splitPane , cnstrs );
		return panel;
	}

	@Override
	public void disposeHook2()
	{
		workspace.removeWorkspaceListener( workspaceListener );
		if ( astInspector != null ) 
		{
			astInspector.setVisible( false );
			astInspector.dispose();
		}
	}

	@Override
	public IEditorView getOrCreateEditor(IAssemblyProject project, IResource resource) 
	{
		if ( resource.hasType( ResourceType.SOURCE_CODE ) ) {
			return new SourceEditorView(this.workspace);
		}
		throw new IllegalArgumentException("Unsupported resource type: "+resource);
	}

	@Override
	public String getID() {
		return "source-editor";
	}

}