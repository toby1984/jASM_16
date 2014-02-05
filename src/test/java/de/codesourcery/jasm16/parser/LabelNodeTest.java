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
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.ast.UnparsedContentNode;
import de.codesourcery.jasm16.compiler.*;
import de.codesourcery.jasm16.utils.TextRegion;

public class LabelNodeTest extends TestHelper
{
    public void testParseNotchStyleLabel() throws Exception {
        
        String source = ":label";
        
        ASTNode result = new LabelNode().parse( createParseContext( source ) );
        assertEquals(LabelNode.class , result.getClass() );
        assertSourceCode( source  , result );
        assertEquals( "label" , ((LabelNode) result).getIdentifier().getRawValue() );
    }
    
    public void testParseLocalLabelWithoutPreviousGlobalFirst() throws Exception {
        
        String source = ".label";
        ASTNode result = new LabelNode().parse( createParseContext( source ) );
        assertEquals(UnparsedContentNode.class , result.getClass() );
    }       
    
    public void testParseLocalLabel() throws Exception {
        
        String source = ".label";
        IParseContext context = createParseContext( source );
        
        final ISymbol globalSymbol = new Label( context.getCompilationUnit() , new TextRegion(0,source.length() ) , new Identifier("globalLabel") , null );
        context.getSymbolTable().defineSymbol( globalSymbol );
		context.storePreviousGlobalSymbol( globalSymbol );
		
		ASTNode result = new LabelNode().parse( context );
        assertEquals(LabelNode.class , result.getClass() );
        assertSourceCode( source  , result );
        assertEquals( "label" , ((LabelNode) result).getIdentifier().getRawValue() );
    }   
    
    public void testParseIntelLabel() throws Exception {
        
        String source = "label:";
        
        ASTNode result = new LabelNode().parse( createParseContext( source ) );
        assertEquals(LabelNode.class , result.getClass() );
        assertSourceCode( source  , result );
        assertEquals( "label" , ((LabelNode) result).getIdentifier().getRawValue() );
    }      
}
