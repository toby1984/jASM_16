package de.codesourcery.jasm16.parser;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.ICompilationUnit;

public class ASTTest extends TestHelper {

	public void testValidOrg() throws Exception {
		
		final String source = ".org 0x2000";
		
		ICompilationUnit unit = compile( source );
		assertFalse( unit.hasErrors() );
		assertEquals( Address.valueOf( 0x2000 ) , unit.getObjectCodeStartOffset() );
	}
	
	public void testValidOrg2() throws Exception {
		
		final String source = ".org 0x2000\n" +
				".org 0x2001";
		
		ICompilationUnit unit = compile( source );
		assertFalse( unit.hasErrors() );
		assertEquals( Address.valueOf( 0x2000 ) , unit.getObjectCodeStartOffset() );
	}	
	public void testInvalidOrg1() throws Exception {
		
		final String source = ".org 0x2000\n" +
				".org 0x2000";
		
		ICompilationUnit unit = compile( source );
		assertTrue( unit.hasErrors() );
		assertEquals( Address.valueOf( 0x2000 ) , unit.getObjectCodeStartOffset() );
	}		
	
	public void testInvalidOrg2() throws Exception {
		
		final String source = ".org 0x2000\n" +
				".org 0x1000";
		
		ICompilationUnit unit = compile( source );
		assertTrue( unit.hasErrors() );
		assertEquals( Address.valueOf( 0x2000 ) , unit.getObjectCodeStartOffset() );
	}	
}
