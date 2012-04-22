package de.codesourcery.jasm16.parser;

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.ast.OriginNode;

public class OriginNodeTest extends TestHelper {

	public void testAddressCalculation1() throws Exception {
		
		final String source = ".org 1024";
		IParseContext context = createParseContext( source );
		
		final ASTNode node = new OriginNode().parse( context );
		assertTrue( node instanceof OriginNode );
		assertEquals( 1024 , ((OriginNode) node).getSizeInBytes( 0 ) );
		assertEquals( 0 , ((OriginNode) node).getSizeInBytes( 1024 ) );
		assertEquals( ObjectCodeOutputNode.UNKNOWN_SIZE , ((OriginNode) node).getSizeInBytes( 2048 ) );		
	}
}
