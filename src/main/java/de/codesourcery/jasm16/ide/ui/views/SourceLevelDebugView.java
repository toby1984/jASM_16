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

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.emulator.Breakpoint;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.ui.viewcontainers.DebuggingPerspective;
import de.codesourcery.jasm16.utils.ITextRegion;

public class SourceLevelDebugView extends SourceCodeView
{
    public static final String VIEW_ID = "source-level-debug";

    private static final Logger LOG = Logger.getLogger(SourceLevelDebugView.class);
    
    private JPanel panel;
    private final IEmulator emulator;
    
    private volatile Object currentHighlight;
    private IAssemblyProject currentProject;
    private volatile ICompilationUnit currentUnit;
    private final DebuggingPerspective perspective;
    
    // @GuardedBy( breakpointHighlights )
    private final Map<Address,Object> breakpointHighlights = new HashMap<Address,Object>();    
    
    private final IEmulationListener listener = new EmulationListener() {
        
        public void breakpointAdded(IEmulator emulator, Breakpoint breakpoint) {
            highlightBreakpoint( breakpoint , true );
        }
        
        public void breakpointDeleted(IEmulator emulator, Breakpoint breakpoint) {
            highlightBreakpoint( breakpoint , false );   
        }
        
        public void afterCommandExecution(IEmulator emulator, int commandDuration) {
            refreshDisplayHook();
        }
        
        public void afterContinuousExecutionHook() {
            refreshDisplayHook();
        }
        
        public void afterReset(IEmulator emulator) {
            refreshDisplayHook();
        }
        
        public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes) {
            scrollToVisible( emulator.getCPU().getPC() , true ,true);
        }
    };
    
    public SourceLevelDebugView(IWorkspace workspace,DebuggingPerspective perspective, IEmulator emulator)
    {
        super(workspace, false);
        if ( perspective == null ) {
            throw new IllegalArgumentException("perspective must not be NULL.");
        }
        if (emulator == null) {
            throw new IllegalArgumentException("emulator must not be NULL.");
        }
        this.perspective = perspective;
        this.emulator = emulator;
        this.emulator.addEmulationListener( listener );
    }
    
    private final MouseListener mouseListener = new MouseAdapter() {
        
        public void mouseClicked(java.awt.event.MouseEvent e) 
        {
            if ( e.getButton() != MouseEvent.BUTTON3 ) {
                return;
            }
            
            final int offset = getModelOffsetForLocation( e.getPoint() );
            if ( offset == -1 ) {
                return;
            }
            
            final ASTNode n = getCurrentCompilationUnit().getAST().getNodeInRange( offset );
            if ( n == null ) {
                return;
            }
            
            final StatementNode statementNode = getStatementNode( n );
            if ( statementNode == null ) {
                return;
            }
            for ( ASTNode child : statementNode.getChildren() ) 
            {
                if ( child instanceof InstructionNode) {
                    Address address = ((InstructionNode) child).getAddress();
                    if ( address != null ) {
                        toggleBreakpoint( address );
                    }
                }
            }
        }
    };
    
    protected void disposeHook2() {
        
        if ( panel != null ) {
            removeMouseListener( mouseListener );
        }
        emulator.removeEmulationListener( listener );
    }

    private void toggleBreakpoint(Address address)
    {
        Breakpoint existing = emulator.getBreakPoint( address );
        if ( existing != null ) {
            emulator.deleteBreakpoint( existing );
        } else {
            emulator.addBreakpoint( new Breakpoint(address) );
        }
    }
    
    @Override
    public JPanel getPanel()
    {
        if ( panel == null ) 
        {
            panel = createPanel();
            addMouseListener( mouseListener );
        }
        return panel;
    }
    
    private JPanel createPanel() {
        
        final JPanel result = new JPanel();
        setColors( result );
        result.setLayout( new GridBagLayout() );
        
        final EmulatorControllerView controller = new EmulatorControllerView( perspective , emulator );
        addChild( controller );
        
        GridBagConstraints cnstrs = constraints( 0 , 0, true , false , GridBagConstraints.HORIZONTAL );
        cnstrs.weighty=0.0;
        result.add( controller.getPanel() , cnstrs );
        
        final JPanel sourceView = super.getPanel();
        cnstrs = constraints( 0 , 1, true , true, GridBagConstraints.BOTH );
        cnstrs.weighty=1.0;
        result.add( sourceView , cnstrs );        
        
        return result;
    }
    
    public void scrollToVisible(Address address) {
        scrollToVisible(address,false,false);
    }
    
    protected void scrollToVisible(Address address,boolean highlight,boolean reloadSource) {

        if ( perspective.getCurrentProject() == null ) {
            return;
        }
        
        final boolean updateParentView  = reloadSource || perspective.getCurrentProject() != this.currentProject;
        if ( updateParentView ) {
            this.currentProject = perspective.getCurrentProject();
        }
        
        final ICompilationUnit unit = getCompilationUnitForAddress( address );
        if ( unit == null ) {
            System.out.println("Found no source for address "+address);
            return;
        }
        
        final StatementNode node = getStatementNodeForAddress( unit , address );
        if ( node != null ) 
        {
            if ( updateParentView || this.currentUnit == null || ! this.currentUnit.getResource().isSame( unit.getResource() ) ) 
            {
                switchToCompilationUnit( perspective.getCurrentProject() , unit );
                highlightBreakpoints();
            }
            
            final ITextRegion region = node.getTextRegion();
            
            // scroll to current location
            gotoLocation( region.getStartingOffset() );
            
            if ( highlight ) 
            {
                // highlight location
                try 
                {            
                    if ( currentHighlight == null ) 
                    {
                        currentHighlight = getHighlighter().addHighlight( 
                                region.getStartingOffset() , 
                                region.getEndOffset() , 
                                new DefaultHighlighter.DefaultHighlightPainter(Color.WHITE) );
                    } else {
                        getHighlighter().changeHighlight( currentHighlight ,
                                region.getStartingOffset() ,
                                region.getEndOffset() );
                    }
                } catch (BadLocationException e) {
                    LOG.error("refreshDisplayHook(): ",e);
                }      
            }
        } else {
            System.out.println("Failed to locate AST node for address "+address);
        }
            
    }
    
    @Override
    protected void refreshDisplayHook()
    {
        scrollToVisible( emulator.getCPU().getPC() , true ,false);
    }
    
    private void highlightBreakpoints() 
    {
        final List<Breakpoint> copy;
        synchronized(breakpointHighlights) {
            copy = emulator.getBreakPoints();
        }
        for ( Breakpoint bp : copy ) {
            highlightBreakpoint(bp,true);
        }
    }
    
    private void highlightBreakpoint(Breakpoint bp,boolean renderHighlight)
    {
        StatementNode node = getStatementNodeForAddress( this.currentUnit , bp.getAddress() );
        if ( node == null ) {
            return;
        }
        
        final Object existingHighlight;
        synchronized (breakpointHighlights) {
            existingHighlight = breakpointHighlights.get(bp.getAddress());
        }
        
        if ( existingHighlight != null && ! renderHighlight ) 
        {
            getHighlighter().removeHighlight( existingHighlight );
            
        } 
        else if ( existingHighlight == null && renderHighlight ) 
        {
            final ITextRegion region = node.getTextRegion();
            try {
                final Object tag = getHighlighter().addHighlight( 
                        region.getStartingOffset() , 
                        region.getEndOffset() , 
                        new DefaultHighlighter.DefaultHighlightPainter(Color.RED) );
                synchronized (breakpointHighlights) 
                {
                    breakpointHighlights.put( bp.getAddress() , tag );
                }
            } 
            catch (BadLocationException e) {
                LOG.error("highlightBreakpoint(): ",e);
            }
        }
    }

    private void switchToCompilationUnit(IAssemblyProject project,ICompilationUnit unit) 
    {
        try {
            clearHighlights();
            openFile( this.currentProject , unit.getResource() );
        } catch (IOException e) {
            LOG.error("refreshDisplayHook(): Caught ",e);
            return;
        }
        this.currentUnit = unit;
    }
    
    private void clearHighlights()
    {
        synchronized (breakpointHighlights) {
            getHighlighter().removeAllHighlights();
            breakpointHighlights.clear();
            this.currentHighlight = null;
        }
    }
    
    protected StatementNode getStatementNodeForAddress(final ICompilationUnit unit , final Address address) 
    {
        return getStatementNode( getASTNodeForAddress(unit,address) );
    }
    
    protected StatementNode getStatementNode(ASTNode node) {
        ASTNode current = node;
        while ( current != null ) {
            if ( current instanceof StatementNode) {
                return (StatementNode) current;
            }
            current = current.getParent();
        }
        return null;
    }

    protected ASTNode getASTNodeForAddress(final ICompilationUnit unit , final Address address) 
    {
        final ObjectCodeOutputNode[] result = {null};
        
        final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {
            
            @Override
            public boolean visit(ASTNode node)
            {
                if ( node instanceof ObjectCodeOutputNode) {
                    if ( ((ObjectCodeOutputNode) node).getAddress().equals( address ) ) {
                        result[0] = (ObjectCodeOutputNode) node;
                        return false;
                    }
                }
                return true;
            }
        };
        ASTUtils.visitInOrder( unit.getAST() , visitor );
        return result[0];
    }    
    
    private ICompilationUnit getCompilationUnitForAddress(Address address) 
    {
        for ( ICompilationUnit unit : this.currentProject.getBuilder().getCompilationUnits() ) 
        {
            if ( unit.getAST() != null ) {
                final Address start = ASTUtils.getEarliestMemoryLocation( unit.getAST() );
                Address end = ASTUtils.getLatestMemoryLocation( unit.getAST() );
                end = end.incrementByOne( false ); // AddressRange is (startInclusive, endExclusive[
                if ( start != null && end != null ) {
                    if ( new AddressRange( start , end ).contains( address ) ) {
                        return unit;
                    }
                }
            }
        }
        
        return null;
    }
    
    @Override
    public String getTitle()
    {
        return "Source-level debug";
    }

    @Override
    public String getID()
    {
        return VIEW_ID;
    }
    
}
