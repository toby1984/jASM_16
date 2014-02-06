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
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentEvent.EventType;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AbstractDocument.DefaultDocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultStyledDocument.AttributeUndoableEdit;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.View;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.CommentNode;
import de.codesourcery.jasm16.ast.EndMacroNode;
import de.codesourcery.jasm16.ast.IPreprocessorDirective;
import de.codesourcery.jasm16.ast.IncludeSourceFileNode;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.InvokeMacroNode;
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.StartMacroNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.ast.SymbolReferenceNode;
import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.Severity;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.compiler.io.AbstractResourceResolver;
import de.codesourcery.jasm16.compiler.io.DefaultResourceMatcher;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.compiler.phases.ExpandMacrosPhase;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.IWorkspaceListener;
import de.codesourcery.jasm16.ide.NavigationHistory;
import de.codesourcery.jasm16.ide.NavigationHistory.INavigationHistoryListener;
import de.codesourcery.jasm16.ide.NavigationHistory.Location;
import de.codesourcery.jasm16.ide.WorkspaceListener;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.viewcontainers.EditorContainer;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Abstract base-class for views that display source code.
 * 
 * <p>This class provides common functionality like
 * syntax-highlighing , navigation history and so forth.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class SourceCodeView extends AbstractView implements IEditorView {

	private static final Logger LOG = Logger.getLogger(SourceCodeView.class);

	// time to wait before recompiling after the user changed the source code
	private static final int RECOMPILATION_DELAY_MILLIS = 300;

	// UI widgets
	private volatile JPanel panel;

	private Object currentHighlight;    

	private boolean registeredWithTooltipManager = false;
	private volatile boolean editable;
	private SearchDialog searchDialog;

	private Object currentUnderlineHighlight;

	private final NavigationHistory navigationHistory;

	private final PopupListener popupListener = new PopupListener();
	private final UndoManager undoManager = new UndoManager();
	private final UndoableEditListener undoListener = new  UndoableEditListener() 
	{
		public void undoableEditHappened(UndoableEditEvent e) 
		{
			UndoableEdit edit = e.getEdit();
			if ( edit instanceof AttributeUndoableEdit) {
				return;
			}
			else if ( edit instanceof DefaultDocumentEvent) {
				if ( ((DefaultDocumentEvent) edit).getType() == EventType.CHANGE ) {
					return;
				}
			}
			undoManager.addEdit(e.getEdit());
			undoAction.updateUndoState();
			redoAction.updateRedoState();
		}
	};

	protected abstract class UndoRedoAction extends AbstractAction {

		public void updateUndoState() {
			if (undoManager.canUndo()) {
				setEnabled(true);
				putValue(Action.NAME, undoManager.getUndoPresentationName());
			} else {
				setEnabled(false);
				putValue(Action.NAME, "Undo");
			}
		}   

		public void updateRedoState() 
		{
			if (undoManager.canRedo()) {
				setEnabled(true);
				putValue(Action.NAME, undoManager.getRedoPresentationName() );
			} else {
				setEnabled(false);
				putValue(Action.NAME, "Redo");
			}
		}           
	}

	private final UndoRedoAction undoAction = new UndoRedoAction() {

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				undoManager.undo();
			} catch (CannotUndoException ex) {
				LOG.error("Unable to undo: " + ex,ex);
			}
			updateUndoState();
			redoAction.updateRedoState();           
		}
	};

	private final UndoRedoAction redoAction = new UndoRedoAction() {

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				undoManager.redo();
			} catch (CannotRedoException ex) {
				LOG.error("Unable to redo: " + ex,ex);
			}
			updateRedoState();
			undoAction.updateUndoState();
		}

	};

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

	private final SimpleAttributeSet macroDefinitionStyle;
	private final SimpleAttributeSet macroInvocationStyle;

	// compiler
	private final IResourceResolver resourceResolver;
	protected final IWorkspace workspace;

	private final INavigationHistoryListener navigationHistoryListener = new INavigationHistoryListener() {

		@Override
		public void navigationHistoryChanged()
		{
			UIUtils.invokeLater( new Runnable() {

				@Override
				public void run()
				{
					SourceCodeView.this.onNavigationHistoryChange();
				}

			} );
		}
	};
	private volatile boolean navigationHistoryUpdatesEnabled = true; // controls whether the CaretListener will forward caret position changes to the NavigationHistory
	private volatile boolean isBuilding = false;

	private final IWorkspaceListener workspaceListener = new WorkspaceListener() {

		public void projectDeleted(IAssemblyProject deletedProject) 
		{
			if ( deletedProject.isSame( project ) )
			{
				dispose();
			}
		}

		public void buildStarted(IAssemblyProject project) 
		{
			if ( project.isSame( getCurrentProject() ) ) 
			{
				isBuilding = true;
			}
		}

		public void buildFinished(IAssemblyProject project, boolean success) {
			if ( project.isSame( getCurrentProject() ) ) {
				isBuilding = false;
			}        	
		};

		public void projectClosed(IAssemblyProject closedProject) 
		{
			if ( closedProject.isSame( project ) )
			{
				dispose();
			}
		}        

		private void dispose() {
			if ( getViewContainer() != null ) {
				getViewContainer().disposeView( SourceCodeView.this );
			} else {
				SourceCodeView.this.dispose();
			}
		}

		public void resourceDeleted(IAssemblyProject project, IResource deletedResource) 
		{
			if ( DefaultResourceMatcher.INSTANCE.isSame( persistentResource , deletedResource ) ) 
			{
				dispose();
			}
		}
	};

	private IAssemblyProject project;
	private String initialHashCode; // hash code used to check whether current editor content differs from the one on disk
	private IResource persistentResource; // source code on disk
	private InMemorySourceResource sourceInMemory; // possibly edited source code (in RAM / JEditorPane)
	private ICompilationUnit compilationUnit;

	private CompilationThread compilationThread = null;

	protected static final class UnderlineHighlightPainter extends DefaultHighlighter.DefaultHighlightPainter {

		private int thickness;

		public UnderlineHighlightPainter(Color c, int thickness) {
			super(c);
			this.thickness = thickness;
		}

		@Override
		public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds,JTextComponent c, View view) 
		{
			Rectangle r;
			if (offs0 == view.getStartOffset() &&
					offs1 == view.getEndOffset()) {
				// Contained in view, can just use bounds.
				if (bounds instanceof Rectangle) {
					r = (Rectangle) bounds;
				}
				else {
					r = bounds.getBounds();
				}
			}
			else {
				// Should only render part of View.
				try {
					// --- determine locations ---
					Shape shape = view.modelToView(offs0, Position.Bias.Forward,
							offs1,Position.Bias.Backward,
							bounds);
					r = (shape instanceof Rectangle) ?
							(Rectangle)shape : shape.getBounds();
				} catch (BadLocationException e) {
					// can't render
					r = null;
				}
			}

			if (r != null) 
			{
				Color color = getColor();

				if (color == null) {
					color = c.getSelectionColor();
				}
				g.setColor(color);

				// If we are asked to highlight, we should draw something even
				// if the model-to-view projection is of zero width (6340106).
				r.width = Math.max(r.width, 1);
				g.fillRect(r.x, r.y+r.height, r.width, thickness );
			}

			return r;            
		}
	}    

	/*  WAIT_FOR_EDIT-------> WAIT_FOR_TIMEOUT ------>( do compilation ) ----+
	 *     ^    ^                     |                                      |
	 *     |    |                     |                                      |
	 *     |    +---RESTART_TIMEOUT---+                                      |
	 *     +-----------------------------------------------------------------+
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

	protected final Highlighter getHighlighter() {
		return editorPane.getHighlighter();
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

	protected final void replaceText(ITextRegion region,String newValue) 
	{
		disableDocumentListener();
		try {
			editorPane.getDocument().remove( region.getStartingOffset() , region.getLength() );
			editorPane.getDocument().insertString( region.getStartingOffset() , newValue , defaultStyle );
		} catch (BadLocationException e) {
			throw new RuntimeException(e);
		} finally {
			enableDocumentListener();
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
				} 
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
				synchronized( LOCK ) 
				{				
					currentState = WaitState.WAIT_FOR_EDIT;
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

	protected final void notifyDocumentChanged() 
	{
		updateTitle();

		if ( compilationThread == null ) 
		{
			compilationThread = new CompilationThread();
			compilationThread.start();
		} 
		compilationThread.documentChanged();		
	}

	private final DocumentFilter documentFilter = new DocumentFilter() 
	{

		private Line getLine(int offset) 
		{
			try {
				return compilationUnit.getLineForOffset( offset );
			} catch(NoSuchElementException e) {
				return null;
			}
		}

		private int getIndentionOfPreviousLine(int currentOffset) 
		{
			if ( compilationUnit == null || compilationUnit.getAST() == null ) {
				return -1;
			}

			Line previous = null;
			try {
				previous = compilationUnit.getLineForOffset( currentOffset );
			} 
			catch(NoSuchElementException e) {
				return -1;
			}            

			while ( previous != null ) 
			{
				StatementNode stmt = compilationUnit.getAST().getFirstStatementForOffset( previous.getLineStartingOffset() );
				if ( stmt != null && stmt.hasChildren() ) {
					return stmt.child(0).getTextRegion().getStartingOffset() - previous.getLineStartingOffset();
				}
				previous = compilationUnit.getPreviousLine( previous );
			}
			return -1;
		}

		public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException 
		{
			if ( text.equals("\n") && length == 0 ) 
			{
				final int indention = getIndentionOfPreviousLine( offset );
				if ( indention > 0 ) {
					super.replace( fb , offset , length , text+StringUtils.repeat(" " , indention ), attrs );
					return;
				}
			}
			super.replace(fb, offset, length, text, attrs);
		}
	};

	private final DocumentListener recompilationListener = new DocumentListener() {

		private void textChanged(DocumentEvent e) 
		{
			notifyDocumentChanged();
		}

		@Override
		public void removeUpdate(DocumentEvent e) { textChanged(e); }        

		@Override
		public void insertUpdate(DocumentEvent e) { 
			textChanged(e); 
		}

		@Override
		public void changedUpdate(DocumentEvent e)  { /* do nothing, style change only */ }
	};

	private final CaretListener listener = new CaretListener() {

		@Override
		public void caretUpdate(final CaretEvent e) 
		{
			// according to JDK docs, caretUpdate() is NOT necessarily called by the EDT,
			// wrap all code so we can safely update UI components 
			final Runnable r = new Runnable() {

				public void run() 
				{
					// gotoLocation() will set updateNavigationHistory == false when it's been
					// programatically triggered
					if ( navigationHistoryUpdatesEnabled && sourceInMemory != null ) 
					{
						navigationHistory.add( new Location( project , sourceInMemory , e.getDot() ) );
					} 

					// do not fire caret updates while building, this 
					// causes at least the SourceEditorView to access the AST that
					// is still under construction and trigger a NPE in ASTNode#getNodeInRange()                
					if ( ! isEditable() || isBuilding() ) 
					{
						return;
					}

					if ( compilationUnit != null && compilationUnit.getAST() != null && compilationUnit.getAST().getTextRegion() != null ) 
					{
						try {
							final SourceLocation location = getSourceLocation( e.getDot() );
							cursorPosition.setHorizontalAlignment( JTextField.RIGHT );
							cursorPosition.setText( "Line "+location.getLineNumber()+" , column "+location.getColumnNumber()+" (offset "+e.getDot()+")");
						} 
						catch(NoSuchElementException e2) {
							// ok, user clicked on unknown location
						}
					}
					onCaretUpdate( e );                    
				};
			};
			UIUtils.invokeLater( r );
		}
	};

	protected void onCaretUpdate(CaretEvent e) {

	}

	public SourceCodeView(IResourceResolver resourceResolver , IWorkspace workspace,NavigationHistory navigationHistory,boolean isEditable) 
	{
		if (workspace == null) {
			throw new IllegalArgumentException("workspace must not be null");
		}
		if ( resourceResolver == null ) {
			throw new IllegalArgumentException("resourceResolver must not be NULL.");
		}
		this.navigationHistory = navigationHistory;
		this.resourceResolver = resourceResolver;
		this.editable = isEditable;
		this.workspace = workspace;
		defaultStyle = new SimpleAttributeSet();
		errorStyle = createStyle( Color.RED );
		registerStyle = createStyle( Color.ORANGE );   
		commentStyle = createStyle( Color.WHITE );
		macroDefinitionStyle = createStyle(Color.YELLOW );
		macroInvocationStyle = createStyle(Color.YELLOW);
		instructionStyle = createStyle( new Color(50,186,223) );
		labelStyle = createStyle( new Color(237,237,81) );
		preProcessorStyle = createStyle( new Color( 200 , 200 , 200 ) ); 
		workspace.addWorkspaceListener( workspaceListener );
		navigationHistory.addListener( navigationHistoryListener );
	}

	protected final static SimpleAttributeSet createStyle(Color color) 
	{
		SimpleAttributeSet result = new SimpleAttributeSet();
		StyleConstants.setForeground( result , color );
		return result;
	}

	protected final SourceLocation getSourceLocation(ITextRegion range) {
		return getSourceLocation( range.getStartingOffset() );
	}

	protected final SourceLocation getSourceLocation(int offset) {
		final Line line = compilationUnit.getLineForOffset( offset);
		return new SourceLocation( compilationUnit , line , new TextRegion( offset , 0 ) );
	}

	protected final GridBagConstraints constraints(int x,int y,int fill) {
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

	protected final String getTextFromTextPane() 
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

	@Override
	public final void openResource(IAssemblyProject project, IResource resource,int caretPosition) throws IOException 
	{
		if ( this.project != project || this.persistentResource != resource ) {
			openResource( project , resource , caretPosition,true );
		}
	}    

	protected final void openResource(final IAssemblyProject project, final IResource sourceFile,boolean compileSource) throws IOException 
	{
		openResource(project,sourceFile,0,compileSource);
	}

	protected final void openResource(final IAssemblyProject project, 
			final IResource sourceFile,int caretPosition,boolean compileSource) throws IOException 
			{
		if ( project == null ) {
			throw new IllegalArgumentException("project must not be NULL");
		}
		if (sourceFile == null) {
			throw new IllegalArgumentException("sourceFile must not be NULL");
		}

		// read source first so we don't discard internal state
		// and end up with an IOException later on...
		final String source = Misc.readSource( sourceFile );
		this.initialHashCode = Misc.calcHash( source );

		this.project = project;
		if ( sourceFile instanceof InMemorySourceResource) {
			this.sourceInMemory = (InMemorySourceResource) sourceFile;
			this.persistentResource = sourceInMemory.getPersistentResource();        
		} else {
			this.sourceInMemory = new InMemorySourceResource( sourceFile , editorPane ) {

				@Override
				public String toString()
				{
					return "SourceCodeView[ "+persistentResource+" ]";
				}
			};
			this.persistentResource = sourceFile;
		}
		clearHighlight();

		try {
			disableDocumentListener();
			try 
			{
				final Document doc = editorPane.getDocument();
				doc.putProperty(Document.StreamDescriptionProperty, null);

				disableNavigationHistoryUpdates();
				System.out.println("Text length: "+( source == null ? 0 : source.length() ) );

				try {
					editorPane.setText( source );
				} finally {
					enableNavigationHistoryUpdates();
				}

				try {
					editorPane.setCaretPosition( caretPosition );
				} catch(IllegalArgumentException e) {
					LOG.error("openResource(): Invalid caret position "+caretPosition+" in resource "+sourceFile);
				}

				if ( panel != null ) 
				{
					ICompilationUnit existing = null;
					if ( ! compileSource ) {
						existing = project.getProjectBuilder().getCompilationUnit( sourceFile );
					}
					validateSourceCode( existing );
				}
			} finally {
				enableDocumentListener();
			}
		} finally {
			enableNavigationHistoryUpdates();
		}

		editorPane.requestFocus();        
		updateTitle();
			}

	protected final boolean isBuilding() {
		return isBuilding;
	}

	protected final void validateSourceCode() throws IOException {
		validateSourceCode(null);
	}

	protected final void validateSourceCode(ICompilationUnit existing) throws IOException {

		long time = -System.currentTimeMillis();
		disableDocumentListener();
		try 
		{
			clearCompilationErrors();

			onSourceCodeValidation();

			final IResourceResolver delegatingResolver = new AbstractResourceResolver() {

				private IResourceResolver getChildResourceResolver(IResource parent) 
				{
					IResource r = parent == null ? getCurrentResource() : parent;
					if ( ! ( r instanceof FileResource ) ) 
					{
						if ( r instanceof InMemorySourceResource) {
							r = ((InMemorySourceResource) r).getPersistentResource();
						} 
					}
					if ( ! ( r instanceof FileResource ) ) {
						throw new RuntimeException("Internal error, not a file-resource: "+getCurrentResource());
					}            		
					final FileResource fr = (FileResource) r;
					return new FileResourceResolver( fr.getAbsoluteFile().getParentFile() ){
						@Override
						protected ResourceType determineResourceType(File file) 
						{
							// TODO: Maybe implement some more general mechanism of determining resource types ?
							return project.getConfiguration().isSourceFile( file ) ? ResourceType.SOURCE_CODE : ResourceType.UNKNOWN;
						}
					};
				}

				@Override
				public IResource resolve(String identifier) throws ResourceNotFoundException 
				{
					try {
						if ( resourceResolver != null ) {
							return resourceResolver.resolve( identifier );
						}
					} 
					catch(ResourceNotFoundException e) 
					{
					}
					return getChildResourceResolver(null).resolve( identifier );
				}

				@Override
				public IResource resolveRelative(String identifier, IResource parent)
						throws ResourceNotFoundException 
						{
					try {
						if ( resourceResolver != null ) {
							return resourceResolver.resolveRelative( identifier , parent );
						}
					} 
					catch(ResourceNotFoundException e) 
					{
					}

					final IResource realParent;
					if ( parent instanceof InMemorySourceResource ) 
					{
						realParent = ((InMemorySourceResource ) parent).getPersistentResource();
					} else {
						realParent = parent;
					}
					return getChildResourceResolver( parent ).resolveRelative( identifier , realParent );					
						}
			};

			try 
			{
				if ( existing == null || existing.getAST() == null ) 
				{
					compilationUnit = project.getProjectBuilder().parse( 
							sourceInMemory ,
							delegatingResolver,                		
							new CompilationListener() 
							);
				} else {
					compilationUnit = existing;
				}
			} 
			catch(Exception e) {
				LOG.error("validateSourceCode(): ",e);
			} 
			finally {
				doHighlighting( compilationUnit , true );                
			}

			for ( ICompilationError error : compilationUnit.getErrors() ) 
			{
				onCompilationError( error );
			}
			for ( ICompilationError error : compilationUnit.getWarnings() ) 
			{
				onCompilationWarning( error );
			}			

		} finally {
			enableDocumentListener();
			time += System.currentTimeMillis();
			System.out.println("Source code validation: "+time+" ms");
		}
	}

	protected void onSourceCodeValidation() {

	}

	protected final void doHighlighting(ICompilationUnit unit,boolean called) 
	{
		if ( unit == null ) {
			throw new IllegalArgumentException("Internal error,compilation unit must not be NULL.");
		}

		if ( panel == null ) {
			return;
		}

		if ( unit.getAST() != null ) 
		{
			onHighlightingStart();

			long time = -System.currentTimeMillis();
			try 
			{
				final int markerCount = unit.getMarkers( (String[]) null ).size();
				System.out.println("DEBUG: Starting to highlight "+unit.getResource()+" with "+markerCount+" markers.");
				doSemanticHighlighting( unit );
			} finally {
				time += System.currentTimeMillis();
				System.out.println("DEBUG: Highlighting "+unit.getResource()+" took "+time+" ms.");
			}
		}

		if ( unit.hasErrors() ) 
		{
			highlightCompilationErrors( compilationUnit );
		}  
	}

	protected void onHighlightingStart() {
	}

	protected final void doSemanticHighlighting(ICompilationUnit unit)
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
				System.out.println("Highlighting "+nodes.size()+" nodes.");
				for ( ASTNode child : nodes ) 
				{
					doSemanticHighlighting( unit , child );
				}
			}
		} finally {
			enableDocumentListener();
		}
	}

	protected final void doSemanticHighlighting(ICompilationUnit unit, ASTNode node)
	{
		if ( highlight( node ) ) {
			return; // don't highlight children if parent already was
		}

		if ( ! (node instanceof IncludeSourceFileNode ) ) {
			for ( ASTNode child : node.getChildren() ) {
				doSemanticHighlighting( unit , child );
			}
		}
	}

	protected final boolean highlight(ASTNode node) 
	{
		if ( node instanceof StartMacroNode || node instanceof EndMacroNode) 
		{
			highlight( node , macroDefinitionStyle );			
			return true;
		}
		else if ( node instanceof InvokeMacroNode) 
		{
			highlight( node , macroInvocationStyle );			
			return true;
		}
		else if ( node instanceof InstructionNode ) 
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
			return true;
		} 
		else if ( node instanceof IPreprocessorDirective)
		{
			highlight( node , preProcessorStyle );
			return true;
		} 
		else if ( node instanceof SymbolReferenceNode || node instanceof LabelNode ) 
		{
			highlight( node , labelStyle );
			return true;
		} else if ( node instanceof CommentNode ) {
			highlight( node , commentStyle );
			return true;
		} else if ( node instanceof RegisterReferenceNode ) {
			highlight( node , registerStyle );
			return true;
		}
		return false;
	}

	protected final void highlight(ASTNode node, AttributeSet attributes) 
	{
		highlight( node.getTextRegion() , attributes );
	}

	protected final void highlight(ITextRegion range, AttributeSet attributes) 
	{
		editorPane.getStyledDocument().setCharacterAttributes( range.getStartingOffset() , range.getLength() , attributes , true );
	}

	public final void moveCursorTo(ITextRegion location,boolean updateNavigationHistory) 
	{
		moveCursorTo( location.getStartingOffset() , updateNavigationHistory );
	}

	public final void moveCursorTo(int offset,boolean updateNavigationHistory) 
	{
		if ( compilationUnit == null || compilationUnit.getAST() == null ) {
			return;
		}

		if ( ! editorPane.hasFocus() ) {
			editorPane.requestFocus();
		}
		try
		{        
			if ( ! updateNavigationHistory ) {
				disableNavigationHistoryUpdates();
				try {
					editorPane.setCaretPosition( offset  );
				} finally {
					enableNavigationHistoryUpdates();
				}
			} else {
				editorPane.setCaretPosition( offset  );
			}
		} 
		catch(IllegalArgumentException e) {
			LOG.error("moveCursorTo(): Failed to offset "+offset+" on project "+project+" , resource "+sourceInMemory,e);
		} 
		centerCurrentLineInScrollPane();
	}

	public final void centerCurrentLineInScrollPane()
	{
		final Runnable r = new Runnable() {
			@Override
			public void run() {

				final Container container = SwingUtilities.getAncestorOfClass(JViewport.class, editorPane);

				if (container == null) {
					return;
				}

				try {
					final Rectangle r = editorPane.modelToView(editorPane.getCaretPosition());
					if (r == null ) {
						return;
					}
					final JViewport viewport = (JViewport) container;
					final int extentHeight = viewport.getExtentSize().height;
					final int viewHeight = viewport.getViewSize().height;

					int y = Math.max(0, r.y - (extentHeight / 2));
					y = Math.min(y, viewHeight - extentHeight);

					viewport.setViewPosition(new Point(0, y));
				} 
				catch (BadLocationException ble) {
					LOG.error("centerCurrentLineInScrollPane(): ",ble);
				}    			
			}

		};

		UIUtils.invokeLater( r );
	}

	protected final boolean canNavigationHistoryBack() 
	{
		return navigationHistory.canGoBack();
	}

	protected final boolean canNavigationHistoryForward() {
		return navigationHistory.canGoForward();
	}    

	protected final void navigationHistoryBack() 
	{
		gotoLocation( navigationHistory.goBack() );
	}

	protected final void navigationHistoryForward() 
	{
		gotoLocation( navigationHistory.goForward() );
	}       

	// guaranteed to only be called on EDT
	protected void onNavigationHistoryChange() {

	}

	public final void gotoLocation(final int offset) {
		gotoLocation(project,sourceInMemory , offset,false);
	}    

	private final void gotoLocation(Location location) 
	{
		if ( location == null ) {
			return;
		}
		IAssemblyProject project = workspace.getProjectForResource( location.getResource() );
		gotoLocation(project,location.getResource(),location.getOffset(),true);
	}

	private final void gotoLocation(
			IAssemblyProject project,
			IResource resource,
			final int offset,
			final boolean triggeredProgramatically) 
	{
		if ( this.project != project || ! isCurrentlyDisplayed( resource ) )
		{
			if ( getViewContainer() instanceof EditorContainer) 
			{
				try {
					((EditorContainer) getViewContainer()).openResource( workspace , project , resource , offset );
				} catch (IOException e) {
					LOG.error("gotoLocation(): Failed for project "+project+", resource "+resource,e);
				}
				return;
			}

			try {
				openResource( project , resource , offset , true );
			} 
			catch (IOException e) {
				LOG.error("gotoLocation(): Failed to project "+project+",resource "+resource,e);
			}
			return;
		}

		final Runnable r = new Runnable() 
		{
			@Override
			public void run() 
			{
				editorPane.requestFocusInWindow();     

				if ( triggeredProgramatically ) 
				{
					disableNavigationHistoryUpdates();
					editorPane.setCaretPosition( offset );
					enableNavigationHistoryUpdates();
				} 
				else 
				{
					editorPane.setCaretPosition( offset );
				}
				centerCurrentLineInScrollPane();                
			}
		};

		UIUtils.invokeLater( r );
	}    

	protected final boolean isCurrentlyDisplayed(IResource resource) 
	{
		if ( this.sourceInMemory == null ) {
			return false;
		}

		return this.sourceInMemory.getIdentifier().equals( resource.getIdentifier() );
	}    

	protected final void disableNavigationHistoryUpdates() {
		navigationHistoryUpdatesEnabled = false;
	}

	protected final void enableNavigationHistoryUpdates() {
		navigationHistoryUpdatesEnabled = true;
	}	

	protected final ITextRegion getVisibleTextRegion() 
	{
		JViewport viewport = editorScrollPane.getViewport();
		Rectangle viewRect = viewport.getViewRect();

		Point p1 = viewRect.getLocation();
		int startIndex = editorPane.viewToModel(p1);
		if ( startIndex < 0 ) {
			return null;
		}
		Point p2 = new Point(p1.x + viewRect.width-10 , p1.y + viewRect.height-10 ); // -10 is some arbitrary offset to fix an issue with viewToModel() returning a position at the end of the input text 
		int endIndex = editorPane.viewToModel(p2);
		if ( endIndex < 0 ) {
			return null;
		}

		int len = endIndex-startIndex;
		if ( len < 0) {
			return null;
		}
		System.out.println("getVisibleTextRegion( "+p1+" , "+p2+" => "+startIndex+","+endIndex);
		return new TextRegion( startIndex , len );
	}

	//	protected final ITextRegion getVisibleTextRegion() 
	//	{
	//		final Point startPoint = editorScrollPane.getViewport().getViewPosition();
	//		final Dimension size = editorScrollPane.getViewport().getExtentSize();
	//
	//		final Point endPoint = new Point(startPoint.x + size.width, startPoint.y + size.height);
	//		try {
	//			final int start = editorPane.viewToModel( startPoint );
	//			if ( start < 0 ) {
	//				return null;
	//			}
	//			final int end = editorPane.viewToModel( endPoint );
	//			if ( end < 0 ) {
	//				return null;
	//			}
	//			final int len = end-start;
	//			if ( len < 0  ) {
	//				return null;
	//			}
	//			return new TextRegion( start , len );
	//		} 
	//		catch(NullPointerException e) 
	//		{
	//			LOG.error("getVisibleTextRegion(): Caught ",e);
	//			return null;
	//		}
	//	}

	protected final void clearCompilationErrors() 
	{
		disableDocumentListener();      
		try {
			final StyledDocument doc = editorPane.getStyledDocument();
			doc.setCharacterAttributes( 0 , doc.getLength() , defaultStyle , true );
		} finally {
			enableDocumentListener();
		}
	}

	protected final void disableDocumentListener() 
	{
		documentListenerDisableCount++;     
		editorPane.getDocument().removeDocumentListener( recompilationListener );
		if ( editorPane.getDocument() instanceof AbstractDocument) {
			((AbstractDocument) editorPane.getDocument()).setDocumentFilter( null );
		}         
	}

	protected final void enableDocumentListener() 
	{
		documentListenerDisableCount--;     
		if ( documentListenerDisableCount == 0) 
		{
			editorPane.getDocument().addDocumentListener( recompilationListener );
			if ( editorPane.getDocument() instanceof AbstractDocument) {
				((AbstractDocument) editorPane.getDocument()).setDocumentFilter( documentFilter );
			}            
		}
	}   

	protected final void highlightCompilationErrors(ICompilationUnit unit) 
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
					highlight( location , errorStyle );
				}
			}
		} finally {
			enableDocumentListener();
		}
	}

	protected void onCompilationError(ICompilationError error) {

	}

	protected void onCompilationWarning(ICompilationError error) {

	}

	// ============= view creation ===================

	@Override
	public JPanel getPanel()
	{
		if ( panel == null ) {
			panel = createPanel();
			if ( this.persistentResource != null ) 
			{
				try {
					validateSourceCode();
				} catch (IOException e) {
					LOG.error("getPanel(): ",e);
				}
			}
		}
		return panel;
	}

	private final MouseListener mouseListener = new MouseAdapter() {

		public void mouseClicked(java.awt.event.MouseEvent e) {

			if ( e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON1 && ( e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK ) != 0 ) 
			{
				// navigate to symbol definition
				final ASTNode node = getASTNodeForLocation( e.getPoint() );
				if ( node instanceof SymbolReferenceNode) 
				{
					final SymbolReferenceNode ref = (SymbolReferenceNode) node;
					gotoToSymbolDefinition( ref );
				}
			}
		}
	};

	protected final void gotoToSymbolDefinition(SymbolReferenceNode ref) 
	{
		if ( getCurrentCompilationUnit() != null ) 
		{
			ISymbolTable table = getCurrentCompilationUnit().getSymbolTable();			
			ISymbol symbol = ref.resolve( table , true );
			if ( symbol != null ) 
			{
				if ( symbol != null ) 
				{
					final ITextRegion location = symbol.getLocation();
					final ICompilationUnit newCompilationUnit = symbol.getCompilationUnit();
					final IAssemblyProject project = workspace.getProjectForResource( newCompilationUnit.getResource() );
					gotoLocation( project , newCompilationUnit.getResource() , location.getStartingOffset() , false );
				} 
			}
		}
	}

	private final JPanel createPanel() 
	{
		disableDocumentListener(); // necessary because setting colors on editor pane triggers document change listeners (is considered a style change...)

		try {
			editorPane.setEditable( editable );
			editorPane.getDocument().addUndoableEditListener( undoListener );
			editorPane.setCaretColor( Color.WHITE );
			setupKeyBindings( editorPane );
			setColors( editorPane );
			editorScrollPane = new JScrollPane(editorPane);
			setColors( editorScrollPane );
			editorPane.addCaretListener( listener );
			editorPane.addMouseListener( mouseListener );
			editorPane.addMouseMotionListener( new MouseMotionAdapter() {

				@Override
				public void mouseMoved(MouseEvent e) 
				{
					if ( (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0 ) // ctrl pressed
					{ 
						final ASTNode node = getASTNodeForLocation( e.getPoint() );
						if ( node instanceof SymbolReferenceNode) {
							maybeUnderlineIdentifierAt( e.getPoint() );
						} else {
							clearUnderlineHighlight();                        
						}
					} 
					else if ( compilationUnit != null  ) 
					{
						String tooltipText=null;
						if ( compilationUnit != null  ) 
						{
							final ASTNode node = getASTNodeForLocation( e.getPoint() );
							if ( node instanceof InvokeMacroNode) 
							{
								tooltipText = ExpandMacrosPhase.expandInvocation( (InvokeMacroNode) node , compilationUnit );
								if ( tooltipText != null ) {
									tooltipText = "<html>"+tooltipText.replace("\n","<br>")+"</html>";
								}
							}
						}
						if ( ! StringUtils.equals( editorPane.getToolTipText() , tooltipText ) ) {
							editorPane.setToolTipText( tooltipText );
						}
					}
				}                
			});
			editorPane.addMouseListener( popupListener );
		} finally {
			enableDocumentListener();
		}

		EditorContainer.addEditorCloseKeyListener( editorPane , this );

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

		cursorPosition.setSize( new Dimension(400,15) );
		cursorPosition.setEditable( false );
		setColors( cursorPosition ); 

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

		// setup result panel
		final JPanel panel = new JPanel();
		panel.setLayout( new GridBagLayout() );
		setColors( panel );  
		cnstrs = constraints( 0 , 0 , true , true , GridBagConstraints.BOTH );
		panel.add( topPanel , cnstrs );
		return panel;
	}

	/**
	 * Returns the mouse pointer's location relative to the
	 * editorpane or <code>null</code> if the mouse pointer is 
	 * outside of the editor.
	 * 
	 * @return location or <code>null</code> if mouse ptr is outside of the editor pane
	 */
	protected final Point getMouseLocation()
	{
		final Point location = MouseInfo.getPointerInfo().getLocation() ;
		final Point locOnScreen = editorPane.getLocationOnScreen();

		final Point result= new Point( location.x - locOnScreen.x , location.y - locOnScreen.y );
		return editorPane.contains( result ) ? result : null;
	}

	protected final void setupKeyBindings(final JTextPane editor) 
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

		// 'Underline text when pressing CTRL while hovering over an identifier' 
		editorPane.addKeyListener( new KeyAdapter() 
		{
			private boolean isControlKey(KeyEvent e) {
				return e.getKeyCode() == KeyEvent.VK_CONTROL;
			}

			public void keyPressed(KeyEvent e) 
			{
				if ( isControlKey(e) ) 
				{
					final Point ptr = getMouseLocation();
					if ( ptr != null ) {
						maybeUnderlineIdentifierAt( ptr );
					}
				}
			}

			public void keyReleased(KeyEvent e) {
				if ( isControlKey(e) ) {
					clearUnderlineHighlight();
				}                
			};
		} );

		// "Undo" action
		addKeyBinding( editor , 
				KeyStroke.getKeyStroke(KeyEvent.VK_Z,Event.CTRL_MASK),
				undoAction );

		addKeyBinding( editor , 
				KeyStroke.getKeyStroke(KeyEvent.VK_Y,Event.CTRL_MASK),
				redoAction );   

		// 'Search' action 
		addKeyBinding( editor , 
				KeyStroke.getKeyStroke(KeyEvent.VK_F,Event.CTRL_MASK),
				new AbstractAction() 
		{
			@Override
			public void actionPerformed(ActionEvent e) {
				showSearchDialog();
			}
		});

		setupKeyBindingsHook( editor );
	}

	protected void setupKeyBindingsHook(JTextPane editor) {

	}

	protected final void saveCurrentFile() {

		if ( ! hasUnsavedContent() ) {
			return;
		}

		final String source = getTextFromTextPane();
		try {
			Misc.writeResource( getCurrentResource() , source );
			this.initialHashCode = Misc.calcHash( source );
			updateTitle();
		} catch (IOException e1) {
			LOG.error("save(): Failed to write to "+getCurrentResource());
			return;
		}

		if ( compilationUnit == null || compilationUnit.hasErrors() ) {
			return;
		}

		try {
			getCurrentProject().getProjectBuilder().build();
		} 
		catch (IOException e) {
			LOG.error("save(): Compilation failed",e);
		}
	}

	public final IAssemblyProject getCurrentProject() {
		return project;
	}

	public final IResource getCurrentResource() {
		return this.persistentResource;
	}

	public final IResource getSourceFromMemory() {
		return this.sourceInMemory;
	}

	@Override
	public final void disposeHook()
	{
		try {
			disposeHook2();            
		} 
		finally 
		{
			if ( registeredWithTooltipManager ) {
				ToolTipManager.sharedInstance().unregisterComponent( editorPane );
			}
			workspace.removeWorkspaceListener( workspaceListener );
			navigationHistory.removeListener( navigationHistoryListener );
		}
	}

	protected void disposeHook2() {
	}

	@Override
	public final void refreshDisplay()
	{
		try {
			if ( project != null ) {
				validateSourceCode();
			}
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		refreshDisplayHook();
	}

	protected void refreshDisplayHook() {

	}

	@Override
	public String getTitle() 
	{
		if ( getCurrentResource() == null ) {
			return "source view";
		}
		final String prefix = hasUnsavedContent() ? "*" : "";
		final String identifier;
		if ( getCurrentResource() instanceof FileResource ) {
			identifier = ((FileResource) getCurrentResource()).getFile().getName();
		} else {
			identifier = getCurrentResource().getIdentifier();
		}
		return prefix + identifier;
	}

	@Override
	public final boolean hasUnsavedContent() 
	{
		if ( this.persistentResource == null ) {
			return false;
		}
		return ! initialHashCode.equals( Misc.calcHash( getTextFromTextPane() ) );
	}

	@Override
	public final boolean mayBeDisposed() {
		return ! hasUnsavedContent();
	}

	protected final void updateTitle() 
	{
		final String title = ( hasUnsavedContent() ? "*" : "")+getCurrentResource().getIdentifier();
		getViewContainer().setTitle( SourceCodeView.this , title );               
	}

	@Override
	public String getID() {
		return "source-view";
	}

	public final boolean isEditable()
	{
		return editable;
	}

	public void setEditable(boolean editable)
	{
		this.editable = editable;
		editorPane.setEditable( editable );       
	}

	protected final ICompilationUnit getCurrentCompilationUnit() {
		return compilationUnit;
	}

	protected final void addMouseListener(MouseAdapter listener) {
		editorPane.addMouseListener( listener );
		editorPane.addMouseMotionListener( listener );
	}    

	protected final void showTooltip(String s) 
	{
		if ( ! registeredWithTooltipManager ) {
			ToolTipManager.sharedInstance().registerComponent( editorPane );
			registeredWithTooltipManager = true;
		}
		editorPane.setToolTipText( s );
	}

	protected final void clearTooltip() {
		editorPane.setToolTipText( null );
	}	

	protected final void removeMouseListener(MouseAdapter listener) {
		editorPane.removeMouseListener( listener );
		editorPane.removeMouseMotionListener( listener );
	}

	protected final int getModelOffsetForLocation(Point p) {
		return editorPane.viewToModel( p );
	}

	protected final void addPopupMenu(PopupMenu menu) {
		editorPane.add( menu );
	}

	protected final ASTNode getASTNodeForLocation(Point p) 
	{
		final AST ast = getCurrentCompilationUnit() != null ? getCurrentCompilationUnit().getAST() : null;
		if ( ast == null ) {
			return null;
		}

		int offset = editorPane.viewToModel( p );
		if ( offset != -1 ) {
			return ast.getNodeInRange( offset );
		}
		return null;
	}

	protected class PopupListener extends MouseAdapter 
	{
		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
		}

		private void maybeShowPopup(MouseEvent e) 
		{
			if (e.isPopupTrigger()) 
			{
				final ASTNode node = getASTNodeForLocation( e.getPoint() );
				final JPopupMenu menu = createPopupMenu( node , editorPane.getCaretPosition(),
						editorPane.getSelectedText() );
				if ( menu != null ) {
					menu.show(e.getComponent(),e.getX(), e.getY());
				}
			}
		}
	}    

	protected JPopupMenu createPopupMenu(ASTNode node, int caretPosition,String currentSelection) {
		return null;
	}

	protected final void showSearchDialog() 
	{
		final int cursorPos = editorPane.getCaretPosition();
		final String selection = editorPane.getSelectedText();
		if ( searchDialog == null ) {
			searchDialog = new SearchDialog();
			searchDialog.setVisible( true );
			searchDialog.activate(selection,cursorPos);
		} else {
			searchDialog.activate(selection,cursorPos);
		}
	}

	protected final void highlightLocation(ITextRegion region) {

		if (region == null) {
			throw new IllegalArgumentException("region must not be null");
		}

		try {
			if ( currentHighlight == null ) {
				currentHighlight = editorPane.getHighlighter().addHighlight(
						region.getStartingOffset() ,
						region.getEndOffset() , 
						new DefaultHighlighter.DefaultHighlightPainter(Color.WHITE) );
			} else {
				editorPane.getHighlighter().changeHighlight( 
						currentHighlight,
						region.getStartingOffset() , 
						region.getEndOffset() );
			}
		} catch (BadLocationException e) {
			LOG.error("highlightLocation(): Bad location "+region,e);
			throw new RuntimeException("Bad text location "+region,e);
		}
	}


	protected final void clearUnderlineHighlight() 
	{
		final Runnable r = new Runnable() {

			@Override
			public void run()
			{
				if ( currentUnderlineHighlight != null ) 
				{
					editorPane.getHighlighter().removeHighlight( currentUnderlineHighlight );
					currentUnderlineHighlight = null;
					editorPane.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
					editorPane.repaint();
				}                 
			}
		};
		UIUtils.invokeLater( r );
	}    

	protected final void maybeUnderlineIdentifierAt(Point mouseLocation) {

		final ASTNode node = getASTNodeForLocation( mouseLocation );
		if ( !(node instanceof SymbolReferenceNode) ) 
		{
			return;
		}
		final SymbolReferenceNode ref = (SymbolReferenceNode) node;
		underlineLocation( ref.getTextRegion() );
	}    

	protected final void underlineLocation(final ITextRegion region) 
	{
		if ( region == null ) {
			throw new IllegalArgumentException("region must not be NULL.");
		}

		Runnable r = new Runnable() {
			@Override
			public void run()
			{
				try 
				{
					if ( currentUnderlineHighlight == null ) {
						currentUnderlineHighlight = editorPane.getHighlighter().addHighlight(
								region.getStartingOffset() ,
								region.getEndOffset() , 
								new UnderlineHighlightPainter(Color.BLUE,1) );
					} else {
						editorPane.getHighlighter().changeHighlight( 
								currentUnderlineHighlight,
								region.getStartingOffset() , 
								region.getEndOffset() );
					}
					editorPane.setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
					editorPane.repaint();
				} 
				catch (BadLocationException e) {
					LOG.error("underlineLocation(): Bad location "+region,e);
				}                  
			}
		};
		UIUtils.invokeLater( r );
	}

	protected final void clearHighlight() {
		if ( currentHighlight != null ) {
			editorPane.getHighlighter().removeHighlight( currentHighlight );
			currentHighlight = null;
		}
	}    

	protected static enum Direction {
		FORWARD {

			@Override
			public int advance(int index) {
				return index+1;
			}
		} ,
		BACKWARD {

			@Override
			public int advance(int index) {
				return index-1;
			}
		} ;

		public abstract int advance(int index);
	}

	protected final class SearchDialog extends JFrame {

		private final JTextField searchPattern = new JTextField();

		private final JCheckBox wrapSearch = new JCheckBox("Wrap",false);
		private final JCheckBox caseSensitive = new JCheckBox("Match case",false);    	

		private final JButton nextButton = new JButton("Next");
		private final JButton previousButton = new JButton("Previous");
		private final JButton closeButton = new JButton("Close");

		private final JTextField messageArea = new JTextField("",25);

		private String lastSearchPattern;
		private int lastMatch = -1;
		private int currentIndex = 0;
		private Direction lastDirection = Direction.FORWARD;

		public SearchDialog() 
		{
			super("Search");

			messageArea.setBackground(null);
			messageArea.setEditable(false);
			messageArea.setBorder(null);
			messageArea.setFocusable(false);			

			setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );

			final JPanel panel = new JPanel();
			panel.setLayout( new GridBagLayout() );

			// add search pattern
			GridBagConstraints cnstrs = constraints( 0 , 0, true , 
					false , GridBagConstraints.HORIZONTAL );
			panel.add( searchPattern , cnstrs );

			searchPattern.addActionListener( new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					search(lastDirection);
				}
			});

			// add wrap checkbox
			cnstrs = constraints( 0 , 1, false, false , GridBagConstraints.HORIZONTAL );
			panel.add( wrapSearch , cnstrs );

			// 'case-sensitive' checkbox
			cnstrs = constraints( 1 , 1, false, false , GridBagConstraints.HORIZONTAL );
			panel.add( caseSensitive , cnstrs );

			// add message area
			cnstrs = constraints( 0 , 2 , true , false , GridBagConstraints.HORIZONTAL );
			panel.add( messageArea , cnstrs );			

			// create button panel
			final JPanel buttonPanel = new JPanel();
			buttonPanel.setLayout( new GridBagLayout() );

			cnstrs = constraints( 0 , 0, false , true , GridBagConstraints.HORIZONTAL );
			buttonPanel.add( previousButton , cnstrs );
			previousButton.addActionListener( new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					search(Direction.BACKWARD);
				}
			});

			cnstrs = constraints( 1 , 0, false , true , GridBagConstraints.HORIZONTAL );
			buttonPanel.add( nextButton , cnstrs );
			nextButton.addActionListener( new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					search(Direction.FORWARD);
				}
			});

			cnstrs = constraints( 2 , 0, true , true , GridBagConstraints.HORIZONTAL );
			buttonPanel.add( closeButton , cnstrs );
			closeButton.addActionListener( new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					setVisible(false);
					clearHighlight();
				}
			});

			// add button panel
			// add wrap checkbox
			cnstrs = constraints( 0 , 3 , true , true , GridBagConstraints.HORIZONTAL );
			panel.add( buttonPanel , cnstrs );			

			// add everything to content pane
			getContentPane().add( panel );
			setAlwaysOnTop(true);
			pack();
		}

		public final void activate(String selectedText,int cursorPos) 
		{
			setVisible(true);

			currentIndex = cursorPos;
			final String text = StringUtils.isNotBlank( selectedText ) ? selectedText : lastSearchPattern;
			if ( text != null ) {
				searchPattern.setText( selectedText );
			}
			searchPattern.requestFocus();
		}

		protected void search(Direction direction) {

			showMessage(null);

			final String source = getTextFromTextPane();
			final String pattern = searchPattern.getText();

			if ( StringUtils.isBlank( pattern ) ) {
				showMessage("Please enter a search pattern");
				return;
			}
			lastSearchPattern = pattern;

			lastDirection = direction;

			if ( lastMatch != -1 ) // advance past last match 
			{
				if ( ! advance( source , direction , pattern , lastMatch ) ) {
					clearHighlight();
					showNotFoundMessage();
					return;
				}
			}

			// remember start index so we don't loop forever
			// if the search pattern doesn't match at all
			final int searchStartIndex = currentIndex;
			final boolean matchCaseSensitive = caseSensitive.isSelected();
			do {
				final String currentText =
						source.substring( currentIndex , currentIndex+pattern.length() );

				final boolean matches;
				if ( matchCaseSensitive ) {
					matches = currentText.equals( pattern );
				} else {
					matches = currentText.equalsIgnoreCase( pattern );
				}

				if ( matches ) 
				{
					lastMatch = currentIndex;
					gotoLocation( currentIndex );
					editorPane.requestFocus();
					editorPane.setCaretPosition( currentIndex );
					highlightLocation( new TextRegion( currentIndex , pattern.length() ) );
					// TODO: Maybe show 'match found' message ?
					return;
				}
				if ( ! advance( source , direction , pattern , currentIndex ) ) {
					clearHighlight();
					showNotFoundMessage();
					break;
				}
			} while ( currentIndex != searchStartIndex );

			if ( lastMatch != -1 ) {
				currentIndex = lastMatch;
			}
			clearHighlight();
			showNotFoundMessage();
		}

		private void showNotFoundMessage() {
			showMessage("No (more) matches");
		}

		private boolean advance(String source, Direction direction,String pattern,int index) 
		{
			int newIndex = direction.advance( index );
			if ( newIndex < 0 ) {
				if ( wrapSearch.isSelected() ) 
				{
					showSearchWrappedMessage();
					newIndex = source.length() - 1 - pattern.length() ;
				} else {
					return false; 
				}
			} else if ( ( newIndex+pattern.length()) >= source.length() ) {
				if ( wrapSearch.isSelected() ) {
					showSearchWrappedMessage();
					newIndex = 0;
				} else {
					return false; 
				}
			}
			currentIndex = newIndex;
			return true;
		}

		private void showSearchWrappedMessage() {
			showMessage("Search wrapped");
		}

		private void showMessage(String message) {
			messageArea.setText( message );
		}
	}

	protected final int getCaretPosition() {
		return editorPane.getCaretPosition();
	}    
}