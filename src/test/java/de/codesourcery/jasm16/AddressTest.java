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
        final Address sum1 = adr1.minus( Size.words( 1 ) );
        assertEquals( Address.wordAddress( 0xffff ) , sum1 );
        
        final Address adr3 = Address.wordAddress( 1 );
        final Address sum2 = adr3.minus( Size.words( 1 ) );
        assertEquals( Address.wordAddress( 0 ) , sum2 );        
    }    
    
    public void testSubtractionOnByteAddresses() 
    {
        final Address adr1 = Address.byteAddress( 0 );
        final Address sum1 = adr1.minus( Size.bytes( 1 ) );
        assertEquals( Address.byteAddress( 0x20000 - 1 ) , sum1 );
        
        final Address adr3 = Address.byteAddress( 1 );
        final Address sum2 = adr3.minus( Size.bytes( 1 ) );
        assertEquals( Address.byteAddress( 0 ) , sum2 );        
    }      
    
    public void testAdditionOnWordAddresses() 
    {
        final Address adr1 = Address.wordAddress( 0 );
        final Address sum1 = adr1.plus( Size.words( 1 ) , true );
        assertEquals( Address.wordAddress( 1 ) , sum1 );
        
        final Address adr3 = Address.wordAddress( 0xffff );
        final Address sum2 = adr3.plus( Size.words( 1 ) , true ); // wrap-around
        assertEquals( Address.wordAddress( 0 ) , sum2 );        
        
        final Address adr4 = Address.wordAddress( 0xffff );
        final Address sum3 = adr4.plus( Size.words( 1 ) , false ); // no wrap
        assertEquals( Address.wordAddress( 0x10000 ) , sum3 );           
    }   
    
    public void testAdditionOnByteAddresses() 
    {
        final Address adr1 = Address.byteAddress( 0 );
        final Address sum1 = adr1.plus( Size.bytes( 1 ) , true );
        assertEquals( Address.byteAddress( 1 ) , sum1 );
        
        final Address adr3 = Address.byteAddress( (0xffff * 2) + 1 );
        final Address sum2 = adr3.plus( Size.bytes( 1 ) , true ); // wrap-around
        assertEquals( Address.byteAddress( 0 ) , sum2 );        
        
        final Address adr4 = Address.byteAddress( (0xffff * 2) + 1  );
        final Address sum3 = adr4.plus( Size.bytes( 1 ) , false ); // no wrap
        assertEquals( Address.byteAddress( 0x20000 ) , sum3 );           
    }      
}
