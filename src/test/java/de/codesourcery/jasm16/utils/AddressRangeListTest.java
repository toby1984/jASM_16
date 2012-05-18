package de.codesourcery.jasm16.utils;

import junit.framework.TestCase;
import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.AddressRangeList;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;

public class AddressRangeListTest extends TestCase 
{
    private AddressRangeList list;
    
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        list = new AddressRangeList();
    }
    
    public void testAddOne() {
        
        final WordAddress start = Address.wordAddress( 10 );
        final AddressRange range1 = new AddressRange( start , Size.words( 1 ) );
        
        list.add( range1 );
        assertFalse( list.isEmpty() );
        assertEquals( 1 , list.size() );
        
        assertFalse( range1.equals( list.findAddressRange( start.minus( Size.words(1 ) ) ) ) );          
        assertTrue( range1.equals( list.findAddressRange( start ) ) );
        assertFalse( range1.equals( list.findAddressRange( start.plus( Size.words(1 ), false ) ) ) );        
    }
    
    public void testAddTwo() {
        
        final WordAddress start1 = Address.wordAddress( 10 );
        final AddressRange range1 = new AddressRange( start1 , Size.words( 1 ) );
        
        final WordAddress start2 = Address.wordAddress( 11 );
        final AddressRange range2 = new AddressRange( start2 , Size.words( 1 ) );        
        
        list.add( range1 );
        list.add( range2 );        
        assertFalse( list.isEmpty() );
        assertEquals( 1 , list.size() );
        
        assertFalse( range1.equals( list.findAddressRange( start1.minus( Size.words(1 ) ) ) ) );          
        assertEquals( new AddressRange( start1 , Size.words(2) ) , list.findAddressRange( start1 ) );
        assertEquals( new AddressRange( start1 , Size.words(2) ) , list.findAddressRange( start1.plus( Size.words(1 ), false ) ) );        
        assertFalse( range1.equals( list.findAddressRange( start2.plus( Size.words(2 ), false ) ) ) );          
    }   
    
    public void testAddingOverlappingRangesWorks() {
        
        final WordAddress start1 = Address.wordAddress( 10 );
        final AddressRange range1 = new AddressRange( start1 , Size.words( 1 ) );
        
        final WordAddress start2 = Address.wordAddress( 9 );
        final AddressRange range2 = new AddressRange( start2 , Size.words( 5 ) );        
        
        list.add( range1 );
        list.add( range2 );
        
        assertFalse( list.isEmpty() );
        assertEquals( 1 , list.size() );
        assertEquals( new AddressRange( Address.wordAddress( 9 ) , Size.words( 5) ) , list.get(0) );
    }     
    
    public void testBinarySearch() {
        
        for ( int i = 1 ; i < 11 ; i++ ) {
            WordAddress start = Address.wordAddress( i );
            final AddressRange range = new AddressRange( start , Size.words( 1 ) );
            list.add( range );
        }

        assertEquals( 1 , list.size() );
        
        assertNull( list.findAddressRange( Address.wordAddress( 0 ) ) );
        assertEquals( new AddressRange(Address.wordAddress(1),Size.words(10) ) , list.findAddressRange( Address.wordAddress( 1 ) ) );
        assertEquals( new AddressRange(Address.wordAddress(1),Size.words(10) ) , list.findAddressRange( Address.wordAddress( 4 ) ) );
        assertEquals( new AddressRange(Address.wordAddress(1),Size.words(10) ) , list.findAddressRange( Address.wordAddress( 5 ) ) );
        assertEquals( new AddressRange(Address.wordAddress(1),Size.words(10) ) , list.findAddressRange( Address.wordAddress( 6 ) ) );
        assertEquals( new AddressRange(Address.wordAddress(1),Size.words(10) ) , list.findAddressRange( Address.wordAddress( 10 ) ) );          
        assertNull( list.findAddressRange( Address.wordAddress( 11 ) ) );        
    }     
}
