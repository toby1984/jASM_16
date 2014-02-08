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
import java.awt.Event;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.CaretEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.ast.NumberNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.ast.SymbolReferenceNode;
import de.codesourcery.jasm16.compiler.Equation;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.Severity;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.compiler.io.DefaultResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.IWorkspaceListener;
import de.codesourcery.jasm16.ide.NavigationHistory;
import de.codesourcery.jasm16.ide.WorkspaceListener;
import de.codesourcery.jasm16.ide.ui.utils.ASTTableModelWrapper;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.viewcontainers.EditorContainer;
import de.codesourcery.jasm16.ide.ui.viewcontainers.ViewContainerManager;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Crude editor to test the compiler's inner workings.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class SourceEditorView extends SourceCodeView {

	private static final Logger LOG = Logger.getLogger(SourceEditorView.class);

	// UI widgets

	private volatile JPanel panel;

	private final ViewContainerManager viewContainerManager;

	private JFrame astInspector;
	private final JTree astTree = new JTree();

	private final JTable statusArea = new JTable();
	private final StatusModel statusModel = new StatusModel();

	private final JButton navigationHistoryBack = new JButton("Previous");
	private final JButton navigationHistoryForward = new JButton("Next");

	private final SymbolTableModel symbolTableModel = new SymbolTableModel();
	private final JTable symbolTable = new JTable( symbolTableModel );	

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
			if ( DefaultResourceMatcher.INSTANCE.isSame( deletedResource , getCurrentResource() ) ) 
			{
				dispose();
			}
		}

		public void buildFinished(IAssemblyProject project, boolean success) {
			if ( isASTInspectorVisible() ) {
				symbolTableModel.refresh();
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
		}

		@Override
		public int getRowCount()
		{
			return messages.size();
		}

		public StatusMessage getMessage(int modelRow) {
			return messages.get(modelRow);
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
		
		public void addWarning(String message)
		{
			addMessage( new StatusMessage(Severity.WARNING , message ) );
		} 		

		public void clearMessages()
		{
			messages.clear();
			fireTableDataChanged();
		}

	}

	protected void onCaretUpdate(CaretEvent e) 
	{
		// if AST inspector is visible, make sure the current AST node is visible
		// (scroll there if it isn't)
		if ( ! isASTInspectorVisible() ) {
			return;
		}

		final AST ast = getCurrentCompilationUnit() != null ? getCurrentCompilationUnit().getAST() : null;
		if ( ast == null ) {
			return;
		}

		final ASTNode n = ast.getNodeInRange( e.getDot() );
		if ( n != null ) {
			TreePath path = new TreePath( n.getPathToRoot() );
			astTree.setSelectionPath( path );
			astTree.scrollPathToVisible( path );
		}
	}

	private boolean isASTInspectorVisible() {
		return astInspector != null && astInspector.isVisible();
	}

	public SourceEditorView(IResourceResolver resourceResolver,IWorkspace workspace,
			ViewContainerManager viewContainerManager,NavigationHistory navigationHistory)

	{
		super( resourceResolver,workspace , navigationHistory , true );
		workspace.addWorkspaceListener( workspaceListener );
		this.viewContainerManager = viewContainerManager;
	}

	protected void setStatusMessage(String message) {
	}

	@Override
	protected final void setupKeyBindingsHook(final JTextPane editor) 
	{
		// 'Rename' action 
		addKeyBinding( editor , 
				KeyStroke.getKeyStroke(KeyEvent.VK_R,Event.ALT_MASK|Event.SHIFT_MASK),
				new AbstractAction() 
		{
			@Override
			public void actionPerformed(ActionEvent e) {
				maybeRenameLabel( editor.getCaretPosition() );
			}
		});
	}

	private void maybeRenameLabel(int caretPosition) 
	{
		final ICompilationUnit compilationUnit = getCurrentCompilationUnit();

		if ( compilationUnit == null || compilationUnit.getAST().hasErrors() ) {
			System.out.println("*** Renaming not supported for erronous compilation units");
			return;
		}

		final ASTNode node = compilationUnit.getAST().getNodeInRange( caretPosition );

		final ISymbol scopeOfSymbolToRename;
		final Identifier symbolToRename;
		if ( node instanceof LabelNode) 
		{
			symbolToRename = ((LabelNode) node).getIdentifier();
			scopeOfSymbolToRename = ((LabelNode) node).getScope();
		} 
		else if ( node instanceof SymbolReferenceNode) 
		{
			ISymbol resolved = ((SymbolReferenceNode) node).resolve( compilationUnit.getSymbolTable() );
			symbolToRename = resolved.getName();
			scopeOfSymbolToRename = resolved.getScope();
		} else {
			return;
		}

		final ISymbol oldSymbol = compilationUnit.getSymbolTable().getSymbol( symbolToRename , scopeOfSymbolToRename );
		if ( oldSymbol == null ) {
			System.out.println("*** Renaming symbols defined in other source files is currently not implemented, sorry. ***");
			return;
		}
		// TODO: Add support for renaming .equ definitions ...
		if ( !( oldSymbol instanceof Label ) ) {
			System.out.println("*** Only labels can currently be renamed, sorry. ***");
			return;
		}

		final ISymbol oldScope = oldSymbol.getScope();
		final Identifier oldIdentifier = oldSymbol.getName();

		String result = UIUtils.showInputDialog(null, "Please choose a new identifier","Enter a new identifier for '"+oldIdentifier.getRawValue()+"'");
		if ( StringUtils.isBlank(result) || ! Identifier.isValidIdentifier( result ) ) {
			return;
		}

		final Identifier newIdentifier;
		try {
			newIdentifier = new Identifier(result);
		} catch(ParseException e) {
			throw new RuntimeException(e);
		}

		// rename symbol (this will ALSO update the symbol's ITextRegion !)
		final ISymbol newSymbol = compilationUnit.getSymbolTable().renameSymbol( oldSymbol , newIdentifier );

		// gather all AST nodes that need to be updated
		final List<ASTNode> nodesRequiringUpdate = new ArrayList<>();

		final ISimpleASTNodeVisitor<ASTNode> simpleAstVisitor = new ISimpleASTNodeVisitor<ASTNode>()  {

			@Override
			public boolean visit(ASTNode node) 
			{
				if ( node instanceof LabelNode ) {
					final LabelNode label = (LabelNode) node;
					if ( ObjectUtils.equals( oldIdentifier , label.getIdentifier() ) &&
						 ObjectUtils.equals( oldScope, label.getScope() ) ) 
					{
						nodesRequiringUpdate.add( node );
					}
				} 
				else if ( node instanceof SymbolReferenceNode) 
				{
					final SymbolReferenceNode ref = (SymbolReferenceNode) node;
					final ISymbol symbol = ref.resolve( compilationUnit.getSymbolTable() );
					if ( symbol != null && 
							ObjectUtils.equals( oldIdentifier , symbol.getName() ) && 
							ObjectUtils.equals( oldScope , symbol.getScope() ) ) 
					{
						nodesRequiringUpdate.add( node );
					}
				}
				return true;
			}
		};

		ASTUtils.visitInOrder( compilationUnit.getAST() , simpleAstVisitor );

		Collections.sort( nodesRequiringUpdate , new Comparator<ASTNode>() {

			@Override
			public int compare(ASTNode n1, ASTNode n2) 
			{
				final ITextRegion r1 = n1.getTextRegion();
				final ITextRegion r2 = n2.getTextRegion();
				if ( r1 != null && r2 != null ) {
					if ( r1.getStartingOffset() < r2.getStartingOffset() ) {
						return -1;
					} 
					if ( r1.getStartingOffset() > r2.getStartingOffset() ) {
						return 1;
					}
					return 0;
				} 
				if ( r1 != null ) {
					return -1;
				} 
				if ( r2 != null )  {
					return 1;
				}
				return 0;
			}
		});

		// we now need to offset the location of EVERY AST node 
		// that has a location > oldSymbol.getLocation()
		final int lengthDelta;
		if (  newIdentifier.getRawValue().length() >= oldIdentifier.getRawValue().length() ) {
			lengthDelta = newIdentifier.getRawValue().length() - oldIdentifier.getRawValue().length();
		} else {
			// new identifier is shorter than the old one, need to adjust by a NEGATIVE offset
			lengthDelta = -( oldIdentifier.getRawValue().length() - newIdentifier.getRawValue().length() );
		}

		try {
			int currentOffsetAdjustment = 0;
			for ( ASTNode n : nodesRequiringUpdate ) 
			{
				if ( n instanceof LabelNode ) 
				{
					// update symbol in document
					final ITextRegion oldRegion = oldSymbol.getLocation();
					final TextRegion newRegion = new TextRegion( oldRegion.getStartingOffset()+currentOffsetAdjustment,
							oldRegion.getLength() );

					replaceText( newRegion , newIdentifier.getRawValue() );

					((LabelNode) n).setLabel( (Label) newSymbol );
					n.adjustTextRegion( currentOffsetAdjustment , lengthDelta ); 
					currentOffsetAdjustment += lengthDelta;
				} 
				else if ( n instanceof SymbolReferenceNode) 
				{
					((SymbolReferenceNode) n).setIdentifier( newIdentifier );

					final ITextRegion oldRegion = n.getTextRegion();
					final TextRegion newRegion = new TextRegion( oldRegion.getStartingOffset()+currentOffsetAdjustment,
							oldRegion.getLength() );
					replaceText( newRegion , newIdentifier.getRawValue() );

					n.adjustTextRegion( currentOffsetAdjustment , lengthDelta );
					currentOffsetAdjustment += lengthDelta;
				} else {
					throw new RuntimeException("Internal error,unhandled node type "+n);
				}
			}
		} finally {
			notifyDocumentChanged();
		}
	}

	private void showASTInspector() 
	{
		if ( astInspector == null ) {
			setupASTInspector();
		}

		if ( ! astInspector.isVisible() ) 
		{
			symbolTableModel.refresh();
			astInspector.setVisible( true );
		}
	}

	private static final int COL_SYMBOL_NAME = 0;
	private static final int COL_SYMBOL_VALUE = 1;
	private static final int COL_SYMBOL_RESOURCE = 2;	

	protected final class SymbolTableModel extends AbstractTableModel {

		private ISymbolTable getSymbolTable() 
		{
			ISymbolTable  result = getCurrentCompilationUnit().getSymbolTable();
			if ( result != null && result.getParent() != null ) {
				return result.getParent();
			}
			return result;
		}

		public void refresh() {
			fireTableStructureChanged();
		}

		@Override
		public int getRowCount()
		{
			ISymbolTable table = getSymbolTable();
			return table == null ? 0 : getSymbolTable().getSize();
		}

		@Override
		public int getColumnCount()
		{
			return 3;
		}

		private List<ISymbol> getSortedSymbols() {

			ISymbolTable table = getSymbolTable();
			List<ISymbol> all = table == null ? Collections.<ISymbol>emptyList() : table.getSymbols();
			Collections.sort( all , new Comparator<ISymbol>() {

				@Override
				public int compare(ISymbol o1, ISymbol o2)
				{
					return o1.getFullyQualifiedName().compareTo( o2.getFullyQualifiedName() );
				}
			} );
			return all;
		}

		private ISymbol getSymbolForRow(int modelRowIndex) {
			return getSortedSymbols().get( modelRowIndex );
		}

		@Override
		public String getColumnName(int column)
		{
			if ( column == COL_SYMBOL_NAME ) {
				return "Symbol name";
			} else if ( column == COL_SYMBOL_VALUE ) {
				return "Symbol value";
			} else if ( column == COL_SYMBOL_RESOURCE ) {
				return "Compilation unit";
			}
			throw new IllegalArgumentException("Internal error, unhandled column "+column);
		}

		@Override
		public Class<?> getColumnClass(int columnIndex)
		{
			return String.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			final ISymbol symbol = getSymbolForRow( rowIndex );
			if ( columnIndex == COL_SYMBOL_NAME ) {
				return symbol.getFullyQualifiedName();
			} 

			if ( columnIndex == COL_SYMBOL_VALUE ) 
			{
				if ( symbol instanceof Label ) {
					final Address address = ((Label) symbol).getAddress();
					return address == null ? "<no address assigned>" : address.toString();
				} else if ( symbol instanceof Equation ) {

					Equation eq = (Equation) symbol;
					Long value = eq.getValue( getSymbolTable() );
					return value == null ? "<failed to evaluate>" : value.toString();
				} 
				return "<unknown symbol type: "+symbol+">";
			}

			if ( columnIndex == COL_SYMBOL_RESOURCE ) {
				return symbol.getCompilationUnit();
			}
			throw new IllegalArgumentException("Internal error, unhandled column "+columnIndex);            
		}
	}

	private void setupASTInspector() 
	{
		astInspector = new JFrame("AST");

		final MouseAdapter treeMouseListener = new MouseAdapter() 
		{
			@Override
			public void mouseMoved(MouseEvent e) 
			{
				String text = null;
				TreePath path= astTree.getClosestPathForLocation( e.getX() , e.getY() );
				
				if ( path != null ) 
				{
					ASTNode node = (ASTNode) path.getLastPathComponent();
					if ( node instanceof InstructionNode) { // TODO: debug code, remove when done
						text = null;
					}
					try {
						text = getCurrentCompilationUnit().getSource( node.getTextRegion() );
					} catch (Exception ex) {
						text = "Node "+node.getClass().getSimpleName()+" has invalid text region "+node.getTextRegion();
					}
					text = "<HTML>"+text.replace("\n", "<BR>" )+"</HTML>";
				}
				if ( ! ObjectUtils.equals( astTree.getToolTipText() , text ) ) {
					astTree.setToolTipText( text );
				}
			}
		};
		astTree.addMouseMotionListener( treeMouseListener );
		astTree.setCellRenderer( new ASTTreeCellRenderer() ); 

		final JScrollPane pane = new JScrollPane( astTree );
		setColors( pane );
		pane.setPreferredSize( new Dimension(400,600) );

		GridBagConstraints cnstrs = constraints( 0, 0 , true , false , GridBagConstraints.REMAINDER );
		cnstrs.weighty = 0.9;
		panel.add( pane , cnstrs );

		// add symbol table 
		symbolTable.setFillsViewportHeight( true );
		MouseAdapter mouseListener = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if ( e.getButton() == MouseEvent.BUTTON1 ) 
				{
					int viewRow = symbolTable.rowAtPoint( e.getPoint() );
					if ( viewRow != -1 ) {
						final int modelRow = symbolTable.convertRowIndexToModel( viewRow );
						final ISymbol symbol = symbolTableModel.getSymbolForRow( modelRow );
						final int caretPosition = symbol.getLocation().getStartingOffset();

						IEditorView editor = null;
						if ( DefaultResourceMatcher.INSTANCE.isSame( symbol.getCompilationUnit().getResource() , getSourceFromMemory() ) ) {
							editor = SourceEditorView.this;
						} 
						else if ( getViewContainer() instanceof EditorContainer) 
						{
							final EditorContainer parent = (EditorContainer) getViewContainer();
							try {
								editor = parent.openResource( 
										workspace , getCurrentProject() ,
										symbol.getCompilationUnit().getResource() , caretPosition );
							} 
							catch (IOException e1) {
								LOG.error("mouseClicked(): Failed top open "+symbol.getCompilationUnit().getResource(),e1);
								return;
							}
						}
						if ( editor instanceof SourceCodeView) {
							((SourceCodeView) editor).moveCursorTo( caretPosition , true );
						}
					}
				}
			}
		};
		symbolTable.addMouseListener( mouseListener);

		final JScrollPane tablePane = new JScrollPane( symbolTable );
		setColors( tablePane );
		tablePane.setPreferredSize( new Dimension(400,200) );	

		cnstrs = constraints( 0, 1 , true , true , GridBagConstraints.REMAINDER );
		cnstrs.weighty = 0.1;
		panel.add( pane , cnstrs );   

		final JSplitPane split = new JSplitPane( JSplitPane.VERTICAL_SPLIT , pane , tablePane );
		setColors( split );

		// setup content pane
		astInspector.getContentPane().add( split );
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

		private String getSourceFor(ASTNode node) throws IOException {
			final ITextRegion range = node.getTextRegion();
			try {
				return range == null ? "<no source location>" : getCurrentCompilationUnit().getSource( range );
			} catch(StringIndexOutOfBoundsException e) {
				return "<node has invalid text range "+range+">";
			}
		}

		private String getLabelFor(ASTNode n) throws IOException 
		{
			String name = n.getClass().getSimpleName();
			ITextRegion range = n.getTextRegion();
			String source = getSourceFor( n );
			String txt = name+" "+source+" ( "+range+" )";  

			final Address address  = ASTUtils.getEarliestMemoryLocation( n );		
			final String sAddress = address == null ? "" : "0x"+Misc.toHexString( address.toWordAddress() );
			if ( n instanceof StatementNode ) 
			{
				final List<Line> linesForRange = getCurrentCompilationUnit().getLinesForRange( n.getTextRegion() );
				return sAddress+": Statement "+StringUtils.join( linesForRange , ",");
			} 
			if ( n instanceof AST ) {
				return "AST";
			} 
			if ( n instanceof OperatorNode ) {
				return "Operator "+((OperatorNode) n).getOperator();
			} 
			if ( n instanceof NumberNode ) {
				return "Number ("+((NumberNode) n).getValue()+")";
			}
			if ( n instanceof LabelNode ) 
			{
				final Label label = ((LabelNode) n).getLabel();
				return ( label.isLocalSymbol() ? "." : "" )+ label.getFullyQualifiedName();
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
	
	@Override
	protected void onCompilationWarning(ICompilationError error)
	{
		statusModel.addMessage( new StatusMessage( Severity.WARNING , error ) );
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

	@Override
	protected void onNavigationHistoryChange() 
	{
		navigationHistoryBack.setEnabled( canNavigationHistoryBack() );
		navigationHistoryForward.setEnabled( canNavigationHistoryForward() );
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
				final boolean currentlyVisible = isASTInspectorVisible();
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

		GridBagConstraints cnstrs = constraints( 0, 0 , false , true , GridBagConstraints.NONE );		
		toolbar.add( showASTButton , cnstrs );

		// navigation history back button
		cnstrs = constraints( 1, 0 , false , true , GridBagConstraints.NONE );		
		toolbar.add( navigationHistoryBack, cnstrs );
		navigationHistoryBack.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e)
			{
				navigationHistoryBack();
			}
		});

		navigationHistoryBack.setEnabled( false );

		// navigation history forward button
		cnstrs = constraints( 2, 0 , true , true  , GridBagConstraints.NONE );		
		toolbar.add( navigationHistoryForward , cnstrs );
		navigationHistoryForward.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e)
			{
				navigationHistoryForward();
			}
		});		
		navigationHistoryForward.setEnabled( false );

		// create status area
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
					final int viewRow = statusArea.rowAtPoint( e.getPoint() );
					if ( viewRow != -1 ) {
						final int modelRow = statusArea.convertRowIndexToModel( viewRow );
						StatusMessage message = statusModel.getMessage( modelRow );
						if ( message.getLocation() != null ) 
						{
							moveCursorTo( message.getLocation() , true );
						}
					}
				}
			};
		} );

		EditorContainer.addEditorCloseKeyListener( statusArea , this );

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

		final AncestorListener l = new AncestorListener() {

			@Override
			public void ancestorRemoved(AncestorEvent event) { }

			@Override
			public void ancestorMoved(AncestorEvent event) { }

			@Override
			public void ancestorAdded(AncestorEvent event) { 
				splitPane.setDividerLocation( 0.8d );
			}
		};
		panel.addAncestorListener( l );
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
	public String getID() {
		return "source-editor";
	}

	@Override
	protected JPopupMenu createPopupMenu(ASTNode node, int caretPosition,
			String currentSelection) 
	{
		final JPopupMenu popup = new JPopupMenu();
		boolean gotEntries = false;

		final ICompilationUnit unit = getCurrentCompilationUnit();

		if ( getCurrentProject() != null && unit != null &&
				unit.getAST() != null && ! unit.hasErrors() ) 
		{
			try 
			{
				if ( WorkspaceExplorer.canOpenInDebugPerspective( getCurrentProject() ) ) 
				{
					final IResource executable = getCurrentProject().getProjectBuilder().getExecutable();
					addMenuEntry( popup , "Open in debugger", new ActionListener() {

						@Override
						public void actionPerformed(ActionEvent e) {
							try {
								WorkspaceExplorer.openDebugPerspective( getCurrentProject() , executable ,
										viewContainerManager );
							} catch (IOException e1) {
								LOG.error("actionPerformed(): ",e1);
							} 
						}
					});
					gotEntries  = true;
				}
			} catch (IOException e) {
				LOG.error("createPopupMenu(): ",e);
			}
		}

		return gotEntries ? popup : null;
	}
}