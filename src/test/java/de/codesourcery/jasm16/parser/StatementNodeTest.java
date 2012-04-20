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

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.parser.IParseContext;

public class StatementNodeTest extends TestHelper {

	public void testParsingPreservesWhitespaceAtEOL() throws Exception {
		
		final String source = "SET A , 10 ";
		final IParseContext context = createParseContext( source );
		final ASTNode result = new StatementNode().parse( context );
		assertFalse( result.hasErrors() );
		assertEquals( source , toSourceCode( result , source ) );
	}
	
    public void testParseOrigin() throws Exception {
        
        assertCompiles( ".org 1024" );
        assertCompiles( ".origin 1024" );
        
        assertCompiles( ":label .org 1024" );
        assertCompiles( ":label .origin 1024" );        
    }
    
    public void testParseOrigin2() throws Exception {
        
        final String source = "  .org 1024\n.origin 2048";
        ICompilationUnit unit = compile( source );
        assertFalse( unit.hasErrors() );
        
        assertEquals( 2 , unit.getParsedLineCount() );
        assertEquals( "  .org 1024\n" , toSourceCode( unit.getAST().child(0) , source ) );
        assertEquals( ".origin 2048" , toSourceCode( unit.getAST().child(1) , source ) );        
    }    
	
    public void testParseDifferentLabelStyles() throws Exception {
        
        assertCompiles( ":label" );
        assertCompiles( "    :label" );
        assertCompiles( ":label   " );
        
        assertCompiles( ".label" );
        assertCompiles( "    .label" );
        assertCompiles( ".label   " );      
        
        assertCompiles( "label:" );
        assertCompiles( "    label:" );
        assertCompiles( "label:   " );        
        
    }	
}
