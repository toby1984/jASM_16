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
import java.awt.Dimension;
import java.awt.Event;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentEvent.EventType;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.AbstractDocument.DefaultDocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument.AttributeUndoableEdit;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.CommentNode;
import de.codesourcery.jasm16.ast.EquationNode;
import de.codesourcery.jasm16.ast.IncludeBinaryFileNode;
import de.codesourcery.jasm16.ast.InitializedMemoryNode;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.OriginNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.SymbolReferenceNode;
import de.codesourcery.jasm16.ast.UninitializedMemoryNode;
import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.Severity;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.compiler.io.AbstractResource;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.IWorkspaceListener;
import de.codesourcery.jasm16.ide.WorkspaceListener;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Line;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Crude editor to test the compiler's inner workings.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class SourceCodeView extends AbstractView implements IEditorView {

    private static final Logger LOG = Logger.getLogger(SourceCodeView.class);

    // time to wait until recompiling after the user edited the source code
    private static final int RECOMPILATION_DELAY_MILLIS = 300;

    // UI widgets

    private volatile JPanel panel;
    
    private volatile boolean editable;
    
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

    // compiler
    protected final IWorkspace workspace; 
    private final IWorkspaceListener workspaceListener = new WorkspaceListener() {
        
        public void projectDeleted(IAssemblyProject deletedProject) 
        {
            if ( deletedProject.isSame( project ) )
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
            if ( deletedResource.isSame( sourceFileOnDisk ) ) 
            {
                dispose();
            }
        }
    };
    
    private IAssemblyProject project;
    protected String initialHashCode; // hash code used to check whether current editor content differs from the one on disk
    protected IResource sourceFileOnDisk; // source code on disk
    protected IResource sourceInMemory; // possibly edited source code (in RAM / JEditorPane)
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
        public void changedUpdate(DocumentEvent e)  { /* do nothing, style change only */ }
    };

    private final CaretListener listener = new CaretListener() {

        @Override
        public void caretUpdate(CaretEvent e) 
        {
            if ( ! isEditable() ) {
                return;
            }
            
            if ( compilationUnit != null && compilationUnit.getAST() != null && compilationUnit.getAST().getTextRegion() != null ) 
            {
                try {
                    final SourceLocation location = getSourceLocation( e.getDot() );
                    cursorPosition.setHorizontalAlignment( JTextField.RIGHT );
                    cursorPosition.setText( "Line "+location.getLineNumber()+" , column "+location.getColumnNumber()+" (offset "+e.getDot()+")");
                } catch(NoSuchElementException e2) {
                    // ok, user clicked on unknown location
                }
            }
            onCaretUpdate( e );
        }
    };
    
    protected void onCaretUpdate(CaretEvent e) {
        
    }

    public SourceCodeView(IWorkspace workspace,boolean isEditable) 
    {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        this.editable = isEditable;
        this.workspace = workspace;
        defaultStyle = new SimpleAttributeSet();
        errorStyle = createStyle( Color.RED );
        registerStyle = createStyle( Color.ORANGE );   
        commentStyle = createStyle( Color.DARK_GRAY );
        instructionStyle = createStyle( Color.BLUE );
        labelStyle = createStyle( Color.GREEN );
        preProcessorStyle = createStyle( new Color( 200 , 200 , 200 ) ); 
        workspace.addWorkspaceListener( workspaceListener );
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

    protected final void openFile(IAssemblyProject project, final IResource sourceFile) throws IOException 
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
        this.sourceFileOnDisk = sourceFile;

        final String source = Misc.readSource( sourceFile );
        this.initialHashCode = Misc.calcHash( source );
        
        sourceInMemory = new AbstractResource(ResourceType.SOURCE_CODE) {

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
        
        updateTitle();
    }

    protected final void validateSourceCode() throws IOException {

        disableDocumentListener();
        try 
        {
            clearCompilationErrors();
            
            onSourceCodeValidation();
            
            compilationUnit = project.getBuilder().parse( sourceInMemory ,
                    new CompilationListener() );
            doHighlighting( compilationUnit , true );
        } finally {
            enableDocumentListener();
        }
    }
    
    protected void onSourceCodeValidation() {
        
    }

    protected final void doHighlighting(ICompilationUnit unit,boolean addStatusMessages) 
    {
        if ( panel == null ) {
            return;
        }

        if ( unit.getAST() != null ) 
        {
            onHighlightingStart();
            
            doSemanticHighlighting( unit );
        }

        if ( unit.hasErrors() ) 
        {
            showCompilationErrors( compilationUnit );
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
        highlight( node );
        for ( ASTNode child : node.getChildren() ) {
            doSemanticHighlighting( unit , child );
        }
    }

    protected final void highlight(ASTNode node) 
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

    protected final void highlight(ASTNode node, AttributeSet attributes) 
    {
        highlight( node.getTextRegion() , attributes );
    }

    protected final void highlight(ITextRegion range, AttributeSet attributes) 
    {
        editorPane.getStyledDocument().setCharacterAttributes( range.getStartingOffset() , range.getLength() , attributes , true );
    }

    public final void moveCursorTo(ITextRegion location) 
    {
        if ( compilationUnit == null || compilationUnit.getAST() == null ) {
            return;
        }
        editorPane.setCaretPosition( location.getStartingOffset() );
        centerCurrentLineInScrollPane();
        editorPane.requestFocus();
    }

    public final void centerCurrentLineInScrollPane()
    {
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

    public final void gotoLocation(int offset) {
        editorPane.setCaretPosition( offset );
        centerCurrentLineInScrollPane();
    }
    
    protected final ITextRegion getVisibleTextRegion() 
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
    }

    protected final void enableDocumentListener() 
    {
        documentListenerDisableCount--;     
        if ( documentListenerDisableCount == 0) 
        {
            editorPane.getDocument().addDocumentListener( recompilationListener );
        }
    }   

    protected final void showCompilationErrors(ICompilationUnit unit) 
    {
        beforeShowCompilationErrors( unit );
        
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
                
                onCompilationError( error );
            }
        } finally {
            enableDocumentListener();
        }
    }
    
    protected void beforeShowCompilationErrors(ICompilationUnit unit) {
    }   
    
    protected void onCompilationError(ICompilationError error) {
        
    }

    // ============= view creation ===================

    @Override
    public JPanel getPanel()
    {
        if ( panel == null ) {
            panel = createPanel();
            if ( this.sourceFileOnDisk != null ) {
                try {
                    validateSourceCode();
                } catch (IOException e) {
                    LOG.error("getPanel(): ",e);
                }
            }
        }
        return panel;
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

    protected final void setupKeyBindings(JTextPane editor) 
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
        
        // "Undo" action
        addKeyBinding( editor , 
                KeyStroke.getKeyStroke(KeyEvent.VK_Z,Event.CTRL_MASK),
                undoAction );
        
        addKeyBinding( editor , 
                KeyStroke.getKeyStroke(KeyEvent.VK_Y,Event.CTRL_MASK),
                redoAction );       
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
            getCurrentProject().getBuilder().build();
        } 
        catch (IOException e) {
            LOG.error("save(): Compilation failed",e);
        }
    }

    public final IAssemblyProject getCurrentProject() {
        return project;
    }

    public final IResource getCurrentResource() {
        return this.sourceFileOnDisk;
    }

    @Override
    public final void disposeHook()
    {
        workspace.removeWorkspaceListener( workspaceListener );
        disposeHook2();
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
    public IEditorView getOrCreateEditor(IAssemblyProject project, IResource resource) 
    {
        if ( resource.hasType( ResourceType.SOURCE_CODE ) ) {
            return new SourceCodeView(this.workspace, true );
        }
        throw new IllegalArgumentException("Unsupported resource type: "+resource);
    }

    @Override
    public final boolean hasUnsavedContent() 
    {
        if ( this.sourceFileOnDisk == null ) {
            return false;
        }
        return ! initialHashCode.equals( Misc.calcHash( getTextFromTextPane() ) );
    }

    @Override
    public final boolean mayBeDisposed() {
        return ! hasUnsavedContent();
    }

    @Override
    public final void openResource(IAssemblyProject project, IResource resource) throws IOException 
    {
        if ( this.project != project || this.sourceFileOnDisk != resource ) {
            openFile( project , resource );
        }
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
    
    protected final void addMouseListener(MouseListener listener) {
        editorPane.addMouseListener( listener );
    }    
    
    protected final void removeMouseListener(MouseListener listener) {
        editorPane.addMouseListener( listener );
    }
    
    protected final int getModelOffsetForLocation(Point p) {
        return editorPane.viewToModel( p );
    }    
    
}