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

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.InitializedMemoryNode;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.exceptions.ParseException;

public class InitializedMemoryNodeTest extends TestHelper
{

    public void testParseInitializedMemory() throws ParseException, IOException 
    {
        final String source = ".dat 0x01";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InitializedMemoryNode.class , result.getClass() );
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        resolveSymbols( unit , node );
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 2 , size );
        final byte[] data = node.getBytes();
        assertEquals( 2 , data.length );
        assertEquals( 0 , data[0] );
        assertEquals( 1 , data[1] );
        assertSourceCode( ".dat 0x01" , result );        
    }  
    
    public void testParseInitializedMemory1() throws ParseException, IOException 
    {
        final String source = ".dat (1+2)";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InitializedMemoryNode.class , result.getClass() );
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        resolveSymbols( unit, node );
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 2 , size );
        final byte[] data = node.getBytes();
        assertEquals( 2 , data.length );
        assertEquals( 0 , data[0] );
        assertEquals( 3 , data[1] );
        assertSourceCode( ".dat (1+2)" , result );        
    }     
    
    public void testParseInitializedMemory2() throws ParseException, IOException 
    {
        final String source = ".dat 0x12,0x34";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InitializedMemoryNode.class , result.getClass() );
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        resolveSymbols( unit , node );
        
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 4 , size );
        final byte[] data = node.getBytes();
        assertEquals( 4 , data.length );
        assertEquals( 0x0 , data[0] );
        assertEquals( 0x12 , data[1] );
        assertEquals( 0 , data[2] );
        assertEquals( 0x34 , data[3] );
        assertSourceCode( ".dat 0x12,0x34" , result );        
    }   
    
    public void testParseInitializedMemoryWithExpressions() throws ParseException, IOException 
    {
        final String source = ".dat 0x10+2,0x34";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InitializedMemoryNode.class , result.getClass() );
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        resolveSymbols( unit , node );
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 4 , size );
        final byte[] data = node.getBytes();
        assertEquals( 4 , data.length );
        assertEquals( 0x0 , data[0] );
        assertEquals( 0x12 , data[1] );
        assertEquals( 0 , data[2] );
        assertEquals( 0x34 , data[3] );
        assertSourceCode( ".dat 0x10+2,0x34" , result );        
    }     
    
    public void testParseInitializedMemory3() throws ParseException, IOException 
    {
        final String source = ".dat \"a\"";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InitializedMemoryNode.class , result.getClass() );
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        resolveSymbols(unit, node );
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 2 , size );
        final byte[] data = node.getBytes();
        assertEquals( 2 , data.length );
        assertEquals( 0 , data[0] );
        assertEquals( 97 , data[1] );
        assertSourceCode( ".dat \"a\"" , result );        
    }   
    
    public void testParseInitializedMemory4() throws ParseException, IOException 
    {
        final String source = ".dat \"ab\"";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        assertFalse( getErrors( source , result ) , result.hasErrors() );
        assertEquals( InitializedMemoryNode.class , result.getClass() );
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        resolveSymbols( unit , node );
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 4 , size );
        final byte[] data = node.getBytes();
        assertEquals( 4 , data.length );
        assertEquals( 0 , data[0] );
        assertEquals( 97 , data[1] );
        assertEquals( 0 , data[2] );
        assertEquals( 98 , data[3] );        
        assertSourceCode( ".dat \"ab\"" , result );        
    }     
    
    public void testParseInitializedMemory6() 
    {
        final String source = ".byte \"a\"";

        final IParseContext context = createParseContext( source );

        final ASTNode result = new InitializedMemoryNode().parse( context );
        assertTrue( result.hasErrors() );
    }   
    
    public void testParseInitializedMemory7() 
    {
        final String source = ".byte 256";

        final IParseContext context = createParseContext( source );

        new InitializedMemoryNode().parse( context );
        assertTrue( context.getCompilationUnit().hasErrors() );
    }  
    
    public void testParseInitializedMemory8() throws ParseException, IOException 
    {
        final String source = ".word 256,2";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        resolveSymbols( unit, node );
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 4 , size );
        final byte[] data = node.getBytes();
        assertEquals( 1 , data[0] );
        assertEquals( 0 , data[1] );        
        assertEquals( 0 , data[2] );
        assertEquals( 2 , data[3] );          
        assertSourceCode( ".word 256,2" , result );         
    }  
    
    public void testParseInitializedMemoryWithValueOutOfRange() throws Exception 
    {
        final String source = "label: .dat label+0xffff+1";

        ICompilationUnit unit = compile( source );
        assertTrue( unit.hasErrors() );
        
        assertSourceCode( source , unit.getAST() );         
    }
    
    public void testParseInitializedMemory9() throws ParseException, IOException 
    {
        final String source = ".dat 256,2";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        resolveSymbols( unit , node );
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 4 , size );
        final byte[] data = node.getBytes();
        assertEquals( 1 , data[0] );
        assertEquals( 0 , data[1] );        
        assertEquals( 0 , data[2] );
        assertEquals( 2 , data[3] );        
        assertSourceCode( ".dat 256,2" , result );         
    }    // 1
    
    public void testParseInitializedMemory10() throws IOException, ParseException 
    {
        final String source = ".dat 0x170, \"Hello \", 0x2e1";

        ICompilationUnit unit = CompilationUnit.createInstance("string",source);
        final IParseContext context = createParseContext( unit );
        
        final ASTNode result = new InitializedMemoryNode().parse( context );
        resolveSymbols(unit, result );
        assertFalse( result.hasErrors() );
        assertSourceCode( ".dat 0x170, \"Hello \", 0x2e1" , result ); 
        
        final InitializedMemoryNode node = (InitializedMemoryNode) result;
        final int size = node.getSizeInBytes( 0 );
        assertEquals( 16 ,size );

        final byte[][] actual = new byte[1][];
        IObjectCodeWriter writer = new IObjectCodeWriter() {
            
            @Override
            public void close() throws IOException { }
            
            @Override
            public void writeObjectCode(byte[] data, int offset, int length) throws IOException { }
            
            @Override
            public void writeObjectCode(byte[] values) throws IOException
            {
                actual[0] = values;
            }
            
            @Override
            public void deleteOutput() throws IOException { }

            @Override
            public Address getCurrentWriteOffset()
            {
                return Address.ZERO;
            }

            @Override
            public void advanceToWriteOffset(Address offset) throws IOException
            {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
        
        
        ICompilationUnit instance = CompilationUnit.createInstance("dummy" , source );
        ICompilationContext compContext = createCompilationContext(instance);
        node.writeObjectCode( writer, compContext );
        
        final byte[] data = actual[0];
        assertEquals( 16 , data.length );
        
        final byte[] expected = {0x01,0x70,0x00,0x48,0x00,0x65,0x00,0x6c,0x00,0x6c,0x00,0x6f,0x00,0x20,0x02,(byte) 0xe1};
        assertEquals( expected.length , data.length );
        for ( int i = 0 ; i < expected.length ; i++ ) {
            assertEquals( expected[i] , data[i] );
        }
        
    }   
}
