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
import java.util.Set;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ISymbolAware;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.IASTNodeVisitor;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.GenericCompilationError;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
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

    
    public boolean execute(List<ICompilationUnit> units, 
            final ISymbolTable symbolTable , 
            IObjectCodeWriterFactory writerFactory , 
            ICompilationListener listener, 
            IResourceResolver resourceResolver, 
            Set<CompilerOption> options)
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
        boolean sizeIsStable;
        do {
            sizeIsStable = true;
            for (int i = 0; i < units.size(); i++)
            {
                final ICompilationUnit unit = units.get(i);
                
                if ( unit.getAST() == null ) 
                {
                    continue;
                }
                final ICompilationContext context = createCompilationContext(units,
                        symbolTable, writerFactory, resourceResolver, options,
                        unit);
                
                Address startingOffset = unit.getObjectCodeStartOffset();
                final long newSizeInBytes = assignAddresses( context , startingOffset);
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
            }
        } while ( ! sizeIsStable );
        return true;
    }

    private long assignAddresses(final ICompilationContext compContext,Address startingOffset) 
    {
        final long[] currentSize = { startingOffset.getValue() };

        final ICompilationUnit unit = compContext.getCurrentCompilationUnit();

        final IASTNodeVisitor<ASTNode> visitor = new IASTNodeVisitor<ASTNode>() {

            
            public void visit(ASTNode n,IIterationContext ctx) 
            {
                try {
                    internalVisit( n , ctx );
                } catch(RuntimeException e) {
                    LOG.error("visit(): Failed to assign addresses to "+n+" at "+unit+" ( "+n.getTextRegion()+") ");
                    throw e;
                }
            }
            public void internalVisit(ASTNode n,IIterationContext ctx) 
            {
                if ( n instanceof LabelNode) 
                {
                    final Label symbol = ((LabelNode) n).getLabel();
                    if ( symbol != null )
                    {
                        long byteAddress = currentSize[0];
                        int wordAddress = (int) (byteAddress >> 1);
                        if ( ( wordAddress << 1 ) != byteAddress ) {
                            throw new RuntimeException("Internal error, address of label "+symbol+" is "+
                                    byteAddress+" which is not on a 16-bit boundary?");
                        }
                        symbol.setAddress( Address.wordAddress( wordAddress ) );
                    }
                } 
                else if ( n instanceof ObjectCodeOutputNode) 
                {
                    final ObjectCodeOutputNode outputNode = (ObjectCodeOutputNode) n;

                    if ( n instanceof ISymbolAware ) {
                        outputNode.symbolsResolved( compContext );
                    }

                    final int sizeInBytes = outputNode.getSizeInBytes(currentSize[0]);
                    if ( sizeInBytes != ObjectCodeOutputNode.UNKNOWN_SIZE ) 
                    {
                        currentSize[0] += sizeInBytes;
                    }
                } else if ( n instanceof ISymbolAware ) {
                    ((ISymbolAware) n).symbolsResolved( compContext );
                }
            }
        };   

        try {
            ASTUtils.visitPostOrder( unit.getAST() , visitor );
        } 
        catch(Exception e) {
            unit.addMarker( 
                    new GenericCompilationError( "Internal compiler error during phase '"+getName()+
                            "' : "+e.getMessage() ,unit,e ) );
            LOG.error("execute(): Caught while handling "+unit,e);
        }
        return currentSize[0];
    }

    
    protected void run(ICompilationUnit unit, ICompilationContext context)
            throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }		
}
