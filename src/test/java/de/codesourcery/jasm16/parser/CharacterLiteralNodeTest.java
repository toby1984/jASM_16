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
import de.codesourcery.jasm16.ast.CharacterLiteralNode;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.IParseContext;

public class CharacterLiteralNodeTest extends TestHelper
{

    public void test() throws ParseException 
    {
        final String input = "\"Hello \"";
        
        IParseContext context = createParseContext( input );
        final ASTNode result = new CharacterLiteralNode().parse( context );
        assertFalse( result.hasErrors() );
        
        final CharacterLiteralNode node = (CharacterLiteralNode) result;
        
        assertEquals( 6*2 , node.getBytes().size() );
        assertSourceCode( "\"Hello \"" , result );
    }
}
