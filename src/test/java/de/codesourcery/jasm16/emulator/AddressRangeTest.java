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
package de.codesourcery.jasm16.emulator;

import junit.framework.TestCase;
import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Size;

public class AddressRangeTest extends TestCase 
{
    public void testContains1() {
        final AddressRange range = new AddressRange( Address.byteAddress( 2 ) , Size.sizeInBytes( 4 ) );
        
        assertEquals( 2 , range.getSize().toSizeInWords().getValue() );
        assertEquals( 2*2 , range.getSize().toSizeInBytes().getValue() );
        
        assertEquals( Address.wordAddress( 1 ) , range.getStartAddress() );
        assertEquals( Address.wordAddress( 3 ) , range.getEndAddress() );
        
        assertFalse( range.contains( Address.byteAddress( 0 ) ) );
        assertFalse( range.contains( Address.byteAddress( 1 ) ) );
        assertTrue( range.contains( Address.byteAddress( 2 ) ) );
        assertTrue( range.contains( Address.byteAddress( 3 ) ) );
        assertTrue( range.contains( Address.byteAddress( 4 ) ) );
        assertTrue( range.contains( Address.byteAddress( 5 ) ) );
        assertFalse( range.contains( Address.byteAddress( 6 ) ) );
    }
    
    public void testContains2() 
    {
        final AddressRange range = new AddressRange( Address.wordAddress( 1 ) , Size.sizeInWords( 2 ) );
        
        assertEquals( 2 , range.getSize().toSizeInWords().getValue() );
        assertEquals( 2*2 , range.getSize().toSizeInBytes().getValue() );
        
        assertEquals( Address.byteAddress( 2 ) , range.getStartAddress() );
        assertEquals( Address.byteAddress( 6 ) , range.getEndAddress() );
        
        assertFalse( range.contains( Address.wordAddress( 0 ) ) );
        assertTrue( range.contains( Address.wordAddress( 1 ) ) );
        assertTrue( range.contains( Address.wordAddress( 2 ) ) );
        assertFalse( range.contains( Address.wordAddress( 3 ) ) );
    }  
    
    public void testIntersectsWith() 
    {
        
        final AddressRange range1 = new AddressRange( Address.byteAddress( 0x04 ) , Size.sizeInBytes( 4 ) ); 
        
        // |---- b ----|---- a ----|
        final AddressRange range2 = new AddressRange( Address.byteAddress( 0x00 ) , Size.sizeInBytes( 4 ) ); 
        
        assertFalse( range1+" intersects "+range2+" ?" , range1.intersectsWith( range2 ) ); 
        assertFalse( range2.intersectsWith( range1 ) );       
        
        /* |---- b ----|
         *       |---- a ----|
         */
        final AddressRange range3 = new AddressRange( Address.byteAddress( 0x02 ) , Size.sizeInBytes( 4 ) ); 
        
        assertTrue( range1.intersectsWith( range3 ) ); 
        assertTrue( range3.intersectsWith( range1 ) );          
        
        /* |---- b ----|
         * |---- a ----|
         */        
        final AddressRange range4 = new AddressRange( Address.byteAddress( 0x04 ) , Size.sizeInBytes( 4 ) ); 
        
        assertTrue( range1.intersectsWith( range4 ) ); 
        assertTrue( range4.intersectsWith( range1 ) );
        
        /*       |---- b ----|
         * |---- a ----|
         */        
        final AddressRange range5 = new AddressRange( Address.byteAddress( 0x06 ) , Size.sizeInBytes( 4 ) );
        
        assertTrue( range1.intersectsWith( range5 ) ); 
        assertTrue( range5.intersectsWith( range1 ) );    
        
        /*             |---- b ----|
         * |---- a ----|
         */        
        final AddressRange range6 = new AddressRange( Address.byteAddress( 0x08 ) , Size.sizeInBytes( 4 ) );
        
        assertFalse( range1+" intersects "+range6+" ?" , range1.intersectsWith( range6 ) ); 
        assertFalse( range6.intersectsWith( range1 ) );          
    }
    
    public void testWrapping() 
    {
        AddressRange test = new AddressRange( Address.wordAddress( 0 ) , Size.sizeInWords( 65536 ) );
        assertEquals( 0 , test.getStartAddress().toWordAddress().getValue() );
        assertEquals( 65536 , test.getEndAddress().toWordAddress().getValue() );
    }
}
