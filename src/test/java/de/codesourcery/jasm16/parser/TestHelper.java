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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;
import de.codesourcery.jasm16.OpCode;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ASTVisitor;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.ast.UnparsedContentNode;
import de.codesourcery.jasm16.compiler.CompilationContext;
import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.Compiler;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnitResolver;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.SymbolTable;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.compiler.io.NullObjectCodeWriterFactory;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.Lexer;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.scanner.Scanner;
import de.codesourcery.jasm16.utils.DebugCompilationListener;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Misc;

public abstract class TestHelper extends TestCase implements ICompilationUnitResolver
{
    protected ISymbolTable symbolTable;
 
    protected static final Set<CompilerOption> OPTIONS = Collections.singleton( CompilerOption.DEBUG_MODE );
    
    protected static final IResourceResolver RESOURCE_RESOLVER = new IResourceResolver() {
        
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
    
    protected static final IObjectCodeWriterFactory NOP_WRITER = new NullObjectCodeWriterFactory();
    
    protected void assertTextRegion(ITextRegion expected,ITextRegion actual,String source) {

        if ( ! expected.isSame( actual ) ) {
            System.out.println("Expected: >"+expected.apply( source )+"< ("+expected+")");
            System.out.println("Got: >"+actual.apply( source )+"< ("+actual+")");
            fail( "expected: "+expected+" , got "+actual);
        }
    }
    
    protected IParseContext createParseContext(String source) 
    {
        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        return new ParseContext( unit , symbolTable , new Lexer(new Scanner( source ) ) , RESOURCE_RESOLVER ,this, Collections.singleton( ParserOption.DEBUG_MODE ) );
    }
    
    protected IParseContext createParseContext(ICompilationUnit unit) throws IOException 
    {
    	final String source = Misc.readSource( unit.getResource() );
        return new ParseContext( unit , symbolTable , new Lexer(new Scanner( source ) ) , RESOURCE_RESOLVER ,this, Collections.singleton( ParserOption.DEBUG_MODE ) );
    }    
    
    @Override
    protected void setUp() throws Exception
    {
        symbolTable = new SymbolTable();
    }
    
    protected final void assertToken(ILexer lexer , TokenType type , String contents ) {
        assertToken( lexer , type , contents , -1 );
    }
    
    protected final ICompilationUnit assertCompiles(String source) throws Exception {

		final ICompilationUnit unit = compile( source );
		Misc.printCompilationErrors( unit , source , true );
		assertFalse( unit.hasErrors() );
		assertNotNull( unit.getAST() );
		assertFalse( unit.getAST().hasErrors() );
        assertSourceCode( source , unit.getAST() );
        return unit;
    }
    
    protected final ICompilationUnit compile(String source) throws Exception {
    	return compile(source, new DebugCompilationListener(true) );
    }
    
    protected final ICompilationUnit compile(String source,ICompilationListener listener) throws Exception {

		final Compiler compiler = new Compiler();
		
		compiler.setCompilerOption( CompilerOption.DEBUG_MODE  , true );
		
		compiler.setObjectCodeWriterFactory( NOP_WRITER );
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );

		compiler.insertCompilerPhaseAfter( new CompilerPhase("pry-on-symbols") {

			@Override
			protected void run(ICompilationUnit unit, ICompilationContext context) throws IOException 
			{
				symbolTable = context.getSymbolTable();
			}
		} , ICompilerPhase.PHASE_GENERATE_CODE );
		
		compiler.compile( Collections.singletonList( unit ) , listener );
		
		Misc.printCompilationErrors( unit , source , true );
        assertSourceCode( source , unit.getAST() );  
		return unit;
    }    
    
    protected final void assertDoesNotCompile(String source) {

        final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
        final Compiler c = new Compiler();
        c.setObjectCodeWriterFactory( NOP_WRITER );
        c.compile(Collections.singletonList( unit ) , new CompilationListener() );
        final ASTNode result = unit.getAST();
        boolean hasErrors = ! unit.getErrors().isEmpty();
        assertTrue( "Compilation should have failed", hasErrors );
        assertSourceCode( source , result );    	
    }    
    
    protected final void assertToken(ILexer lexer , TokenType type , String contents , int parseOffset) 
    {
        assertFalse( lexer.eof() );
        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( type , tok.getType() );
        assertEquals( contents  , tok.getContents() );
        if ( parseOffset != -1 ) {
            assertEquals( parseOffset , tok.getStartingOffset() );
        }
    }
    
    protected final void assertToken(ILexer lexer , OpCode opCode , String contents) 
    {
        assertToken(lexer,opCode,contents,-1);
    }
    
    protected final ICompilationContext createCompilationContext(ICompilationUnit unit) throws IOException 
    {
        return new CompilationContext( unit , symbolTable , new NullObjectCodeWriterFactory() , 
        		new IResourceResolver() {
            
            @Override
            public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException
            {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public IResource resolve(String identifier) throws ResourceNotFoundException
            {
                throw new UnsupportedOperationException();
            }
        } , this , OPTIONS );
    }
    
    protected final void assertToken(ILexer lexer , OpCode opCode , String contents , int parseOffset) 
    {
        assertFalse( lexer.eof() );
        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( TokenType.INSTRUCTION , tok.getType() );
        
        final OpCode actual = OpCode.fromIdentifier( contents );
        assertEquals( opCode , actual );
        assertEquals( contents  , tok.getContents() );
        if ( parseOffset != -1 ) {
            assertEquals( parseOffset , tok.getStartingOffset() );
        }
    }
    
    protected final String getErrors(final String source,ASTNode node) {
        
        final StringBuilder result = new StringBuilder();
        final ASTVisitor visitor = new ASTVisitor() {
            @Override
            public void visit(UnparsedContentNode node, IIterationContext context)
            {
                final String msg = Misc.toPrettyString( node.getError() , node.getErrorOffset() , source );
                System.out.println( msg );
                result.append("\n").append( msg ).append("\n");
            }
        };
        
        ASTUtils.visitInOrder( node , visitor );
        return result.toString();
    }
    
    protected final void assertSourceCode(String expected,String source , ASTNode node) 
    {
        assertEquals( expected , toSourceCode( node , source ) );
    }
    
    protected final void assertSourceCode(String source,ASTNode node) 
    {
        assertEquals( source , toSourceCode( node , source ) );
    }
    
    protected final String toSourceCode(ASTNode node,String source) 
    {
        if ( node.getTextRegion() == null ) {
            return "<no text range available>";
        }
        return node.getTextRegion().apply( source );
    }
    
    protected void resolveSymbols( final ICompilationUnit unit , ASTNode node ) 
    {
    	ICompilationContext context = new ICompilationContext() {

			@Override
			public List<ICompilationUnit> getAllCompilationUnits() {
				throw new UnsupportedOperationException("Not implemented");
			}

			@Override
			public ICompilationUnit getCurrentCompilationUnit() {
				return unit;
			}

			@Override
			public IObjectCodeWriterFactory getObjectCodeWriterFactory() {
				throw new UnsupportedOperationException("Not implemented");
			}

			@Override
			public ISymbolTable getSymbolTable() {
				return symbolTable;
			}

			@Override
			public boolean hasCompilerOption(CompilerOption option) {
				return false;
			}

			@Override
			public IResource resolve(String identifier) throws ResourceNotFoundException {
				throw new UnsupportedOperationException("Not implemented");
			}

			@Override
			public IResource resolveRelative(String identifier, IResource parent) throws ResourceNotFoundException {
				throw new UnsupportedOperationException("Not implemented");
			}

			@Override
			public ICompilationUnit getOrCreateCompilationUnit(
					IResource resource) throws IOException 
			{
		    	return CompilationUnit.createInstance( resource.getIdentifier() , resource );				
			}
		};
        if ( node instanceof ObjectCodeOutputNode ) 
        {
            ((ObjectCodeOutputNode) node).symbolsResolved( context );
        }
    }
    
    @Override
    public ICompilationUnit getOrCreateCompilationUnit(IResource resource)
    		throws IOException 
    {
    	return CompilationUnit.createInstance( resource.getIdentifier() , resource );
    }
}
