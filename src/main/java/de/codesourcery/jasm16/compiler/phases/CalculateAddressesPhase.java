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
package de.codesourcery.jasm16.compiler.phases;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ByteAddress;
import de.codesourcery.jasm16.ISymbolAware;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.IncludeSourceFileNode;
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.DebugInfo;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnitResolver;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.IParentSymbolTable;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.SourceLocation;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * This compiler phase calculates the addresses of labels defined in the source code.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class CalculateAddressesPhase extends CompilerPhase {

    private static final Logger LOG = Logger.getLogger(CalculateAddressesPhase.class);

    public CalculateAddressesPhase() {
        super(ICompilerPhase.PHASE_RESOLVE_ADDRESSES);
    }

    @Override
    public boolean execute(final List<ICompilationUnit> units, 
            final DebugInfo debugInfo,
            final IParentSymbolTable symbolTable , 
            final IObjectCodeWriterFactory writerFactory , 
            final ICompilationListener listener, 
            final IResourceResolver resourceResolver, 
            final Set<CompilerOption> options, final ICompilationUnitResolver compUnitResolver)
    {

        /*
         * Since the size of an instruction depends on the
         * size of its operands and operands may include expressions
         * that refer to labels, the literal value of an expression
         * may change when the addresses of labels change their value
         * and vice versa.
         * 
         * This method recalculates label addresses as long as the size
         * of the generated object code is not stable (=does not change any longer
         * because all expressions evaluated to a value <=1f or >1f, read: their 
         * values can or cannot be inlined into the instruction itself)
         */
        final Map<String,Long> sizeByCompilationUnit = new HashMap<String,Long>(); 
        
        final DebugInfo debugInfoToUpdate = options.contains(CompilerOption.GENERATE_DEBUG_INFO) ? debugInfo : null;
        
        final ICompilationContextFactory factory = new ICompilationContextFactory() {

            @Override
            public ICompilationContext createContext(ICompilationUnit unit)
            {
                return createCompilationContext(units, symbolTable, writerFactory, resourceResolver, options,compUnitResolver,unit);               
            }
            
        };        
        boolean sizeIsStable;
        do 
        {
            sizeIsStable = true;
            if ( debugInfoToUpdate != null ) {
                debugInfoToUpdate.clear();
            }
            
            Address currentOffset = ByteAddress.ZERO;
            for ( final ICompilationUnit unit : units ) 
            {
                if ( unit.getAST() == null ) 
                {
                    continue;
                }
                final ICompilationContext context = factory.createContext( unit );
                
                final long newSizeInBytes = assignAddresses( context , unit , debugInfoToUpdate , currentOffset , factory );
//                System.out.println("Objectcode size for "+unit.getResource()+" is now: "+newSizeInBytes+" bytes");
                
                Long oldSizeInBytes = sizeByCompilationUnit.get( unit.getIdentifier() );
                
                if ( oldSizeInBytes == null ) {
                    sizeByCompilationUnit.put( unit.getIdentifier() , newSizeInBytes );
                    sizeIsStable = false;
                } else {
                    sizeByCompilationUnit.put( unit.getIdentifier() , newSizeInBytes );
                    if ( oldSizeInBytes.longValue() != newSizeInBytes ) {
                        sizeIsStable = false;
                    }
                }
                currentOffset = currentOffset.plus( Size.bytes( (int) newSizeInBytes ) , false );
            }
        } while ( ! sizeIsStable );
        return true;
    }
    
    protected interface ICompilationContextFactory {
        
        public ICompilationContext createContext(ICompilationUnit unit);
    }

    private int assignAddresses(final ICompilationContext compContext,
            final ICompilationUnit currentUnit,
            final DebugInfo debugInfo , 
            final Address startingOffset,
            final ICompilationContextFactory contextFactory) 
    {
        final MyVisitor first = new MyVisitor(compContext,currentUnit,debugInfo,startingOffset);
        
        final FancyVisitor visitor = new FancyVisitor() {
            
            protected MyVisitor current;
            
            private final Stack<MyVisitor> stack = new Stack<MyVisitor>() {
                @Override
                public MyVisitor push(MyVisitor item)
                {
                    final MyVisitor result = super.push(item);
                    current = item;
                    return result;
                }
                
                @Override
                public synchronized MyVisitor pop()
                {
                    MyVisitor result = super.pop();
                    current = peek();
                    return result;
                }
            };
            
            {
                stack.push(first);
            }
            
            @Override
            public void visit(ASTNode node)
            {
                current.visit( node );
            }
            
            @Override
            public void beforeDescent(ASTNode node)
            {
                if ( node instanceof IncludeSourceFileNode) 
                {
                    final IResource resource = ((IncludeSourceFileNode) node).getResource();
                    final ICompilationUnit newUnit;
                    try {
                        newUnit = compContext.getCompilationUnit( resource );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if ( newUnit == null ) {
                        throw new RuntimeException("Failed to locate compilation unit for resource "+resource );
                    }
                    
                    final ICompilationContext newContext = contextFactory.createContext( newUnit );
                    
                    stack.push( new MyVisitor(newContext, newUnit , debugInfo , Address.byteAddress( stack.peek().getCurrentByteOffset() ) ) );                    
                }
            }
            
            @Override
            public void afterDescent(ASTNode node)
            {
                if ( node instanceof IncludeSourceFileNode) {
                    MyVisitor previous = stack.pop();
                    stack.peek().incOffset( previous.getSizeInBytes() );
                }
            }
        };
        
        visitPostOrder( currentUnit.getAST() , visitor );
        
        return first.getSizeInBytes();
    }
    
    protected final class MyVisitor implements FancyVisitor  
    {
        private long currentByteOffset;
        private final ICompilationUnit currentUnit;
        private final DebugInfo debugInfo;
        private final Address startingOffset;
        private final ICompilationContext compContext;
        
        public MyVisitor(final ICompilationContext compContext,
                final ICompilationUnit currentUnit,
                final DebugInfo debugInfo , Address startingOffset) 
        {
            this.compContext = compContext;
            this.startingOffset = startingOffset;
            this.currentByteOffset = startingOffset.getByteAddressValue();
            this.debugInfo = debugInfo;
            this.currentUnit = currentUnit;
        }
        
        public int getSizeInBytes() {
            return (int) (currentByteOffset - startingOffset.getByteAddressValue());
        }
        
        public void incOffset(int bytes) {
            currentByteOffset+=bytes;
        }
        
        public long getCurrentByteOffset()
        {
            return currentByteOffset;
        }
        
        @Override
        public void visit(ASTNode n) 
        {
            try {
                internalVisit( n );
            } catch(RuntimeException e) {
                LOG.error("visit(): Failed to assign addresses to "+n+" at "+currentUnit+" ( "+n.getTextRegion()+") ");
                throw e;
            }
        }
        
        public void internalVisit(ASTNode n) 
        {
            // System.out.println("---> [ "+currentUnit.getResource()+"] visiting "+n.getClass().getSimpleName()+" ("+n+")");
            
            if ( n instanceof IncludeSourceFileNode ) 
            {
                // already handled by parent visitor
            }
            else if ( n instanceof LabelNode) 
            {
                final Label symbol = ((LabelNode) n).getLabel();
                if ( symbol != null )
                {
                    long byteAddress = currentByteOffset;
                    int wordAddress = (int) (byteAddress >> 1);
                    if ( ( wordAddress << 1 ) != byteAddress ) {
                        throw new RuntimeException("Internal error, address of label "+symbol+" is "+
                                byteAddress+" which is not on a 16-bit boundary?");
                    }
                    final WordAddress labelAddress = Address.wordAddress( wordAddress ) ;
//                    System.out.println("Assigning "+labelAddress+" to label "+symbol.getIdentifier() );
                    symbol.setAddress( labelAddress );
                }
            } 
            else if ( n instanceof ObjectCodeOutputNode) 
            {
                final ObjectCodeOutputNode outputNode = (ObjectCodeOutputNode) n;

                if ( n instanceof ISymbolAware ) {
                    outputNode.symbolsResolved( compContext );
                }

                if ( debugInfo != null && compContext.hasCompilerOption( CompilerOption.GENERATE_DEBUG_INFO ) ) 
                {
                    final long byteAddress = currentByteOffset;
                    final int wordAddress = (int) (byteAddress >> 1);
                    if ( ( wordAddress << 1 ) != byteAddress ) {
                        throw new RuntimeException("Internal error, address of instruction "+outputNode+" is "+
                                byteAddress+" which is not on a 16-bit boundary?");
                    }
                    final WordAddress address = Address.wordAddress( wordAddress ) ;
                    SourceLocation sourceLocation = null;
                    try {
                        sourceLocation = currentUnit.getSourceLocation( outputNode.getTextRegion() );
                    } 
                    catch(NoSuchElementException e) {
                        final String msg = "Failed to find source location for node "+outputNode+" with "+outputNode.getTextRegion();
                        final NoSuchElementException ex = new NoSuchElementException( msg );
                        throw ex;
                    }
                    debugInfo.addSourceLocation( address, sourceLocation ); 
                }
                
                final int sizeInBytes = outputNode.getSizeInBytes( currentByteOffset );
                if ( sizeInBytes != ObjectCodeOutputNode.UNKNOWN_SIZE ) 
                {
                    currentByteOffset += sizeInBytes;
                }
            } else if ( n instanceof ISymbolAware ) {
                ((ISymbolAware) n).symbolsResolved( compContext );
            }
        }

        @Override
        public void beforeDescent(ASTNode node)
        {
        }

        @Override
        public void afterDescent(ASTNode node)
        {
        }
    }    

    protected interface FancyVisitor {
        
        public void beforeDescent(ASTNode node);
        
        public void visit(ASTNode node);
        
        public void afterDescent(ASTNode node);        
    }
    
    private static void visitPostOrder(ASTNode node,FancyVisitor visitor) 
    {
        visitPostOrder( node ,0 , visitor );
    }      
    
    private static void visitPostOrder(ASTNode node, int depth , FancyVisitor visitor) 
    {
        int currentDepth = depth;
        for ( ASTNode child : node.getChildren() ) 
        {
            visitor.beforeDescent( child );
            visitPostOrder( child , currentDepth+1, visitor );
            visitor.afterDescent( child );
        }
        
        visitor.visit( node );
    }      
    
    @Override
    protected void run(ICompilationUnit unit, ICompilationContext context)
            throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }		
}
