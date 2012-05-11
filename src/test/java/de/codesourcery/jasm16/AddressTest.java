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
    
    public void testSubtractionOnWordAddresses() 
    {
        final Address adr1 = Address.wordAddress( 0 );
        final Address sum1 = adr1.minus( Size.sizeInWords( 1 ) );
        assertEquals( Address.wordAddress( 0xffff ) , sum1 );
        
        final Address adr3 = Address.wordAddress( 1 );
        final Address sum2 = adr3.minus( Size.sizeInWords( 1 ) );
        assertEquals( Address.wordAddress( 0 ) , sum2 );        
    }    
    
    public void testSubtractionOnByteAddresses() 
    {
        final Address adr1 = Address.byteAddress( 0 );
        final Address sum1 = adr1.minus( Size.sizeInBytes( 1 ) );
        assertEquals( Address.byteAddress( 0x20000 - 1 ) , sum1 );
        
        final Address adr3 = Address.byteAddress( 1 );
        final Address sum2 = adr3.minus( Size.sizeInBytes( 1 ) );
        assertEquals( Address.byteAddress( 0 ) , sum2 );        
    }      
    
    public void testAdditionOnWordAddresses() 
    {
        final Address adr1 = Address.wordAddress( 0 );
        final Address sum1 = adr1.plus( Size.sizeInWords( 1 ) , true );
        assertEquals( Address.wordAddress( 1 ) , sum1 );
        
        final Address adr3 = Address.wordAddress( 0xffff );
        final Address sum2 = adr3.plus( Size.sizeInWords( 1 ) , true ); // wrap-around
        assertEquals( Address.wordAddress( 0 ) , sum2 );        
        
        final Address adr4 = Address.wordAddress( 0xffff );
        final Address sum3 = adr4.plus( Size.sizeInWords( 1 ) , false ); // no wrap
        assertEquals( Address.wordAddress( 0x10000 ) , sum3 );           
    }   
    
    public void testAdditionOnByteAddresses() 
    {
        final Address adr1 = Address.byteAddress( 0 );
        final Address sum1 = adr1.plus( Size.sizeInBytes( 1 ) , true );
        assertEquals( Address.byteAddress( 1 ) , sum1 );
        
        final Address adr3 = Address.byteAddress( (0xffff * 2) + 1 );
        final Address sum2 = adr3.plus( Size.sizeInBytes( 1 ) , true ); // wrap-around
        assertEquals( Address.byteAddress( 0 ) , sum2 );        
        
        final Address adr4 = Address.byteAddress( (0xffff * 2) + 1  );
        final Address sum3 = adr4.plus( Size.sizeInBytes( 1 ) , false ); // no wrap
        assertEquals( Address.byteAddress( 0x20000 ) , sum3 );           
    }      
}
