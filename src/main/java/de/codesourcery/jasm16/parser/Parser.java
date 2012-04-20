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
import java.util.HashSet;
import java.util.Set;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.SymbolTable;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.Lexer;
import de.codesourcery.jasm16.lexer.ILexer.LexerOption;
import de.codesourcery.jasm16.scanner.Scanner;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Default {@link IParser} implementation.
 * 
 * <p>Here's not much to look at since the actual parsing is 
 * performed by the AST nodes (recursive-descent parsing).</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Parser implements IParser
{
    private final Set<ParserOption> options = new HashSet<ParserOption>(); 
    
    public Parser() {
    }

    @Override
    public AST parse(ICompilationContext context) throws IOException 
    {
        final String source = Misc.readSource( context.getCurrentCompilationUnit() );
        return parse(context.getCurrentCompilationUnit(),context.getSymbolTable(),source,context);
    }

    // unit-testing only
    protected AST parse(final String source) {
        final ICompilationUnit unit = CompilationUnit.createInstance( "string input" , source );
        final IResourceResolver resolver = new IResourceResolver() {

            @Override
            public IResource resolve(String identifier) throws ResourceNotFoundException
            {
                throw new UnsupportedOperationException("Not implemented"); 
            }

            @Override
            public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException
            {
                throw new UnsupportedOperationException("Not implemented"); 
            }
        };
        return parse(  unit , new SymbolTable() , source , resolver );
    }

    protected AST parse(ICompilationUnit unit , ISymbolTable symbolTable , String source,IResourceResolver resolver) 
    {
        final Scanner scanner = new Scanner( source );
        final ILexer lexer = new Lexer( scanner );
        if ( hasParserOption( ParserOption.RELAXED_PARSING ) ) {
            lexer.setLexerOption( LexerOption.CASE_INSENSITIVE_OPCODES , true );
        }        
        return (AST) new AST().parse( new ParseContext(  unit , symbolTable, lexer , resolver , this.options ) );
    }

    @Override
    public void setParserOption(ParserOption option, boolean onOff)
    {
        if ( option == null ) {
            throw new IllegalArgumentException("option must not be NULL.");
        }
        if ( onOff ) {
            options.add( option );
        } else {
            options.remove( option );
        }
    }

    @Override
    public boolean hasParserOption(ParserOption option)
    {
        if (option == null) {
            throw new IllegalArgumentException("option must not be NULL.");
        }
        return options.contains( option );
    }

}
