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

import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.utils.ITextRegion;

/**
 * Abstract base-class for AST nodes that may be used in 
 * as part of expressions.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see ExpressionNode
 */
public abstract class TermNode  extends ASTNode 
{
    public TermNode() {
    }
    
    public TermNode(ITextRegion range) {
        super( range );
    }
    
	/**
	 * Tries to reduce this term by folding constant expressions
	 * into the resulting value.
	 * 
	 * @param context
	 * @return either the NEW , reduced expression or THIS node unaltered if
	 * the term could not be reduced
	 */
	public abstract TermNode reduce(ICompilationContext context);
	
	/**
	 * Checks whether this term is actually a {@link NumberNode} instance.
	 * 
	 * @return <code>true</code> if this AST node can be safely cast to
	 * a {@link NumberNode}. 
	 */
	public boolean isNumberLiteral() {
		return false;
	}
}
