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

import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ASTVisitor;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.SymbolReferenceNode;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.Equation;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.parser.Identifier;

/**
 * This compiler phase checks that all symbol references actually
 * refer to an existing symbol.
 * 
 * <p>Since forward references are allowed in source code, this phase cannot be
 * part of the {@link ParseSourcePhase}.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ASTValidationPhase1 extends CompilerPhase {

    public ASTValidationPhase1() {
		super(ICompilerPhase.PHASE_VALIDATE_AST1);
	}

    @Override
    protected void run(final ICompilationUnit unit , final ICompilationContext compContext) throws IOException
    {
        if ( unit.getAST() == null ) {
            return;
        }
        
        final ASTVisitor visitor = new ASTVisitor() 
        {
        	@Override
        	public void visit(SymbolReferenceNode node, IIterationContext context) 
        	{
        		final ISymbol resolved = node.resolve( compContext.getSymbolTable() );
        		final Identifier id = node.getIdentifier();        		
                if ( resolved == null ) {
                    unit.addMarker( new CompilationError("Unknown identifier '"+id+"'", unit, node ) );
                } 
               	Equation.checkCyclicDependencies(id,compContext.getSymbolTable());
        	}
        };
        
        ASTUtils.visitInOrder( unit.getAST() , visitor );
    }
}