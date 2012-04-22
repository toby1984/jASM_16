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
import java.util.Iterator;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ConstantValueNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompilerPhase;

/**
 * (not enabled by default) Compiler phase that tries to replace expressions with their literal values.
 * 
 * <p>
 * This phase is currently not enabled by default and needs to be inserted manually using
 * {@link ICompiler#insertCompilerPhaseAfter(ICompilerPhase, String)} , <b>must</b>
 * be inserted AFTER {@link ICompilerPhase#PHASE_RESOLVE_ADDRESSES}.</p>
 * 
 * </p>
 * @author tobias.gierke@code-sourcery.de
 */
public class FoldExpressionsPhase extends CompilerPhase {
	
    public FoldExpressionsPhase() {
		super(ICompilerPhase.PHASE_FOLD_EXPRESSIONS);
	}

	@Override
    protected void run(final ICompilationUnit unit, ICompilationContext context) throws IOException
    {
        foldExpressions( context );
    }
	
    private void foldExpressions(final ICompilationContext context) 
    {
        boolean repeat = true;
 outer:		
            while( repeat ) 
            {
                repeat = false;
                final AST ast = context.getCurrentCompilationUnit().getAST();
                if ( ast == null ) {
                    break;
                }
                final Iterator<ASTNode> it = ASTUtils.createInOrderIterator( ast );
                while ( it.hasNext() ) 
                {
                    final ASTNode node = it.next();
                    if ( node instanceof TermNode) 
                    {
                        final TermNode inputNode = (TermNode) node;
                        if ( inputNode instanceof ConstantValueNode || inputNode instanceof RegisterReferenceNode) {
                            continue;
                        }
                        final TermNode reducedNode = inputNode.reduce( context );
                        if ( ! ASTUtils.isEquals( inputNode , reducedNode ) && node.getParent() != null ) 
                        {
                            node.getParent().replaceChild( node , reducedNode );
                            repeat = true;
                            continue outer;						
                        }
                    }
                }
            } 
    }
    
    protected boolean isAbortOnErrors() {
        return true;
    }      
    
}