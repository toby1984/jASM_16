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
package de.codesourcery.jasm16.parser;

import java.io.IOException;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.StartMacroNode;
import de.codesourcery.jasm16.ast.UnparsedContentNode;
import de.codesourcery.jasm16.compiler.*;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * Assembler source code parser.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IParser
{
    public enum ParserOption {
        DEBUG_MODE,
        RELAXED_PARSING,
		NO_SOURCE_INCLUDE_PROCESSING, 
		LOCAL_LABELS_SUPPORTED;
    }
    
    /**
     * Enable/disable a parser flag.
     * 
     * @param option
     * @param onOff
     */
    public void setParserOption(ParserOption option,boolean onOff);
    
    /**
     * Check whether a specific parser option is enabled.
     * 
     * @param option
     * @return
     */
    public boolean hasParserOption(ParserOption option);
    
	/**
	 * Parse a compilation unit.
	 * 
	 * <p>Note that this method will not fail on parse errors , instead
	 * the compilation unit will have {@link ICompilationError} instances
	 * added to it that hold more information about the cause of the error.
	 * </p>
	 * <p>Source code that failed to parse will still become part of the AST
	 * as a {@link UnparsedContentNode}. This may be used by a editor that embeds 
	 * this compiler to highlight the erronous source location.</p>
	 * @param context
	 * @param expandingMacro the macro that is currently being expanded or <code>null</code>
	 * @return
	 * @see AST#hasErrors()
	 * @see ASTNode#hasErrors()
	 * @see ICompilationUnit#getErrors()
	 * @throws IOException if an I/O error occured during parsing the input.
	 */
    public AST parse(ICompilationContext context) throws IOException;
    
	/**
	 * Parse a compilation unit.
	 * 
	 * <p>Note that this method will not fail on parse errors , instead
	 * the compilation unit will have {@link ICompilationError} instances
	 * added to it that hold more information about the cause of the error.
	 * </p>
	 * <p>Source code that failed to parse will still become part of the AST
	 * as a {@link UnparsedContentNode}. This may be used by a editor that embeds 
	 * this compiler to highlight the erronous source location.</p>
	 * @param context
	 * @param expandingMacro the macro that is currently being expanded or <code>null</code>
	 * @return
	 * @see AST#hasErrors()
	 * @see ASTNode#hasErrors()
	 * @see ICompilationUnit#getErrors()
	 * @throws IOException if an I/O error occured during parsing the input.
	 */    
    public AST parse(ICompilationUnit unit , ISymbolTable symbolTable , String source,IResourceResolver resolver,StartMacroNode expandingMacro);
}
