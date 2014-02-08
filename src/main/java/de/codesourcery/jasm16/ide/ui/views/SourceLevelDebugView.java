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
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.ast.SymbolReferenceNode;
import de.codesourcery.jasm16.compiler.Equation;
import de.codesourcery.jasm16.compiler.Executable;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.compiler.io.DefaultResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.emulator.Breakpoint;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.memory.MemUtils;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.NavigationHistory;
import de.codesourcery.jasm16.ide.ui.MenuManager;
import de.codesourcery.jasm16.ide.ui.MenuManager.MenuEntry;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.viewcontainers.DebuggingPerspective;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Misc;

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
        @Override
        public void breakpointAdded(IEmulator emulator, final Breakpoint breakpoint) {
            UIUtils.invokeLater( new Runnable() {
                @Override
                public void run() 
                {            
                    highlightBreakpoint( breakpoint , true );
                }
            });
        }
        @Override
        public void breakpointDeleted(IEmulator emulator, final Breakpoint breakpoint) {
            
            UIUtils.invokeLater( new Runnable() {
                @Override
                public void run() 
                {            
                    highlightBreakpoint( breakpoint , false );
                }
            });
        }
        
        @Override
        public void afterCommandExecution(IEmulator emulator, int commandDuration) {
            refreshDisplayHook();
        }
        
        @Override
        public void onStopHook(final IEmulator emulator, final Address previousPC, final Throwable emulationError) 
        {
            UIUtils.invokeLater( new Runnable() {
                @Override
                public void run() 
                {
                    if ( emulationError != null ) 
                    {
                        if ( ! scrollToVisible( emulator.getCPU().getPC() , true , false ) ) {
                            scrollToVisible( previousPC , true , false );
                        }                
                    } else {
                        refreshDisplayHook();
                    }
                } 
            });
        }
        
        @Override
        public void afterReset(IEmulator emulator) {
            refreshDisplayHook();
        }
        @Override
        public void afterMemoryLoad(final IEmulator emulator, Address startAddress, int lengthInBytes) {
            UIUtils.invokeLater( new Runnable() {
                public void run() {
                    scrollToVisible( emulator.getCPU().getPC() , true ,true);  
                }
            });
        }
    };
    
    public SourceLevelDebugView(IResourceResolver resourceResolver,
    		IWorkspace workspace,
    		DebuggingPerspective perspective, 
    		NavigationHistory navigationHistory,
    		IEmulator emulator)
    {
        super(resourceResolver,workspace, navigationHistory,false);
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
    
    private final MouseAdapter mouseListener = new MouseAdapter() {
        
    	@Override
    	public void mouseMoved(MouseEvent e) 
    	{
            final ASTNode n = getASTNode( e.getPoint() );
            if ( n != null && ( n instanceof SymbolReferenceNode || n instanceof LabelNode) ) 
            {
            	ISymbol symbol = null;
            	if ( n instanceof SymbolReferenceNode) {
            		symbol = ((SymbolReferenceNode) n).resolve( currentUnit.getSymbolTable() , true );
            		if ( symbol == null ) {
            			System.err.println("Failed to resolve symbol: "+n);
            		}
            	} 
            	else 
            	{
            		symbol = ((LabelNode) n).getLabel();
            	}
            	
            	if ( symbol != null && symbol instanceof Label) 
            	{
                	final Address dumpStartAddress = ((Label) symbol).getAddress();
                	
                	final int WORDS_TO_SHOW = 6;
                	final byte[] bytes = MemUtils.getBytes( emulator.getMemory() , dumpStartAddress , Size.words(WORDS_TO_SHOW) , true );
                	
            		final String tooltip = Misc.toHexDumpWithAddresses(dumpStartAddress, 
            				bytes, bytes.length ,  WORDS_TO_SHOW , true , true );
            		
            		showTooltip( tooltip );
            	} 
            	else if ( symbol != null && symbol instanceof Equation ) 
            	{
            	    Equation eq = (Equation) symbol;
            	    ISymbolTable table = currentUnit.getSymbolTable();
            	    if ( table.getParent() != null ) {
            	        table = table.getParent();
            	    }
            	    Long value = eq.getValue( table );
            	    if ( value != null ) 
            	    {
            	        if ( value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                            showTooltip( "0x"+Misc.toHexString( value.intValue() ) +" ("+value.intValue()+")" );            	            
            	        } else {
            	            showTooltip( "0x"+Misc.toHexString( value.longValue() ) +" ("+value.longValue()+")" );
            	        }
            	    }
            	} else {
            		clearTooltip();
            	}
            } else if ( n != null && n instanceof RegisterReferenceNode) {
                RegisterReferenceNode reg = (RegisterReferenceNode) n;
                final int value = emulator.getCPU().getRegisterValue( reg.getRegister() );
                showTooltip( "0x"+Misc.toHexString( value ) +" ("+value+")" ); 
            } else {
            	clearTooltip();            	
            }
    	}
    	
    	@Override
        public void mouseClicked(java.awt.event.MouseEvent e) 
        {
            if ( e.getButton() != MouseEvent.BUTTON3 ) {
                return;
            }
            
            final ASTNode n = getASTNode( e.getPoint() );
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
    	
    	private ASTNode getASTNode(Point p) 
    	{
            final int offset = getModelOffsetForLocation( p );
            if ( offset == -1 ) {
                return null;
            }
            
            final ICompilationUnit unit = getCurrentCompilationUnit();
            if ( unit == null ) {
                return null;
            }
            return unit.getAST().getNodeInRange( offset );
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
            setupMenu( getViewContainer().getMenuManager() );
        }
        return panel;
    }
    
    private void setupMenu(MenuManager menuManager) {
    
    	final MenuEntry entry = new MenuEntry("Options/Emulation options") {
			
			@Override
			public void onClick() 
			{
				if ( currentProject == null ) {
					return;
				}
				
				EmulationOptionsView view = (EmulationOptionsView) getViewContainer().getViewByID( EmulationOptionsView.ID );				
				if ( view == null ) 
				{
						view = new EmulationOptionsView() 
						{
							protected void onSave(de.codesourcery.jasm16.emulator.EmulationOptions options) 
							{
								// close window
								getViewContainer().disposeView( this );
								
								// apply changes
								options.apply( emulator );
								currentProject.setEmulationOptions( options );
								
								try {
									workspace.saveProjectConfiguration( currentProject );
								} 
								catch (IOException e) {
									LOG.error("setupMenu(): Failed to save options for project "+currentProject,e);
								}
								
								if ( options.isNewEmulatorInstanceRequired() ) 
								{
								    perspective.reloadEmulator();
								}
							}
							
							protected void onCancel() {
								getViewContainer().disposeView( this );
							}
						};
						getViewContainer().addView( view );
				} 
				getViewContainer().toFront( view );
				view.setData( currentProject.getEmulationOptions() );
			}
		};
		menuManager.addEntry( entry );
    }
    
    private JPanel createPanel() {
        
        final JPanel result = new JPanel();
        setColors( result );
        result.setLayout( new GridBagLayout() );
        
        final EmulatorControllerView controller = new EmulatorControllerView( perspective , emulator );
        addChild( controller );
        
        GridBagConstraints cnstrs = constraints( 0 , 0, true , false , GridBagConstraints.HORIZONTAL );
        cnstrs.weighty=0.0;
        result.add( controller.getPanel( getViewContainer() ) , cnstrs );
        
        final JPanel sourceView = super.getPanel();
        cnstrs = constraints( 0 , 1, true , true, GridBagConstraints.BOTH );
        cnstrs.weighty=1.0;
        result.add( sourceView , cnstrs );        
        
        return result;
    }
    
    public void scrollToVisible(Address address) {
        scrollToVisible(address,false,false);
    }
    
    protected boolean scrollToVisible(Address address,boolean highlight,boolean reloadSource) {

        if ( perspective.getCurrentProject() == null ) {
            return false;
        }
        
        final boolean updateParentView  = reloadSource || perspective.getCurrentProject() != this.currentProject;
        if ( updateParentView ) {
            this.currentProject = perspective.getCurrentProject();
        }
        
        final Executable executable = this.currentProject.getProjectBuilder().getExecutable();
        if ( executable == null ) {
            return false;
        }
        
        final SourceLocation loc = executable.getDebugInfo().getSourceLocation( address );
        if ( loc == null ) {
            System.out.println("Found no source for address "+address);
            return false;
        }
        
        if ( updateParentView || this.currentUnit == null ||
             ! DefaultResourceMatcher.INSTANCE.isSame( this.currentUnit.getResource() , loc.getCompilationUnit().getResource() ) ) 
        {
            switchToCompilationUnit( perspective.getCurrentProject() , loc.getCompilationUnit() );
            highlightBreakpoints();
        }
        
        // scroll to current location
        gotoLocation( loc.getStartingOffset() );
        
        if ( highlight ) 
        {
            // highlight location
            try 
            {            
                if ( currentHighlight == null ) 
                {
                    currentHighlight = getHighlighter().addHighlight( 
                            loc.getStartingOffset() , 
                            loc.getEndOffset() , 
                            new DefaultHighlighter.DefaultHighlightPainter(Color.GREEN) );
                } else {
                    getHighlighter().changeHighlight( currentHighlight ,
                            loc.getStartingOffset() ,
                            loc.getEndOffset() );
                }
                return true;
            } catch (BadLocationException e) {
                LOG.error("refreshDisplayHook(): ",e);
            }      
        }
        return false;
    }
    
    @Override
    protected void refreshDisplayHook()
    {
        UIUtils.invokeLater( new Runnable() {
            @Override
            public void run() 
            {        
                scrollToVisible( emulator.getCPU().getPC() , true ,false);
            }
        });
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
        	disableDocumentListener();
            clearHighlights();
            openResource( this.currentProject , unit.getResource() , false );
        } catch (IOException e) {
            LOG.error("refreshDisplayHook(): Caught ",e);
            return;
        } finally {
        	enableDocumentListener();
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
