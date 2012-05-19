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

import junit.framework.TestCase;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

public class ASTNodeTest extends TestCase
{
    public class TestNode extends ASTNode {

    	public TestNode() {
    	}
    	
        
        public boolean supportsChildNodes() {
            return true;
        }    	
    	
    	public TestNode(ITextRegion range) {
    		super(range);
    	}
    	
        
		protected ASTNode parseInternal(IParseContext context) throws ParseException
        {
            return null;
        }
        
        
        protected void setTextRegionIncludingAllTokens(ITextRegion textRegion) {
        	super.setTextRegionIncludingAllTokens(textRegion);
        }

        
        public ASTNode copySingleNode()
        {
            return new TestNode();
        }
    }
    
    public void testAddChild() {
        
        final TestNode parent = new TestNode();
        assertEquals( null , parent.getTextRegion() );
        assertFalse( parent.hasChildren() );
        assertEquals(0,parent.getChildCount() );
        
        final TestNode child = new TestNode(new TextRegion(5,7) );
        parent.addChild( child , null );
        
        assertNull( parent.getParent() );
        assertSame( parent ,child.getParent() );
        
        assertTrue( new TextRegion( 5 , 7 ).isSame( parent.getTextRegion() ) );
        assertEquals( 1 , parent.getChildCount() );
        assertTrue( parent.hasChildren() );
        assertNotNull( parent.getChildren() );
        assertEquals( 1 , parent.getChildren().size() );
        assertSame( child , parent.getChildren().get(0) );
    }
}
