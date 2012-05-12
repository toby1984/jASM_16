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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.CommentNode;
import de.codesourcery.jasm16.ast.EquationNode;
import de.codesourcery.jasm16.ast.IncludeBinaryFileNode;
import de.codesourcery.jasm16.ast.InitializedMemoryNode;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.NumberNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.OriginNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.ast.SymbolReferenceNode;
import de.codesourcery.jasm16.ast.UninitializedMemoryNode;
import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.Severity;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.compiler.io.AbstractResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.ui.utils.ASTTableModelWrapper;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Crude editor to test the compiler's inner workings.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class SourceEditorView extends AbstractView implements IEditorView {

	private static final Logger LOG = Logger.getLogger(SourceEditorView.class);

	// time to wait until recompiling after the user edited the source code
	private static final int RECOMPILATION_DELAY_MILLIS = 300;

	// UI widgets

	private volatile JPanel panel;
	private JFrame astInspector;
	private final JTree astTree = new JTree();
	private final JTable statusArea = new JTable();
	private final StatusModel statusModel = new StatusModel();

	private final JTextField cursorPosition = new JTextField(); 
	private final JTextPane editorPane = new JTextPane();
	private volatile int documentListenerDisableCount = 0; 
	private JScrollPane editorScrollPane;  

	private final SimpleAttributeSet registerStyle;    
	private final SimpleAttributeSet commentStyle;     
	private final SimpleAttributeSet instructionStyle;     
	private final SimpleAttributeSet labelStyle;
	private final SimpleAttributeSet preProcessorStyle;

	private final SimpleAttributeSet errorStyle;
	private final SimpleAttributeSet defaultStyle;

	// compiler
	private IAssemblyProject project;
	private String initialHashCode; // hash code used to check whether current editor content differs from the one on disk
	private IResource fileResource; // source code on disk
	private IResource documentResource; // possibly edited source code (in RAM / JEditorPane)
	
	private ICompilationUnit compilationUnit;

	private CompilationThread compilationThread = null;

	/*
	 * 
	 *  WAIT_FOR_EDIT-------> WAIT_FOR_TIMEOUT ------>( do compilation ) ----+
	 *     ^    ^                     |                                      |
	 *     |    |                     |                                      |
	 *     |    +---RESTART_TIMEOUT---+                                      |
	 *     +-----------------------------------------------------------------+
	 * 
	 */
	private enum WaitState {
		WAIT_FOR_EDIT,
		WAIT_FOR_TIMEOUT,
		RESTART_TIMEOUT;
	}

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

	protected class CompilationThread extends Thread {

		private final Object LOCK = new Object();

		// @GuardedBy( LOCK )
		private WaitState currentState = WaitState.WAIT_FOR_EDIT;

		public CompilationThread() {
			setDaemon( true );
		}

		@Override
		public void run()
		{
			while( true )
			{
				try {
					internalRun();
				} 
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		}

		private void internalRun() throws InterruptedException, InvocationTargetException
		{
			synchronized( LOCK ) 
			{
				switch( currentState ) 
				{
				case WAIT_FOR_EDIT:
					LOCK.wait();
					return;
				case RESTART_TIMEOUT:
					currentState = WaitState.WAIT_FOR_TIMEOUT; // $FALL-THROUGH$
					return;
				case WAIT_FOR_TIMEOUT:
					LOCK.wait( RECOMPILATION_DELAY_MILLIS );
					if ( currentState != WaitState.WAIT_FOR_TIMEOUT ) {
						return;
					}
					try {
						SwingUtilities.invokeAndWait( new Runnable() {

							@Override
							public void run()
							{
								try {
									validateSourceCode();
								} catch (IOException e) {
									e.printStackTrace();
								} finally {

								}
							}
						} );
					} finally {
						currentState = WaitState.WAIT_FOR_EDIT;
					}                        
				} 
			}
		}

		public void documentChanged() 
		{
			synchronized( LOCK ) 
			{
				currentState = WaitState.RESTART_TIMEOUT;
				LOCK.notifyAll();
			}
		}
	}

	private DocumentListener recompilationListener = new DocumentListener() {

		private void textChanged(DocumentEvent e) 
		{
			updateTitle();
			
			if ( compilationThread == null ) 
			{
				compilationThread = new CompilationThread();
				compilationThread.start();
			} 
			compilationThread.documentChanged();
		}

		@Override
		public void removeUpdate(DocumentEvent e) { textChanged(e); }        

		@Override
		public void insertUpdate(DocumentEvent e) { textChanged(e); }

		@Override
		public void changedUpdate(DocumentEvent e) 
		{
			// do nothing, style change only
		}
	};

	private final CaretListener listener = new CaretListener() {

		@Override
		public void caretUpdate(CaretEvent e) 
		{
			if ( compilationUnit != null && compilationUnit.getAST() != null && compilationUnit.getAST().getTextRegion() != null ) 
			{
				try {
					final SourceLocation location = getSourceLocation( e.getDot() );
					cursorPosition.setHorizontalAlignment( JTextField.RIGHT );
					cursorPosition.setText( "Line "+location.getLineNumber()+" , column "+location.getColumnNumber()+" (offset "+e.getDot()+")");
				} catch(NoSuchElementException e2) {
					// ok, user clicked on unknown location
				}
				ASTNode n = compilationUnit.getAST().getNodeInRange( e.getDot() );
				if ( n != null && astInspector != null && astInspector.isVisible() ) {
					TreePath path = new TreePath( n.getPathToRoot() );
					astTree.setSelectionPath( path );
					astTree.scrollPathToVisible( path );
				}
			}
		}
	};

	public SourceEditorView() 
	{
		defaultStyle = new SimpleAttributeSet();
		errorStyle = createStyle( Color.RED );
		registerStyle = createStyle( Color.ORANGE );   
		commentStyle = createStyle( Color.DARK_GRAY );
		instructionStyle = createStyle( Color.BLUE );
		labelStyle = createStyle( Color.GREEN );
		preProcessorStyle = createStyle( new Color( 200 , 200 , 200 ) ); 
	}

	private static SimpleAttributeSet createStyle(Color color) 
	{
		SimpleAttributeSet result = new SimpleAttributeSet();
		StyleConstants.setForeground( result , color );
		return result;
	}

	private SourceLocation getSourceLocation(ITextRegion range) {
		return getSourceLocation( range.getStartingOffset() );
	}

	private SourceLocation getSourceLocation(int offset) {
		final Line line = compilationUnit.getLineForOffset( offset);
		return new SourceLocation( compilationUnit , line , new TextRegion( offset , 0 ) );
	}

	private GridBagConstraints constraints(int x,int y,int fill) {
		GridBagConstraints result = new GridBagConstraints();
		result.fill=fill;
		result.weightx=1.0;
		result.weighty=1.0;
		result.gridheight=1;
		result.gridwidth=1;
		result.gridx=x;
		result.gridy=y;
		result.insets = new Insets(1,1,1,1);
		return result;
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
			String source = range == null ? "<no source location>" : compilationUnit.getSource( range );
			String txt = name+" "+source+" ( "+n.getTextRegion()+" )";  

			if ( n instanceof StatementNode ) 
			{
				final List<Line> linesForRange = compilationUnit.getLinesForRange( n.getTextRegion() );
				return "Statement "+StringUtils.join( linesForRange , ",");
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

	protected String getTextFromTextPane() 
	{
		final int len = editorPane.getDocument().getLength();
		if ( len == 0 ) {
			return "";
		}
		try {
			return editorPane.getDocument().getText( 0 , len );
		} catch (BadLocationException e) {
			throw new RuntimeException("bad location: ",e);
		}
	}

	private void openFile(IAssemblyProject project, final IResource sourceFile) throws IOException 
	{
		if ( project == null ) {
			throw new IllegalArgumentException("project must not be NULL");
		}
		if (sourceFile == null) {
			throw new IllegalArgumentException("sourceFile must not be NULL");
		}
		if ( ! sourceFile.hasType( ResourceType.SOURCE_CODE ) ) {
			throw new IllegalArgumentException("Not a source file: "+sourceFile);
		}
		this.project = project;
		this.fileResource = sourceFile;

		final String source = Misc.readSource( sourceFile );
		this.initialHashCode = Misc.calcHash( source );
		
		documentResource = new AbstractResource(ResourceType.SOURCE_CODE) {

			@Override
			public String readText(ITextRegion range) throws IOException
			{
				return range.apply( getTextFromTextPane() );
			}

			@Override
			public String getIdentifier()
			{
				return sourceFile.getIdentifier();
			}

			@Override
			public long getAvailableBytes() throws IOException
			{
				return editorPane.getDocument().getLength();
			}

			@Override
			public OutputStream createOutputStream(boolean append) throws IOException
			{
				throw new UnsupportedOperationException("Cannot save to "+this);
			}

			@Override
			public InputStream createInputStream() throws IOException
			{
				return new ByteArrayInputStream( getTextFromTextPane().getBytes() );
			}

            @Override
            public boolean isSame(IResource other)
            {
                if ( other == this ) {
                    return true;
                }
                return false;
            }
		};

		disableDocumentListener();

		final Document doc = editorPane.getDocument();
		doc.putProperty(Document.StreamDescriptionProperty, null);

		editorPane.setText( source );

		if ( panel != null ) {
			validateSourceCode();
		}

		enableDocumentListener();		
	}

	private void validateSourceCode() throws IOException {

		disableDocumentListener();
		try 
		{
			clearCompilationErrors();
			statusModel.clearMessages();
			compilationUnit = project.getBuilder().parse( documentResource , new CompilationListener() );
			doHighlighting( compilationUnit , true );
		} finally {
			enableDocumentListener();
		}
	}

	protected void doHighlighting(ICompilationUnit unit,boolean addStatusMessages) 
	{
		if ( panel == null ) {
			return;
		}

		if ( unit.getAST() != null ) 
		{
			final ASTTableModelWrapper astModel = new ASTTableModelWrapper( compilationUnit.getAST() ) ;
			astTree.setModel( astModel );

			doSemanticHighlighting( unit );
		}

		if ( unit.hasErrors() ) {
			statusModel.addInfo("Compilation stopped with errors");
			showCompilationErrors( compilationUnit , addStatusMessages );
		}  
	}

	private void doSemanticHighlighting(ICompilationUnit unit)
	{
		if ( unit.getAST() == null ) {
			return;
		}

		// changing character styles triggers
		// change events that in turn would
		// again trigger recompilation...we don't want that...
		disableDocumentListener();

		try {
			final ITextRegion visible = getVisibleTextRegion();
			if ( visible != null ) {
				final List<ASTNode> nodes = unit.getAST().getNodesInRange( visible );
				for ( ASTNode child : nodes ) 
				{
					doSemanticHighlighting( unit , child );
				}
			}
		} finally {
			enableDocumentListener();
		}
	}

	private void doSemanticHighlighting(ICompilationUnit unit, ASTNode node)
	{
		highlight( node );
		for ( ASTNode child : node.getChildren() ) {
			doSemanticHighlighting( unit , child );
		}
	}

	private void highlight(ASTNode node) 
	{
		if ( node instanceof InstructionNode ) 
		{
			ITextRegion children = null;
			for ( ASTNode child : node.getChildren() ) 
			{
				if ( children == null ) {
					children = child.getTextRegion();
				} else {
					children.merge( child.getTextRegion() );
				}
			}
			ITextRegion whole = new TextRegion( node.getTextRegion() );
			whole.subtract( children );
			highlight( whole , instructionStyle );
		} 
		else if ( node instanceof EquationNode || 
				node instanceof UninitializedMemoryNode ||
				node instanceof InitializedMemoryNode ||
				node instanceof OriginNode ||
				node instanceof IncludeBinaryFileNode) 
		{
			highlight( node , preProcessorStyle );
		}

		if ( node instanceof SymbolReferenceNode ) {
			highlight( node , labelStyle );
		} else if ( node instanceof CommentNode ) {
			highlight( node , commentStyle );
		} else if ( node instanceof RegisterReferenceNode ) {
			highlight( node , registerStyle );
		}
	}

	private void highlight(ASTNode node, AttributeSet attributes) 
	{
		highlight( node.getTextRegion() , attributes );
	}

	private void highlight(ITextRegion range, AttributeSet attributes) 
	{
		editorPane.getStyledDocument().setCharacterAttributes( range.getStartingOffset() , range.getLength() , attributes , true );
	}

	private void moveCursorTo(ITextRegion location) 
	{
		if ( compilationUnit == null || compilationUnit.getAST() == null ) {
			return;
		}
		editorPane.setCaretPosition( location.getStartingOffset() );
		centerCurrentLineInScrollPane();
		editorPane.requestFocus();
	}

	public void centerCurrentLineInScrollPane()
	{
		final Container container = SwingUtilities.getAncestorOfClass(JViewport.class, editorPane);

		if (container == null) {
			return;
		}

		try {
			final Rectangle r = editorPane.modelToView(editorPane.getCaretPosition());
			final JViewport viewport = (JViewport) container;
			final int extentHeight = viewport.getExtentSize().height;
			final int viewHeight = viewport.getViewSize().height;

			int y = Math.max(0, r.y - (extentHeight / 2));
			y = Math.min(y, viewHeight - extentHeight);

			viewport.setViewPosition(new Point(0, y));
		} 
		catch (BadLocationException ble) {
		}
	}

	private ITextRegion getVisibleTextRegion() 
	{
		final Point startPoint = editorScrollPane.getViewport().getViewPosition();
		final Dimension size = editorScrollPane.getViewport().getExtentSize();

		final Point endPoint = new Point(startPoint.x + size.width, startPoint.y + size.height);
		try {
			final int start = editorPane.viewToModel( startPoint );
			final int end = editorPane.viewToModel( endPoint );
			final int len = end-start;
			if ( len < 0  || start < 0) {
				return null;
			}
			return new TextRegion( start , len );
		} 
		catch(NullPointerException e) 
		{
			System.out.println("startPoint: "+startPoint+" / size: "+size);
			e.printStackTrace();
			return null;
		}
	}

	private void clearCompilationErrors() 
	{
		disableDocumentListener();		
		try {
			final StyledDocument doc = editorPane.getStyledDocument();
			doc.setCharacterAttributes( 0 , doc.getLength() , defaultStyle , true );
		} finally {
			enableDocumentListener();
		}
	}

	private void disableDocumentListener() 
	{
		documentListenerDisableCount++;		
		editorPane.getDocument().removeDocumentListener( recompilationListener );
	}

	private void enableDocumentListener() 
	{
		documentListenerDisableCount--;		
		if ( documentListenerDisableCount == 0) 
		{
			editorPane.getDocument().addDocumentListener( recompilationListener );
		}
	}   

	private void showCompilationErrors(ICompilationUnit unit,boolean addStatusMessages) 
	{
		disableDocumentListener();

		try 
		{
			for ( ICompilationError error : unit.getErrors() ) 
			{
				final ITextRegion location;
				if ( error.getLocation() != null ) {
					location = error.getLocation();
				} 
				else 
				{
					if ( error.getErrorOffset() != -1 ) {
						location = new TextRegion( error.getErrorOffset(), 1 );
					} else {
						location = null;
					}
				}

				if ( location != null ) 
				{
					System.out.println("Highlighting error at "+location);
					highlight( location , errorStyle );
				}

				if ( addStatusMessages ) {
					statusModel.addMessage( new StatusMessage( Severity.ERROR , error ) );
				}
			}
		} finally {
			enableDocumentListener();
		}
	}

	// ============= view creation ===================

	@Override
	public JPanel getPanel()
	{
		if ( panel == null ) {
			panel = createPanel();
			if ( this.fileResource != null ) {
				try {
					validateSourceCode();
				} catch (IOException e) {
					LOG.error("getPanel(): ",e);
				}
			}
		}
		return panel;
	}

	protected JPanel createPanel() 
	{
		disableDocumentListener(); // necessary because setting colors on editor pane triggers document change listeners (is considered a style change...)
		try {
			editorPane.setCaretColor( Color.WHITE );
			setupKeyBindings( editorPane );
			setColors( editorPane );
			editorScrollPane = new JScrollPane(editorPane);
			setColors( editorScrollPane );
			editorPane.addCaretListener( listener );
		} finally {
			enableDocumentListener();
		}

		editorScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		editorScrollPane.setPreferredSize( new Dimension(400,600 ) );
		editorScrollPane.setMinimumSize(new Dimension(100, 100));

		final AdjustmentListener adjustmentListener = new AdjustmentListener() {

			@Override
			public void adjustmentValueChanged(AdjustmentEvent e) 
			{
				if ( ! e.getValueIsAdjusting() ) 
				{
					if ( compilationUnit != null ) {
						doHighlighting( compilationUnit , false );
					}
				}
			}
		};
		editorScrollPane.getVerticalScrollBar().addAdjustmentListener( adjustmentListener );
		editorScrollPane.getHorizontalScrollBar().addAdjustmentListener( adjustmentListener );

		// button panel
		final JPanel topPanel = new JPanel();

		final JToolBar toolbar = new JToolBar();

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

		toolbar.add( showASTButton );

		cursorPosition.setSize( new Dimension(400,15) );
		cursorPosition.setEditable( false );
		setColors( cursorPosition ); 

		statusArea.setPreferredSize( new Dimension(400, 100 ) );
		statusArea.setModel( statusModel );
		setColors( statusArea ); 

		/**
		 * TOOLBAR
		 * SOURCE
		 * cursor position
		 * status area 
		 */
		topPanel.setLayout( new GridBagLayout() );

		GridBagConstraints cnstrs = constraints( 0, 0 , GridBagConstraints.HORIZONTAL );
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		cnstrs.weighty = 0;
		topPanel.add( toolbar , cnstrs );

		cnstrs = constraints( 0, 1 , GridBagConstraints.BOTH );
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;        
		topPanel.add( editorScrollPane , cnstrs );

		cnstrs = constraints( 0, 2 , GridBagConstraints.HORIZONTAL);
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;        
		cnstrs.weighty = 0;
		topPanel.add( cursorPosition , cnstrs ); 

		cnstrs = constraints( 0, 3 , GridBagConstraints.HORIZONTAL);
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;      
		cnstrs.weighty = 0;

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

	private void setupKeyBindings(JTextPane editor) 
	{
		// 'Save' action 
		addKeyBinding( editor , 
				KeyStroke.getKeyStroke(KeyEvent.VK_S,Event.CTRL_MASK),
				new AbstractAction() 
		{
			@Override
			public void actionPerformed(ActionEvent e) {
				saveCurrentFile();
			}
		});
	}
	
	private void saveCurrentFile() {
		
		final String source = getTextFromTextPane();
		try {
			Misc.writeResource( getCurrentResource() , source );
			this.initialHashCode = Misc.calcHash( source );
			updateTitle();
		} catch (IOException e1) {
			LOG.error("save(): Failed to write to "+getCurrentResource());
		}
	}

	public IAssemblyProject getCurrentProject() {
		return project;
	}

	public IResource getCurrentResource() {
		return this.fileResource;
	}

	@Override
	public void dispose()
	{
		if ( astInspector != null ) 
		{
			astInspector.setVisible( false );
			astInspector.dispose();
		}
	}

	@Override
	public void refreshDisplay()
	{
		try {
			validateSourceCode();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getTitle() 
	{
		if ( getCurrentResource() == null ) {
			return "source view";
		}
		return ( hasUnsavedContent() ? "*" : "" )+getCurrentResource().getIdentifier();
	}

	@Override
	public IEditorView getOrCreateEditor(IAssemblyProject project, IResource resource) 
	{
		if ( resource.hasType( ResourceType.SOURCE_CODE ) ) {
			return new SourceEditorView();
		}
		throw new IllegalArgumentException("Unsupported resource type: "+resource);
	}

	@Override
	public boolean hasUnsavedContent() 
	{
		if ( this.fileResource == null ) {
			return false;
		}
		return ! initialHashCode.equals( Misc.calcHash( getTextFromTextPane() ) );
	}

	@Override
	public boolean mayBeDisposed() {
		return ! hasUnsavedContent();
	}

	@Override
	public void openResource(IAssemblyProject project, IResource resource) throws IOException 
	{
		if ( this.project != project || this.fileResource != resource ) {
			openFile( project , resource );
		}
	}

	private void updateTitle() 
	{
		final String title = ( hasUnsavedContent() ? "*" : "")+getCurrentResource().getIdentifier();
		getViewContainer().setTitle( SourceEditorView.this , title );				
	}

	@Override
	public String getID() {
		return "source-editor";
	}

}