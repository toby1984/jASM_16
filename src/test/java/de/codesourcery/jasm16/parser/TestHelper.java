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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
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
import de.codesourcery.jasm16.compiler.DebugInfo;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnitResolver;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.IMarker;
import de.codesourcery.jasm16.compiler.IParentSymbolTable;
import de.codesourcery.jasm16.compiler.ParentSymbolTable;
import de.codesourcery.jasm16.compiler.io.AbstractResourceResolver;
import de.codesourcery.jasm16.compiler.io.ByteArrayObjectCodeWriterFactory;
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
import de.codesourcery.jasm16.utils.FormattingVisitor;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.Misc;

public abstract class TestHelper extends TestCase implements ICompilationUnitResolver
{
    protected IParentSymbolTable symbolTable;
 
    protected DebugInfo debugInfo;
    
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
    
	protected final class MyResolver implements ICompilationUnitResolver {

		private final ICompilationUnit unit;
		
		public MyResolver(ICompilationUnit unit) {
			this.unit = unit;
		}
		@Override
		public ICompilationUnit getOrCreateCompilationUnit(IResource resource) throws IOException 
		{
			if ( unit.getResource().getIdentifier().equals( resource.getIdentifier() ) ) {
				return unit;
			}
			throw new UnsupportedOperationException("Don't know how to create ICompilationUnit for "+resource);
		}

		@Override
		public ICompilationUnit getCompilationUnit(IResource resource) throws IOException 
		{
			throw new UnsupportedOperationException("Not implemented");
		}
	}    
    
    protected File getTempDir() throws IOException {
    	File f = File.createTempFile("blubb"," blah");
    	File parent = f.getParentFile();
    	assertNotNull( parent );
    	f.delete();
    	return parent;
    }
    
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
        return new ParseContext( unit , symbolTable , new Lexer(new Scanner( source ) ) , RESOURCE_RESOLVER ,this, Collections.singleton( ParserOption.DEBUG_MODE ) , false );
    }
    
    protected IParseContext createParseContext(ICompilationUnit unit) throws IOException 
    {
    	final String source = Misc.readSource( unit.getResource() );
        return new ParseContext( unit , symbolTable , new Lexer(new Scanner( source ) ) , RESOURCE_RESOLVER ,this, Collections.singleton( ParserOption.DEBUG_MODE ) , false );
    }    
    
    @Override
    protected void setUp() throws Exception
    {
        debugInfo = new DebugInfo();
        symbolTable = new ParentSymbolTable();
    }
    
    protected final IToken assertToken(ILexer lexer , TokenType type , String contents ) {
        return assertToken( lexer , type , contents , -1 );
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
    
    protected byte[] compileToByteCode(String source) 
    {
        
        final ICompiler c = new de.codesourcery.jasm16.compiler.Compiler();
        final ByteArrayObjectCodeWriterFactory factory = new ByteArrayObjectCodeWriterFactory();
        c.setObjectCodeWriterFactory( factory );
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string" , source );
        
        c.compile( Collections.singletonList( unit ) );
        
        if ( unit.hasErrors() ) {
            Misc.printCompilationErrors( unit , source , true );
            throw new RuntimeException("Internal error, compilation failed.");
        }
        
        return factory.getBytes();
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
    
    protected final IToken assertToken(ILexer lexer , TokenType type , String contents , int parseOffset) 
    {
        assertFalse( lexer.eof() );
        IToken tok = lexer.read();
        assertNotNull( tok );
        assertEquals( type , tok.getType() );
        assertEquals( contents  , tok.getContents() );
        if ( parseOffset != -1 ) {
            assertEquals( parseOffset , tok.getStartingOffset() );
        }
        return tok;
    }
    
    protected final IToken assertToken(ILexer lexer , OpCode opCode , String contents) 
    {
        return assertToken(lexer,opCode,contents,-1);
    }
    
    protected final ICompilationContext createCompilationContext(ICompilationUnit unit) throws IOException 
    {
        return new CompilationContext( unit , symbolTable , new NullObjectCodeWriterFactory() , 
        		new AbstractResourceResolver() {
            
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
    
    protected final IToken assertToken(ILexer lexer , OpCode opCode , String contents , int parseOffset) 
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
        return tok;
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
			public ICompilationUnit getCurrentCompilationUnit() {
				return unit;
			}

			@Override
			public IObjectCodeWriterFactory getObjectCodeWriterFactory() {
				throw new UnsupportedOperationException("Not implemented");
			}

			@Override
			public IParentSymbolTable getSymbolTable() {
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

			@Override
			public ICompilationUnit getCompilationUnit(IResource resource)
					throws IOException 
			{
				return null;
			}

            @Override
            public DebugInfo getDebugInfo()
            {
                return debugInfo;
            }

			@Override
			public void addMarker(IMarker marker) {
			}

			@Override
			public void addCompilationError(String message, ASTNode node) {
			}
		};
        if ( node instanceof ObjectCodeOutputNode ) 
        {
            ((ObjectCodeOutputNode) node).symbolsResolved( context );
        }
    }
    
    @Override
    public ICompilationUnit getCompilationUnit(IResource resource)
    		throws IOException {
    	return null;
    }
    
    @Override
    public ICompilationUnit getOrCreateCompilationUnit(IResource resource)
    		throws IOException 
    {
    	return CompilationUnit.createInstance( resource.getIdentifier() , resource );
    }
    
    protected void assertEquals(File expected,File actual) {
    	assertEquals( expected.getAbsolutePath() , actual.getAbsolutePath() );
    }
    
}