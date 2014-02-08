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
package de.codesourcery.jasm16.ast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.GenericCompilationError;
import de.codesourcery.jasm16.compiler.ICompilationError;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.exceptions.EOFException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.scanner.IScanner;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * Abstract base-class of all AST nodes.
 * 
 * <p>AST nodes are created for one or more tokens in the input stream.</p>
 * <p>Each AST node keeps track of the source code location ({@link ITextRegion} it
 * was created from so editors etc. have an easy time associating source code
 * with the AST.</p>
 * 
 * <p>Keeping track of the source code locations is slightly complicated
 * because not all tokens (e.g. whitespace,EOL) become part of the AST, so this
 * class actually uses two {@link ITextRegion} fields to keep track of the
 * source code range covered by the AST node (or it's children) and
 * the input that was actually traversed while this subtree was constructed.</p>.   
 * 
 * <p>Make sure you understand how {@link #getTextRegion()} , {@link #recalculateTextRegion(boolean)}, 
 * {@link #setTextRegionIncludingAllTokens(ITextRegion)} and {@link #mergeWithAllTokensTextRegion(ASTNode)}
 * work.</p>
 * 
 * <p>This class also implements the parse error recovery mechanism, check out {@link #parse(IParseContext)} to
 * see how it actually works.</p> 
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class ASTNode
{
    private static final Logger LOG = Logger.getLogger(ASTNode.class);

    /**
     * Default tokens to look for when trying to recover from
     * a parse error.
     * 
     * @see IParseContext#setErrorRecoveryTokenTypes(TokenType[])
     * @see ILexer#advanceTo(TokenType, boolean)
     */
    public static final TokenType[] DEFAULT_ERROR_RECOVERY_TOKEN = new TokenType[]{TokenType.EOL, TokenType.SINGLE_LINE_COMMENT};	

    private ASTNode parent;
    private final List<ASTNode> children = new ArrayList<ASTNode>();

    /** 
     *  This text range covers <b>all</b> tokens that were consumed while
     *  parsing this node (including whitespace, EOL etc.).
     */
    private ITextRegion textRegionIncludingAllTokens;

    /** 
     * Cached value of {@link #textRegionIncludingAllTokens} plus {@link #getTextRegion()} of all
     * child nodes.
     */
    private ITextRegion actualTextRegion;

    /**
     * Creates a new instance.
     * 
     * <p>This instance will have no parent
     * and {@link #textRegionIncludingAllTokens} and {@link #actualTextRegion}
     * will be <code>null</code>.</p>
     */
    public ASTNode() {
    }

    /**
     * Creates a new AST node for a given source code location.
     * 
     * @param allTokensRegion text range covered by <b>this</b> node, never <code>null</code>
     */
    protected ASTNode(ITextRegion allTokensRegion) 
    {
        if (allTokensRegion == null) {
            throw new IllegalArgumentException("allTokensRegion must not be NULL.");
        }
        this.textRegionIncludingAllTokens = new TextRegion( allTokensRegion );        
    }	

    /**
     * Returns the actual source code region covered by 
     * this AST node (and it's child nodes).
     * 
     * <p>Due to the way parsing works (recursive descent...), an AST node always 
     * covers at least the text range that is covered by it's child nodes.</p>
     * 
     * <p>
     * The actual source code region covered by <b>this</b> node is composed
     * of the actual source code regions of all child nodes (=invoking
     * {@link #getTextRegion()} on each child) <b>PLUS</b> 
     * this node's <i>'all-tokens' text range</i> (see .
     * </p>
     * 
     * @return
     * @see #mergeTextRegion(ITextRegion)
     * @see #mergeWithAllTokensTextRegion(ASTNode)
     */
    public final ITextRegion getTextRegion() 
    {
        if ( actualTextRegion == null ) 
        {
            recalculateTextRegion(true);
            return actualTextRegion;
        }
        return actualTextRegion;
    }

    public final void adjustTextRegion(int offsetAdjust,int lengthAdjust) throws IllegalStateException 
    {
    	if ( hasChildren() ) {
    		throw new IllegalStateException("adjustTextRegion() may only be called on LEAF nodes");
    	}
    	
    	final ITextRegion current = getTextRegion();
    	if ( current == null ) {
    		throw new IllegalStateException("Node "+this+" has no text region assigned ?");
    	}

    	if ( textRegionIncludingAllTokens != null ) 
    	{
    		textRegionIncludingAllTokens = new TextRegion( current.getStartingOffset() + offsetAdjust , current.getLength() + lengthAdjust );
    	} 
   		actualTextRegion = new TextRegion( current.getStartingOffset() + offsetAdjust , current.getLength() + lengthAdjust );
   		recalculateTextRegion( true );
    }
    
    /**
     * Merges the actual source code region covered by a node with
     * this node's {@link #textRegionIncludingAllTokens}.
     * 
     * <p>This method is used during expression folding/evaluation to
     * preserve the text range covered by an AST node when an AST node
     * gets replaced with a newly created node representing calculated value.
     * </p>
     * <p>Not using this method during expression folding will cause the location
     * of all tokens that do not resemble actual AST nodes (read: almost all tokens)
     * to be permanently lost.</p>
     * 
     * @param node
     * @see ITextRegion#merge(ITextRegion)     
     */
    protected final void mergeWithAllTokensTextRegion(ASTNode node) 
    {
        mergeWithAllTokensTextRegion( node.getTextRegion() );
    }

    /**
     * Merges the  source code region with this node's {@link #textRegionIncludingAllTokens}.
     * 
     * <p>This method is used during expression folding/evaluation to
     * preserve the text range covered by an AST node when an AST node
     * gets replaced with a newly created node representing calculated value.
     * </p>
     * <p>Not using this method during expression folding will cause the location
     * of all tokens that do not resemble actual AST nodes (read: almost all tokens)
     * to be permanently lost.</p>
     * 
     * @param range
     * @see ITextRegion#merge(ITextRegion)
     */    
    protected final void mergeWithAllTokensTextRegion(List<? extends ITextRegion> range) 
    {
    	for ( ITextRegion r : range ) {
    		mergeWithAllTokensTextRegion(r);
    	}
    }
    
    /**
     * Merges the  source code region with this node's {@link #textRegionIncludingAllTokens}.
     * 
     * <p>This method is used during expression folding/evaluation to
     * preserve the text range covered by an AST node when an AST node
     * gets replaced with a newly created node representing calculated value.
     * </p>
     * <p>Not using this method during expression folding will cause the location
     * of all tokens that do not resemble actual AST nodes (read: almost all tokens)
     * to be permanently lost.</p>
     * 
     * @param range
     * @see ITextRegion#merge(ITextRegion)
     */    
    protected final void mergeWithAllTokensTextRegion(ITextRegion range) 
    {
        if ( this.textRegionIncludingAllTokens == null && this.actualTextRegion != null ) {
            this.textRegionIncludingAllTokens = this.actualTextRegion;
        }

        if ( this.textRegionIncludingAllTokens == null ) 
        {
            this.textRegionIncludingAllTokens = new TextRegion( range );
        } else {
            this.textRegionIncludingAllTokens.merge( range );
        }

        if ( this.actualTextRegion != null ) {
            this.actualTextRegion = null;
            if ( getParent() != null ) { // maybe a parent node already called getTextRegion() on this child...
                getParent().recalculateTextRegion(true);
            }
        }
    }

    protected final void mergeTextRegion(ITextRegion range) 
    {
        final int oldValue = TextRegion.hashCode( this.actualTextRegion );

        if ( this.actualTextRegion == null && textRegionIncludingAllTokens != null) 
        {
            this.actualTextRegion = new TextRegion( textRegionIncludingAllTokens );
        }

        if ( this.actualTextRegion != null ) {
            this.actualTextRegion.merge( range );
        } else {
            this.actualTextRegion  = new TextRegion( range );	    	
        }

        if ( oldValue != TextRegion.hashCode( this.actualTextRegion ) && getParent() != null ) {
            getParent().mergeTextRegion( this.actualTextRegion );
        }
    }

    protected void setTextRegionIncludingAllTokens( ITextRegion textRegion ) 
    {
        this.textRegionIncludingAllTokens = new TextRegion( textRegion );
        this.actualTextRegion = null;
        recalculateTextRegion(true);
    }

    private void recalculateTextRegion(boolean recalculateParents) 
    {
        final int oldValue = TextRegion.hashCode( this.actualTextRegion );

        ITextRegion range = textRegionIncludingAllTokens != null ? new TextRegion( textRegionIncludingAllTokens ) : null;

        for ( ASTNode child : this.children) 
        {
            if ( range == null ) {
                range = new TextRegion( child.getTextRegion() );
            } else {
            	if ( child.getTextRegion() == null ) {
            		throw new IllegalStateException("Child "+child+" has NULL text region?");
            	}
                range.merge( child.getTextRegion() );
            }
        }

        this.actualTextRegion = range;

        if ( recalculateParents &&
             oldValue != TextRegion.hashCode( this.actualTextRegion ) && 
             getParent() != null ) 
        {
            getParent().recalculateTextRegion(true);
        }
    }

    /**
     * Creates a copy of this AST node (and optionally all it's children recursively).
     * 
     * @param shallow whether to only copy this node or also recursively clone
     * all child nodes as well.
     * 
     * @return
     */
    public ASTNode createCopy(boolean shallow) {

        ASTNode result = copySingleNode();
        if ( actualTextRegion != null ) {
            result.actualTextRegion = new TextRegion( actualTextRegion );
        }
        if ( textRegionIncludingAllTokens != null ) {
            result.textRegionIncludingAllTokens = new TextRegion( textRegionIncludingAllTokens );
        }
        if ( ! shallow ) {
            for ( ASTNode child : children ) {
                final ASTNode copy = child.createCopy( shallow );
                result.addChild( copy , null );
            }
        }
        return result;
    }

    /**
     * Returns an <b>independent</b> copy of this node <b>without</b>
     * any of it's children.
     * 
     * @return
     */
    protected abstract ASTNode copySingleNode(); 

    /**
     * Check this AST node or any of it's child nodes is of class
     * {@link UnparsedContentNode}.
     * 
     * @return
     */
    public final boolean hasErrors() 
    {
        final boolean[] hasErrors = new boolean[] { false };

        final ISimpleASTNodeVisitor<UnparsedContentNode> visitor = new ISimpleASTNodeVisitor<UnparsedContentNode>() {

            @Override
            public boolean visit(UnparsedContentNode node) 
            {
                hasErrors[0] = true;
                return false;
            }
        };
        ASTUtils.visitNodesByType( this , visitor , UnparsedContentNode.class );
        return hasErrors[0];
    }    

    /**
     * Swap a direct child of this node with some other node.
     *  
     * @param childToSwap
     * @param otherNode
     */
    public final void swapChild( ASTNode childToSwap, ASTNode otherNode) {

        if ( childToSwap == null ) {
            throw new IllegalArgumentException("childToSwap must not be NULL");
        }
        if ( otherNode == null ) {
            throw new IllegalArgumentException("otherNode must not be NULL");
        }

        assertSupportsChildNodes();

        final int idx = children.indexOf( childToSwap );
        if ( idx == -1 ) 
        {
            throw new IllegalArgumentException("Node "+childToSwap+" is not a child of "+this);
        }

        final ASTNode otherParent = otherNode.getParent();
        if ( otherParent == null ) {
            throw new IllegalArgumentException("Node "+otherNode+" has no parent?");
        }

        final int otherIdx = otherParent.indexOf( otherNode );
        setChild( idx , otherNode );
        otherParent.setChild( otherIdx , childToSwap );
        recalculateTextRegion(true);
        otherParent.recalculateTextRegion( true );
    }

    /**
     * Returns the index of a direct child.
     *  
     * @param node
     * @return
     */
    public final int indexOf(ASTNode node) {
        return children.indexOf( node );
    }

    /**
     * Inserts a new child node at a specific position.
     * 
     * @param index
     * @param newChild
     * @param context parse context or <code>null</code>. If the context is not <code>null</code> and
     * the node being added is <b>not</b> an instance of {@link UnparsedContentNode} , the parse
     * contexts error recovery flag ({@link IParseContext#isRecoveringFromParseError()}) will be reset. See {@link ASTNode#parse(IParseContext)} for
     * a detailed explanation on parser error recovery.     
     * @return
     */
    public final ASTNode insertChild(int index,ASTNode newChild,IParseContext context) 
    {
    	return insertChild(index,newChild,context,true);
    }
    
    /**
     * Inserts a new child node at a specific position.
     * 
     * @param index
     * @param newChild
     * @param context parse context or <code>null</code>. If the context is not <code>null</code> and
     * the node being added is <b>not</b> an instance of {@link UnparsedContentNode} , the parse
     * contexts error recovery flag ({@link IParseContext#isRecoveringFromParseError()}) will be reset. See {@link ASTNode#parse(IParseContext)} for
     * @param mergeTextRegion whether this node's text region should be joined with the new node
     * a detailed explanation on parser error recovery.     
     * @return
     */    
    public final ASTNode insertChild(int index,ASTNode newChild,IParseContext context,boolean mergeTextRegion) 
    {
        try {
            return addChild( index , newChild , mergeTextRegion );
        } finally {
            if ( context != null ) {
                context.setRecoveringFromParseError( false );
            }
        }	        
    }    

    /**
     * Replaces a child node at a specific position.
     *   
     * @param index
     * @param newChild
     */
    public final void setChild( int index, ASTNode newChild) 
    {
        if ( newChild == null ) {
            throw new IllegalArgumentException("newChild must not be NULL");
        }
        if ( index < 0 || index >= children.size() ) {
            throw new IndexOutOfBoundsException("Invalid index "+index+" ( must be >= 0 and < "+children.size()+")");
        }
        assertSupportsChildNodes();
        children.set( index , newChild );
        newChild.setParent( this );
    }

    /**
     * Returns the Nth child.
     * 
     * @param index
     * @return
     * @throws IndexOutOfBoundsException if the index is either less than zero or larger than {@link #getChildCount()} -1
     */
    public final ASTNode child(int index) 
    {
        if ( index < 0 || index >= children.size() ) {
            throw new IndexOutOfBoundsException("Invalid index "+index+" , node "+this+
                    " has only "+children.size()+" children");
        }
        return children.get( index );
    }

    /**
     * Adds child nodes to this node.
     * 
     * @param nodes
     * @param context parse context or <code>null</code>. If the context is not <code>null</code> and
     * the node being added is <b>not</b> an instance of {@link UnparsedContentNode} , the parse
     * contexts error recovery flag ({@link IParseContext#isRecoveringFromParseError()}) will be reset. See {@link ASTNode#parse(IParseContext)} for
     * a detailed explanation on parser error recovery.     
     */
    public final void addChildren(Collection<? extends ASTNode> nodes,IParseContext context) 
    {
        if (nodes == null) {
            throw new IllegalArgumentException("node must not be NULL.");
        }

        boolean recovering = context == null ? false : context.isRecoveringFromParseError();
        try {
            for ( ASTNode node : nodes) 
            {
                if ( recovering && !(node instanceof UnparsedContentNode) ) {
                    recovering = false;
                }
                addChild( 0 , node );
            }
        } finally {
            if ( context != null ) {
                context.setRecoveringFromParseError( recovering );
            }
        }
    }    

    /**
     * Returns whether this AST node supports having child nodes.
     *
     * <p>If a node does not support having child nodes, calling 
     * and of the methods that add/change child nodes will trigger
     * an {@link UnsupportedOperationException}.
     * 
     * @return
     */
    public abstract boolean supportsChildNodes();

    /**
     * Add a child node.
     * 
     * @param node
     * @param context parse context or <code>null</code>. If the context is not <code>null</code> and
     * the node being added is <b>not</b> an instance of {@link UnparsedContentNode} , the parse
     * contexts error recovery flag ({@link IParseContext#isRecoveringFromParseError()}) will be reset. See {@link ASTNode#parse(IParseContext)} for
     * a detailed explanation on parser error recovery.
     * @return
     */
    public final ASTNode addChild(ASTNode node,IParseContext context) 
    {
    	return addChild(node,context,true);
    }
    
    /**
     * Add a child node.
     * 
     * @param node
     * @param context parse context or <code>null</code>. If the context is not <code>null</code> and
     * the node being added is <b>not</b> an instance of {@link UnparsedContentNode} , the parse
     * contexts error recovery flag ({@link IParseContext#isRecoveringFromParseError()}) will be reset. See {@link ASTNode#parse(IParseContext)} for
     * a detailed explanation on parser error recovery.
     * @param mergeTextRegion whether to merge the text region from the newly added child with the subtree
     * this node is in
     * @return
     */
    public final ASTNode addChild(ASTNode node,IParseContext context,boolean mergeTextRegion) 
    {
        try {
            return addChild( children.size() , node , mergeTextRegion );
        } finally {
            if ( context != null && context.isRecoveringFromParseError() && !(node instanceof UnparsedContentNode) ) {
                context.setRecoveringFromParseError( false );
            }
        }
    }
    
    /**
     * (INTERNAL USE ONLY , macro expansion).
     * 
     * @param node
     */
    public final void internalAddChild(ASTNode node) 
    {
    	addChild( children.size() , node , false );
    }

    private final ASTNode addChild(int index , ASTNode node) 
    {
    	return addChild(index,node,true);
    }
    
    private final ASTNode addChild(int index , ASTNode node,boolean mergeTextRegion) 
    {
        if (node == null) {
            throw new IllegalArgumentException("node must not be NULL.");
        }

        // all nodes must accept this
        if ( !(node instanceof UnparsedContentNode ) ) {
            assertSupportsChildNodes();
        }

        if ( index == children.size() ) {
            this.children.add( node );
        } else if ( index < 0 ) {
            throw new IndexOutOfBoundsException("Invalid child index "+index);
        } else if ( index < children.size() ) {
            this.children.add( index , node );
        } else {
            throw new IndexOutOfBoundsException("Child index "+index+" is out of range, node "+this+" only has "+getChildCount()+" children.");		    
        }
        
        if ( mergeTextRegion ) 
        {
	        if ( node.textRegionIncludingAllTokens != null ) 
	        {
	            if ( this.textRegionIncludingAllTokens == null ) {
	                this.textRegionIncludingAllTokens = new TextRegion( node.textRegionIncludingAllTokens );
	            } else {
	                this.textRegionIncludingAllTokens.merge( node.textRegionIncludingAllTokens );
	            }
	        } 
	        mergeTextRegion( node.getTextRegion() );
        }
        
        node.setParent( this );
        return node;
    }

    protected final void assertSupportsChildNodes() 
    {
        if ( ! supportsChildNodes() ) {
            throw new UnsupportedOperationException("Cannot add children to node "+this+" that does not support child nodes");
        }		
    }

    /**
     * Returns the number of direct children this node has.
     * 
     * @return
     */
    public final int getChildCount() {
        return children.size();
    }

    /**
     * Returns whether this node has any children.
     * 
     * @return
     */
    public final boolean hasChildren() {
        return ! children.isEmpty();
    }

    /**
     * Returns the child nodes of this node.
     * 
     * @return
     */
    public final List<ASTNode> getChildren() 
    {
        return new ArrayList<ASTNode>( children );
    }

    private final void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Returns the parent node of this node.
     * 
     * @return parent or <code>null</code> if this node has no parent
     */
    public final ASTNode getParent()
    {
        return parent;
    }

    /**
     * Parse source code (recursive decent parsing).
     * 
     * <p>
     * This method delegates to {@link #parseInternal(IParseContext)} and 
     * takes care of handling any <code>Exceptions</code> thrown by this
     * method appropriately. 
     * </p>
     * <p>
     * The idiom used to continue parsing after encountering a parse error
     * is (see below for a detailed explanation): 
     * <pre>
     *      try {
     *          context.mark();
     *          // setErrorRecoveryTokens( TOKENS );
     *          addChild( new SomeASTNode().parseInternal( context );
     *      } catch(Exception e) {
     *          addCompilationErrorAndAdvanceParser( e , context );
     *      } finally {
     *          context.clearMark();
     *          // setErrorRecoveryTokens( DEFAULT_TOKENS );
     *      }
     *      // continue here regardless of parse error
     * </pre>
     * </p>
     * <p>
     * <h3>Parse error recovery</h3>
     * </p>
     * <p>Parse error recovery is tricky because it involves several different
     * parts of the application to interact correctly.</p>
     * <p><h4>Part 1 - The scanner</h4></p>
     * <p>The {@link IScanner} provides random access to the input stream using the {@link IScanner#setCurrentParseIndex(int)} method.</p>
     * <p><h4>Part 2 - The lexer</h4></p>
     * <p>The {@link ILexer} internally manages a stack of state information (current line number, line starting offset, current parse index,parsed tokens).
     * This  state can be remembered/recalled using the {@link ILexer#mark()} , {@link ILexer#reset()} methods. The {@link ILexer#clearMark()} method
     * removes the last remembered state from the internal stack.</p>
     * <p><h4>Part 3 - {@link #parse(IParseContext)}</h4></p>
     * <p>Upon entry, this method remembers the lexer's state by calling {@link ILexer#mark()}. It then invokes {@link #parseInternal(IParseContext)} inside
     * a <code>try/finally</code> block. If the <code>parseInternal()</code> method fails with an exception, {@link #addCompilationErrorAndAdvanceParser(Exception, IParseContext)} is invoked.
     * The <code>finally</code> block of this method calls {@link ILexer#clearMark()} to remove the no longer needed lexer state information from the lexer's internal stack
     * and ensures that even if we saw a {@link OutOfMemoryError}, the lexer's internal stack does not grow infinitely.</p>
     * <p><h4>Part 4 - {@link #addCompilationErrorAndAdvanceParser(Exception, IParseContext)}</h4></p>
     * <p>This method first invokes {@link ILexer#reset()} to reset the lexer to the state it was when {@link #parse(IParseContext)} got called. It then
     * uses {@link ILexer#advanceTo(TokenType[], boolean)} to advance until a suitable token (see {@link #getParseRecoveryTokenTypes()} is found.
     * </p>     
     * <p>All tokens skipped during advancing will combined into a {@link UnparsedContentNode} that is attached to <b>this</b> node.</p>
     * <p>If the parse context is <b>not</b> in recovery mode yet (see {@link IParseContext#isRecoveringFromParseError()} , an {@link ICompilationError} will be 
     * added to the current compilation unit using {@link ICompilationUnit#addMarker(de.codesourcery.jasm16.compiler.IMarker)} and
     * the context will switch to error recovery mode by invoking {@link IParseContext#setRecoveringFromParseError(boolean)}}.</p>
     * <p>If the parse context was already in recovery mode when {@link #onError(Exception, IParseContext)} got invoked, <b>no</b> compilation error will
     * be added to the current compilation unit since we obviously haven't recovered from the last error yet.</p>
     * <p><h4>Part 5 - {@link ASTNode#addChild(ASTNode, IParseContext)} and friends</h4></p>
     * All {@link ASTNode} methods that actually add one or more new child nodes to a node will reset the parse' contexts error recovery flag when 
     * the node being added is <b>not</b> an instance of {@link UnparsedContentNode}.</p>
     *        
     * @param context
     * @return AST node parsed from the current parse position. Usually that
     * will be an instance of the class this method was invoked on but in case 
     * of compilation errors this method may just return an {@link UnparsedContentNode}
     * so be <b>careful</b> when assuming the actual type returned by this method.
     */
    public final ASTNode parse(IParseContext context) 
    {
        context.mark();
        try {
            ASTNode result = parseInternal( context );
            return result;
        } 
        catch(Exception e) 
        {
            return addCompilationErrorAndAdvanceParser( e , context );
        } finally {
            context.clearMark();
        }
    }

    private final ICompilationError wrapException(Exception e, IParseContext context)
    {
    	System.out.println("=== ASTNode#wrapException(): "+e.getMessage());
    	Throwable cause = e;
    	while ( cause.getCause() != null ) {
    		cause = cause.getCause();
    	}
    	cause.printStackTrace();
    	
        final int errorOffset;        
        final ITextRegion errorRange;
        if ( e instanceof ParseException ) 
        {
            errorRange = ((ParseException) e).getTextRegion();
            errorOffset = errorRange.getStartingOffset();
        } else if ( e instanceof ICompilationError) {
            errorOffset = ((ICompilationError) e).getErrorOffset();
            errorRange = ((ICompilationError) e).getLocation();
        } else if ( e instanceof java.text.ParseException ) {
            errorOffset = ((java.text.ParseException) e).getErrorOffset();    
            errorRange = new TextRegion(errorOffset,0);
        } else if ( e instanceof EOFException) {
            errorOffset = ((EOFException) e).getErrorOffset();
            errorRange = new TextRegion(errorOffset,0);
        } else {
            errorOffset = context.currentParseIndex();
            errorRange = new TextRegion(errorOffset,0);
        } 

        final ICompilationError result;
        if ( e instanceof ICompilationError) 
        {
            result = (ICompilationError) e;
        } else 
        {
            String msg=e.getMessage();
            if ( StringUtils.isBlank( msg ) ) {
                msg = "< no error message >";
            }
            result = new GenericCompilationError( msg , context.getCompilationUnit() , e );
            result.setErrorOffset( errorOffset );
            result.setLocation( errorRange );
        }

        if ( result.getErrorOffset() != -1 ) 
        {
            if ( result.getLineNumber() == -1 || result.getColumnNumber() == -1 || result.getLineStartOffset() == -1 ) 
            {
                if ( result.getLineStartOffset() == -1 ) {
                    result.setLineStartOffset( context.getCurrentLineStartOffset() );
                }
                if (result.getLineNumber() == -1) {
                    result.setLineNumber( context.getCurrentLineNumber() );
                }
                if (result.getColumnNumber() == -1 ) 
                {
                    int column = result.getErrorOffset() - context.getCurrentLineStartOffset()+1; // columns start at 1
                    if ( column > 0 ) {
                        result.setColumnNumber( column );
                    } else {
                        LOG.warn("wrapException(): Error offset "+result.getErrorOffset()+" is not on current line "+context.getCurrentLineNumber()+", starting at "+
                                context.getCurrentLineStartOffset());
                    }
                }
            }
            if ( result.getLocation() == null && errorRange != null ) {
                result.setLocation( errorRange );
            }
        }

        return result;
    }

    /**
     * Add compilation error and switch parser to recovery mode if it isn't already.
     * 
     * See {@link #addCompilationErrorAndAdvanceParser(String, int, Exception, IParseContext)} for a description of how this method works.
     *      
     * @param e
     * @param context
     * @return {@link UnparsedContentNode} that was added as a child to <b>this</b> node
     */
    protected final ASTNode addCompilationErrorAndAdvanceParser(Exception e , IParseContext context) 
    {
        return addCompilationErrorAndAdvanceParser( wrapException( e , context ) , context );
    }  
    
    protected final ASTNode addCompilationErrorAndAdvanceParser(Exception e , TokenType[] recoveryTokens, IParseContext context) 
    {
        return addCompilationErrorAndAdvanceParser( wrapException( e , context ) , recoveryTokens , context );
    }      
    
    protected final ASTNode addCompilationErrorAndAdvanceParser(ICompilationError error, IParseContext context) 
    {   
        return addCompilationErrorAndAdvanceParser( error , DEFAULT_ERROR_RECOVERY_TOKEN , context );
    }

    protected final ASTNode addCompilationErrorAndAdvanceParser(ICompilationError error, TokenType[] recoveryTokens, IParseContext context) 
    {    
        context.reset();

        final List<IToken> tokens = context.advanceTo( recoveryTokens , false );
        final UnparsedContentNode result = new UnparsedContentNode( error.getMessage() , error.getErrorOffset() , tokens );

        if ( context.hasParserOption( ParserOption.DEBUG_MODE ) ) 
        {
            LOG.error("addCompilationErrorAndAdvanceParser(): [in_parse_error_recovery: "+context.isRecoveringFromParseError() +"] error="+error,error.getCause() );
        }

        addChild( result , context );

        if ( ! context.isRecoveringFromParseError() ) 
        {
            context.getCompilationUnit().addMarker( error );
            /*
             * This flag will be reset when an ASTNode that is not a UnparsedContentNode is added to the AST
             * (because this indicates that the parser (at least temporarily) was able to re-synchronize again. 
             */
            context.setRecoveringFromParseError( true ); 
        } 
        return result;
    }

    /**
     * Method to be implemented by subclasses, does the actual recursive-descent parsing.
     * 
     * <p>
     * Parse exceptions thrown by implementations will be attached to the 
     * current compilation unit as {@link ICompilationError} instances ; the
     * parser will then switch to error recovery mode and advance to the next token that 
     * has one of the token types returned by {@link #getParseRecoveryTokenTypes()}.
     * </p>
     * <p>
     * See {@link #parse(IParseContext)}} for a detailed description of the error recovery mechanism.
     * </p>
     * @param context
     * @return
     * @throws ParseException
     */
    protected abstract ASTNode parseInternal(IParseContext context) throws ParseException;

    @Override
    public String toString() 
    {
        final StringBuilder builder = new StringBuilder();
        for (Iterator<ASTNode> it = children.iterator(); it.hasNext();) {
            ASTNode child = it.next();
            builder.append( child.toString() );
            if ( it.hasNext() ) {
                builder.append(" , ");
            }
        }
        return getClass().getSimpleName()+" { "+builder.toString()+" }";
    }

    /**
     * Replace a direct child of this node with another one.
     * 
     * @param child
     * @param newNode
     */
    public final void replaceChild(ASTNode child , ASTNode newNode ) {

        if ( child == null ) {
            throw new IllegalArgumentException("child must not be NULL");
        }

        assertSupportsChildNodes();

        final int idx = children.indexOf( child );
        if ( idx == -1 ) {
            throw new IllegalArgumentException("Node "+child+" is not a child of "+this);
        }
        setChild( idx , newNode );
        recalculateTextRegion(true);
    }

    /**
     * Returns the path to the root node.
     * 
     * @return path to the root node, first element is the root node 
     * while the last element is THIS node.
     */
    public final ASTNode[] getPathToRoot() {

        List<ASTNode> path = new ArrayList<ASTNode>();
        ASTNode current = this;
        do {
            path.add( current );
            current = current.getParent();
        } while( current != null );
        Collections.reverse( path );
        return path.toArray( new ASTNode[ path.size() ] );
    }

    /**
     * Returns all AST nodes <b>below</b> this AST node
     * that overlap with a specific {@link ITextRegion}.
     * 
     * @param visible
     * @return
     */
    public final List<ASTNode> getNodesInRange(ITextRegion visible)
    {
        final List<ASTNode> result = new ArrayList<ASTNode>();
        for ( ASTNode child : children ) 
        {
            if ( child.getTextRegion().overlaps( visible ) ) {
                result.add( child );
            }
        }
        return result;
    } 	

    /**
     * Recursively discovers the AST node that starts closest 
     * to a given source code offset.
     * 
     * @param offset
     * @return source code node or <code>null</code> if neither
     * this node nor any of it's children cover the given offset
     */
    public final ASTNode getNodeInRange(int offset) 
    {
        final SearchResult result= internalGetNodeInRange( this , offset ,0 );
        return result == null ? null : result.node;
    }
    
    protected static final class SearchResult 
    {
        public final ASTNode node;
        public final int depth;
        
        private SearchResult(ASTNode node, int depth)
        {
            this.node = node;
            this.depth = depth;
        }
        
        public boolean isMoreSpecificThan(SearchResult other) 
        {
            return this.node.getTextRegion().getLength() <= other.node.getTextRegion().getLength() &&
                    this.depth > other.depth;
        }
        
        @Override
        public String toString()
        {
            return "SearchResult[ "+node.getTextRegion()+" , depth="+depth+", node="+node;
        }
    }
    
    private static final SearchResult internalGetNodeInRange(ASTNode node, int offset,int currentDepth) 
    {        
        final ITextRegion region = node.getTextRegion();
        if ( region == null || ! region.contains( offset ) ) 
        {
            return null;
        }
        
        SearchResult candidate =  new SearchResult(node,currentDepth);
        
        // special case: IncludeSourceFileNodes have at most one child and that's the AST
        //               of the included source file...we do not want to search this one !
        
        if ( ! ( node instanceof IncludeSourceFileNode) ) 
        {
            for ( ASTNode child : node.getChildren() ) 
            {
                SearchResult tmp = internalGetNodeInRange( child , offset , currentDepth +1 );
                if ( tmp != null && tmp.isMoreSpecificThan( candidate ) )
                {
                    candidate = tmp;
                }
            }
        }
        return candidate;
    }
    
    /**
     * Removes all child nodes.
     * 
     * <p>Note that this method does <b>not</b> alter the text regions associated with this node.</p>
     */
    public final void removeAllChildNodes() {
    	this.children.clear();
    }
    
    /**
     * Returns whether this node has a parent node.
     * 
     * @return
     * @see #getParent()
     */
    public boolean hasParent() {
    	return getParent() != null;
    }
    
    /**
     * Returns whether this node has no parent node.
     * 
     * @return
     * @see #getParent()
     */
    public boolean hasNoParent() {
    	return getParent() == null;
    }    
    
    /**
     * Starting from the current node and ascending the tree upwards,looks for
     * the closest {@link LabelNode} that defines a global label.
     * 
     * @return
     */
    protected final LabelNode getPreviousGlobalLabel() 
    {
    	final StatementNode stmt = getStatement();
    	if ( stmt != null && stmt.hasParent() ) 
    	{
    		ASTNode stmtParent = stmt.getParent();
    		int index = stmtParent.indexOf( stmt );
    		for ( int i = 0 ; i <= index ; i++ ) 
    		{
    			StatementNode tmp = (StatementNode) stmtParent.child(i);
    			LabelNode labelNode = tmp.getLabelNode();
    			if ( labelNode != null && labelNode.getLabel().isGlobalSymbol() ) {
    				return labelNode;
    			}
    		}
    	}
    	return null;
    }
    
    public final StatementNode getStatement() 
    {
    	if ( this instanceof StatementNode) {
    		return (StatementNode) this;
    	}
    	return getParent() != null ? getParent().getStatement() : null;
    }
}