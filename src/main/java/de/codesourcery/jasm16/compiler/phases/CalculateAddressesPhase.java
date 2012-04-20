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
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
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
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * This compiler phase calculates the addresses of labels defined in the source code.
 * 
 * <p>This phase needs to be executed twice (before and after expression folding)
 * because the actual size of some instructions depends on the size of their
 * operands and thus labels may addresses may "shift" after the constant folding took
 * place.</p>
 * @author tobias.gierke@code-sourcery.de
 */
public class CalculateAddressesPhase extends CompilerPhase {

    private static final Logger LOG = Logger.getLogger(CalculateAddressesPhase.class);

    private final boolean failOnUnknownInstructionSize;

    public CalculateAddressesPhase(String name,boolean failOnUnknownInstructionSize) {
        super(name);
        this.failOnUnknownInstructionSize = failOnUnknownInstructionSize;
    }

    @Override
    public boolean execute(List<ICompilationUnit> units, 
    		final ISymbolTable symbolTable , 
    		IObjectCodeWriterFactory writerFactory , 
    		ICompilationListener listener, 
    		IResourceResolver resourceResolver, Set<CompilerOption> options)
    {
        for ( final ICompilationUnit unit : units ) 
        {
            final long[] currentSize = { unit.getObjectCodeStartOffset().getValue() };

            if ( unit.getAST() == null ) 
            {
                continue;
            }
            
            final IASTNodeVisitor<ASTNode> visitor = new IASTNodeVisitor<ASTNode>() {

                @Override
                public void visit(ASTNode n, IIterationContext context)
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
                            symbol.setAddress( Address.valueOf( wordAddress ) );
                        }
                    } 
                    else if ( n instanceof ObjectCodeOutputNode) 
                    {
                        final ObjectCodeOutputNode outputNode = (ObjectCodeOutputNode) n;
                        outputNode.symbolsResolved( symbolTable );

                        final int sizeInBytes = outputNode.getSizeInBytes();
                        if ( sizeInBytes != ObjectCodeOutputNode.UNKNOWN_SIZE ) 
                        {
                            currentSize[0] += sizeInBytes;
                        } else if ( failOnUnknownInstructionSize ) {
                            throw new RuntimeException("Internal error, node "+outputNode+" did not calculate it's size");
                        }
                    }
                    context.continueTraversal();
                }
            };   

            try {
                ASTUtils.visitPostOrder( unit.getAST() , visitor );
            } catch(Exception e) {
                unit.addMarker( new GenericCompilationError( "Internal compiler error during phase '"+getName()+"' : "+e.getMessage() ,unit,e ) );
                LOG.error("execute(): Caught while handling "+unit,e);
            }
        }
        return true;
    }

	@Override
	protected void run(ICompilationUnit unit, ICompilationContext context)
			throws IOException {
		throw new UnsupportedOperationException("Not implemented");
	}		
}
