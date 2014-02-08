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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * Provides various utility methods related to ASTs and AST nodes.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ASTUtils {

    public static void visitInOrder(ASTNode node,ISimpleASTNodeVisitor<ASTNode> visitor) 
    {
        final Iterator<ASTNode> iterator = createInOrderIterator( node );
        while( iterator.hasNext() && visitor.visit( iterator.next() ) );
    }

    protected enum VisitorResult { STOP, DONT_GO_DEEPER, CONTINUE_TRAVERSAL; }
    
    protected static final class IterationContext implements IIterationContext 
    {
        private VisitorResult result;

        public void reset() { result = null; }
        
        private void setResult(VisitorResult result) 
        {
            if ( this.result != null ) {
                throw new IllegalStateException("Result already set?");
            }
            this.result = result;
        }
        
        public boolean isDontGoDeeper() { return result == VisitorResult.DONT_GO_DEEPER; }
        public boolean isContinueTraveral() { return result == VisitorResult.CONTINUE_TRAVERSAL; }        
        public boolean isStop() { return result == VisitorResult.STOP; }
        
        public VisitorResult getResult() { return result; }
        
        @Override
        public void stop() { setResult( VisitorResult.STOP ); }

        @Override
        public void dontGoDeeper() { setResult( VisitorResult.DONT_GO_DEEPER); }

        @Override
        public void continueTraversal() { setResult( VisitorResult.CONTINUE_TRAVERSAL ); }
    }
    
    public static void visitInOrder(ASTNode node,IASTNodeVisitor<ASTNode> visitor) 
    {
        final IterationContext context = new IterationContext();
        visitInOrder( node , context , 0 , visitor );
    }    
    
    public static void visitInOrder(ASTNode node,IterationContext context , int depth , IASTNodeVisitor<ASTNode> visitor) 
    {
        context.reset();
        visitor.visit( node , context );
        if ( ! context.isStop() && ! context.isDontGoDeeper() ) 
        {
            for ( ASTNode child : node.getChildren() ) {
                visitInOrder( child , context , depth + 1 , visitor );
            }
        }
    }
    
    public static void visitPostOrder(ASTNode node,IASTNodeVisitor<ASTNode> visitor) 
    {
        final IterationContext context = new IterationContext();
        visitPostOrder( node , context , 0 , visitor );
    }      
    
    public static void visitPostOrder(ASTNode node,IterationContext context , int depth , IASTNodeVisitor<ASTNode> visitor) 
    {
        int currentDepth = depth;
        for ( ASTNode child : node.getChildren() ) 
        {
            visitPostOrder( child , context , currentDepth+1, visitor );
        }
        
        if ( ! context.isStop() ) 
        {
            context.reset();
            visitor.visit( node , context);
        }
    }    

    /*             3
     *            / \
     * (child 1) 2   1 (child 0)
     */	
    public static Iterator<ASTNode> createDepthFirst(ASTNode node) 
    {
        final Stack<ASTNode> stack = new Stack<ASTNode>();
        if ( node != null ) {
            stack.push( node );
        }
        return new Iterator<ASTNode> () {

            @Override
            public boolean hasNext()
            {
                return ! stack.isEmpty();
            }

            @Override
            public ASTNode next()
            {
                ASTNode n = stack.peek();
                for ( ASTNode child : n.getChildren() ) {
                    stack.push( child );
                }
                return stack.pop();
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }

    /*             1
     *            / \
     * (child 1) 5   2 (child 0)
     *               /\
     *              4  3
     */
    public static Iterator<ASTNode> createInOrderIterator(ASTNode node) 
    {
        if (node == null) {
            throw new IllegalArgumentException("node must not be NULL");
        }
        final Stack<ASTNode> stack = new Stack<ASTNode>();
        stack.push( node );
        return new Iterator<ASTNode>() 
                {

            @Override
            public boolean hasNext() {
                return ! stack.isEmpty();
            }

            /*    A (1)
             *    |\
             *(5) E B (2)
             *    |\ 
             *(4) D C (3)
             */
            @Override
            public ASTNode next() 
            {
                if ( stack.isEmpty() ) {
                    throw new NoSuchElementException();
                }
                ASTNode result = stack.pop();

                final List<ASTNode> children = result.getChildren();
                Collections.reverse( children );

                for ( ASTNode child : children ) {
                    stack.push( child );
                }
                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
                };
    }

    public static <T extends ASTNode> List<T> getNodesByType(final ASTNode root,final Class<T> clazz,final boolean excludeInputNode) {

        final List<T> result = new ArrayList<T>();
        final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {

            @SuppressWarnings("unchecked")
			@Override
            public boolean visit(ASTNode node) 
            {
                if ( clazz.isAssignableFrom( node.getClass() ) ) 
                {
                    if ( ! excludeInputNode || node != root ) {
                        result.add( (T) node );
                    }
                }
                return true;
            }
        };
        // do NOT change the order here, AST#parseInternal() checks
        // .org directives for ascending values and relies
        // on the fact that this method returns the nodes in-order !
        visitInOrder( root , visitor );
        return result;
    }

    public static <T extends ASTNode> void visitNodesByType(ASTNode root,final ISimpleASTNodeVisitor<T> visitor, final Class<T> clazz) {

        final ISimpleASTNodeVisitor<ASTNode> visitor2 = new ISimpleASTNodeVisitor<ASTNode>() {

            @SuppressWarnings("unchecked")
			@Override
            public boolean visit(ASTNode node) 
            {
                if ( clazz.isAssignableFrom( node.getClass() ) ) {
                    return visitor.visit( (T) node );
                }
                return true;
            }
        };
        visitInOrder( root , visitor2 );
    }

    public static <T extends ASTNode> boolean containsNodeWithType(ASTNode node, Class<T> clazz) 
    {
        final boolean[] result = { false };
        final ISimpleASTNodeVisitor<T> visitor = new ISimpleASTNodeVisitor<T>() {

            @Override
            public boolean visit(T node) 
            {
                result[0] = true;
                return false;
            }
        };
        visitNodesByType( node , visitor , clazz );
        return result[0];
    }	
    
    public static final void printAST(ASTNode n,String source) {
        printAST( n , 1 , source );
    }
    
    public static final void printAST(ASTNode n,int depth,String source) 
    {
        final String indent = StringUtils.repeat(" " , depth*2 );
        System.out.println( indent + n.toString()+" >"+n.getTextRegion().apply( source )+"<" );

        for ( ASTNode child  : n.getChildren() ) {
            printAST(child,depth+1 , source );
        }
    }      
    
    public static final void printAST(ASTNode n) {
        printAST( n , 1 );
    }
    
    public static final void printAST(ASTNode n,int depth) 
    {
        final String indent = StringUtils.repeat(" " , depth*2 );
        System.out.println( indent + n.toString() );
        for ( ASTNode child  : n.getChildren() ) {
            printAST(child,depth+1);
        }
    }       

    public static String printAST(ICompilationUnit unit , AST ast) throws IOException {

    	if ( ast == null ) {
    		return "<NULL AST>";
    	}
    	final StringBuilder result = new StringBuilder();
    	printAST( unit , ast , 0 , result );
    	return result.toString();
    }
    
    private static void printAST(ICompilationUnit unit, ASTNode currentNode, int currentDepth,StringBuilder result) throws IOException 
    {
    	final String indent = StringUtils.repeat(" " , currentDepth *2);
    	
    	final String contents = unit.getSource( currentNode.getTextRegion() );
    	final String src = ">"+contents+"<";
  		result.append( indent + " "+currentNode.getClass().getSimpleName()+" ("+src+")").append("\n");    		
    	for ( ASTNode child : currentNode.getChildren() ) {
    		printAST( unit , child , currentDepth+1,result);
    	}
    }
    
    public static boolean isEquals(ASTNode n1,ASTNode n2) 
    {
        final Iterator<ASTNode> it1 = ASTUtils.createInOrderIterator( n1 );
        final Iterator<ASTNode> it2 = ASTUtils.createInOrderIterator( n2 );
        while ( it1.hasNext() && it2.hasNext() ) 
        {
            if ( ! it1.next().equals( it2.next() ) ) {
                return false;
            }
        }
        if ( it1.hasNext() != it2.hasNext() ) {
            return false;
        }
        return true;
    }
    
    public static int getRegisterReferenceCount(ASTNode node) 
    {
        return ASTUtils.getNodesByType( node , RegisterReferenceNode.class , false ).size();
    }    
    
    /**
     * Returns the earliest memory location from a given subtree.
     * 
     * @param node
     * @return earliest memory location or <code>null</code> if the subtree either
     * did not contain any {@link ObjectCodeOutputNode}s or none of the nodes had
     * an address assigned to yet.
     */
    public static Address getEarliestMemoryLocation(ASTNode node) {
        return findMemoryLocation( node , true );
    }
    
    public static Address getLatestMemoryLocation(ASTNode node) {
        return findMemoryLocation( node , false );
    }  
    
    private static Address findMemoryLocation(ASTNode node,final boolean findEarliest) 
    {
        final Address[] result = {null};
        final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {
            
            @Override
            public boolean visit(ASTNode node) 
            {
                if ( node instanceof ObjectCodeOutputNode) 
                {
                    final Address adr = ((ObjectCodeOutputNode) node).getAddress();
                    if ( adr != null ) 
                    {
                        if ( result[0] == null ) 
                        {
                          result[0] = adr;
                          if ( result[0].equals( WordAddress.ZERO ) ) {
                        	  System.out.println("Node with ZERO address: "+node);
                          }
                        } 
                        else if ( (findEarliest && adr.isLessThan( result[0] ) ) || (!findEarliest && adr.isGreaterThan( result[0] ) ) ) 
                        {
                            result[0] = adr;
                            if ( result[0].equals( WordAddress.ZERO ) ) {
                          	  System.out.println("Node with ZERO address: "+node);
                            }                            
                        }
                    }
                }
                return true;
            }
        };
        visitInOrder(node , visitor );
        return result[0];        
    }
    
	public static void debugPrintTextRegions(ASTNode node,final String source) 
	{
		debugPrintTextRegions(node,source,null);
	}
	
	public static void debugPrintTextRegions(ASTNode node,final String source,final ICompilationUnit unit) 
	{
		ASTUtils.visitInOrder( node , new ISimpleASTNodeVisitor<ASTNode>() 
		{
			@Override
			public boolean visit(ASTNode node) 
			{
				ITextRegion region = node.getTextRegion();
				if ( region != null ) {
					int len = region.getEndOffset()-region.getStartingOffset()-1;
					if ( len < 0 ) {
						len = 0;
					}
					String regionString = StringUtils.repeat(" ", region.getStartingOffset() )+"^"+StringUtils.repeat(" " , len )+"^";
					String bodyText=null;
					try {
						region.apply( source ).replace("\n", "|").replace("\r","|");
						bodyText = source.replace("\n", "|").replace("\r","|");
						bodyText += "\n"+regionString;
					} catch(Exception e) {
						bodyText = "<Invalid region>";					
					}
					String loc = "";
					if ( unit != null ) {
						SourceLocation sourceLocation = unit.getSourceLocation( region );
						if ( sourceLocation != null ) {
							loc = "[ "+sourceLocation.toString()+" ]";
						}
					}
					System.out.print("\n-----\nAST Node "+node.getClass().getSimpleName()+" convers range "+region+" "+loc+"\n---\n"+bodyText);
				} else {
					System.err.print("\n-----\nAST Node "+node.getClass().getSimpleName()+" has no text region set");
				}
				return true;
			}
		});		
	}    
}
