package de.codesourcery.jasm16;

import junit.framework.TestCase;

public class AddressTest extends TestCase
{
    public void testCompareTo1() {
        assertEquals( -1 , Address.byteAddress( 1 ).compareTo( Address.byteAddress( 2 ) ) );
        assertEquals( 0 , Address.byteAddress( 1 ).compareTo( Address.byteAddress( 1 ) ) );
        assertEquals( 1 , Address.byteAddress( 2 ).compareTo( Address.byteAddress( 1 ) ) );    

        assertTrue( Address.byteAddress( 1 ).isLessThan( Address.byteAddress( 2 ) ) );
        assertTrue( Address.byteAddress( 1 ).isEqualOrLessThan( Address.byteAddress( 2 ) ) );
        
        assertEquals( 0 , Address.byteAddress( 1 ).compareTo( Address.byteAddress( 1 ) ) );
        assertTrue( Address.byteAddress( 1 ).equals( Address.byteAddress( 1 ) ) );        
        
        assertEquals( 1 , Address.byteAddress( 2 ).compareTo( Address.byteAddress( 1 ) ) );
        assertTrue( Address.byteAddress( 2 ).isGreaterThan( Address.byteAddress( 1 ) ) );
        assertTrue( Address.byteAddress( 2 ).isEqualOrGreaterThan( Address.byteAddress( 1 ) ) );        
    }
    
    public void testCompareTo2() {
        assertEquals( -1 , Address.wordAddress( 1 ).compareTo( Address.wordAddress( 2 ) ) );
        assertEquals( 0 , Address.wordAddress( 1 ).compareTo( Address.wordAddress( 1 ) ) );
        assertEquals( 1 , Address.wordAddress( 2 ).compareTo( Address.wordAddress( 1 ) ) );        
    }   
    
    public void testCompareTo3() {
        assertEquals( -1 , Address.wordAddress( 1 ).compareTo( Address.byteAddress( 3 ) ) );
        assertEquals( 0 , Address.wordAddress( 1 ).compareTo( Address.byteAddress( 2 ) ) );
        assertEquals( 1 , Address.wordAddress( 2 ).compareTo( Address.byteAddress( 1 ) ) );        
    }      
}
