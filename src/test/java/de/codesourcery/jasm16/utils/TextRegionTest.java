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
package de.codesourcery.jasm16.utils;

import junit.framework.TestCase;

public class TextRegionTest extends TestCase
{
    
    public void testInvalidOffsetIsDetected() {
        try {
            new TextRegion( -1 , 25 );
            fail("Should've failed");
        } catch(IllegalArgumentException e) {
            // ok
        }
    }
    
    public void testInvalidLengthIsDetected() {
        try {
            new TextRegion( 0 , -3 );
            fail("Should've failed");
        } catch(IllegalArgumentException e) {
            // ok
        }
    }
    
    public void testGetters() {
        TextRegion range = new TextRegion( 1 , 2 );
        assertEquals( 1 , range.getStartingOffset() );
        assertEquals( 2 , range.getLength() );
        assertEquals( 3 , range.getEndOffset() );
    }
    
    public void testIsSame() {
        TextRegion range1 = new TextRegion( 1 , 2 );
        TextRegion range2 = new TextRegion( 1 , 2 );
        TextRegion range3 = new TextRegion( 1 , 3 );
        TextRegion range4 = new TextRegion( 2 , 2 );
        
        assertTrue( range1.isSame( range2 ) );
        assertTrue( range2.isSame( range1 ) );
        
        assertFalse( range1.isSame( range3 ) );
        assertFalse( range1.isSame( range4 ) );        
    }   
    
    public void testContainsOffset() 
    {
        TextRegion range1 = new TextRegion( 5 , 1 );
        
        assertFalse( range1.contains( 4 ) );
        assertTrue( range1.contains( 5 ) );
        assertFalse( range1.contains( 6 ) );
    }   
    
    public void testContainsRange() 
    {
        TextRegion range1 = new TextRegion( 5 , 4 ); // [5,9[
        
        TextRegion range2 = new TextRegion( 5 , 4 );
        TextRegion range3 = new TextRegion( 4 , 2 );
        TextRegion range4 = new TextRegion( 9 , 2 );
        TextRegion range5 = new TextRegion( 10 , 2 );
        TextRegion range6 = new TextRegion( 5 , 2 );
        TextRegion range7 = new TextRegion( 6 , 2 );

        assertTrue( range1.contains( range2 ) );
        assertFalse( range1.contains( range3 ) );
        assertFalse( range1.contains( range4 ) );
        assertFalse( range1.contains( range5 ) );
        assertTrue( range1.contains( range6 ) );
        assertTrue( range1.contains( range7 ) );
    }   
    
    public void testMergeRange() 
    {
        TextRegion range1 = new TextRegion( 5 , 4 ); // [5,9[
        TextRegion range2 = new TextRegion( 5 , 4 ); // [5,9[
        TextRegion range3 = new TextRegion( 4 , 2 ); // [4,6[
        
        TextRegion range4 = new TextRegion( 9 , 2 ); // [9,11[
        TextRegion range5 = new TextRegion( 10 , 2 ); // [10,12[
        TextRegion range6 = new TextRegion( 5 , 2 ); // [5,7[
        TextRegion range7 = new TextRegion( 6 , 2 ); // [6,8[

        assertEquals( range1 , merge( range1 , range2 ) );
        assertEquals( range1 , merge( range2 , range1 ) );
        
        assertEquals( new TextRegion( 4 , 5 ) , merge( range1 , range3 ) );
        assertEquals( new TextRegion( 5 , 5 ) , merge( range1 , range4 ) );
        assertEquals( new TextRegion( 5 , 7 ) , merge( range1 , range5 ) );    
        assertEquals( range1 , merge( range1 , range6 ) );
        assertEquals( new TextRegion( 5 , 4 ) , merge( range1 , range7 ) );    
    }   
    
    private ITextRegion merge(ITextRegion r1,ITextRegion r2) {
    	ITextRegion result = new TextRegion( r1 );
    	result.merge( r2 );
    	return result;
    }
    
    public void testSubtract() 
    {
        TextRegion range1 = new TextRegion( 5 , 4 ); // [5,9[
        
        TextRegion range2 = new TextRegion( 5 , 4 ); // [5,9[
        TextRegion range3 = new TextRegion( 4 , 2 ); // [4,6[
        
        TextRegion range4 = new TextRegion( 9 , 2 ); // [9,11[
        TextRegion range5 = new TextRegion( 10 , 2 ); // [10,12[
        TextRegion range6 = new TextRegion( 5 , 2 ); // [5,7[
        TextRegion range7 = new TextRegion( 6 , 2 ); // [6,8[

        assertEquals( new TextRegion(5,0) , subtract( range1 , range2 ) );
        assertEquals( new TextRegion(5,0) , subtract( range2 , range1 ) );
        assertEquals( new TextRegion( 6 , 4 ) , subtract( range1 , range3 ) );
        
        assertEquals( new TextRegion( 5 , 4 ) , subtract( range1 , range4 ) );
        
        assertEquals( new TextRegion( 5 , 4 ) , subtract( range1 , range5 ) );    
        assertEquals( new TextRegion( 7 , 2 ) , subtract( range1 , range6 ) );
        assertSubtractingFails( range1 , range7 , UnsupportedOperationException.class  );    
    }   
    
    private ITextRegion subtract(ITextRegion r1,ITextRegion r2) {
    	ITextRegion result = new TextRegion( r1 );
    	result.subtract( r2 );
    	return result;
    } 
    
    public void testIntersect() 
    {
        TextRegion range1 = new TextRegion( 5 , 4 ); // [5,9[
        TextRegion range2 = new TextRegion( 5 , 4 ); // [5,9[
        TextRegion range3 = new TextRegion( 4 , 2 ); // [4,6[
        
        TextRegion range4 = new TextRegion( 9 , 2 ); // [9,11[
        TextRegion range5 = new TextRegion( 10 , 2 ); // [10,12[
        TextRegion range6 = new TextRegion( 5 , 2 ); // [5,7[
        TextRegion range7 = new TextRegion( 6 , 2 ); // [6,8[

        assertEquals( new TextRegion(5,4) , intersect( range1 , range2 ) );
        assertEquals( new TextRegion( 5,4 ) , intersect( range2 , range1 ) );
        
        assertEquals( new TextRegion( 5 , 1 ) , intersect( range1 , range3 ) );
        assertEquals( new TextRegion( 5 , 1 ) , intersect( range3 , range1 ) );        
        
        assertIntersectingFails( range1 , range4 ,IllegalArgumentException.class );
        assertIntersectingFails( range4 , range1 ,IllegalArgumentException.class );
        
        assertIntersectingFails( range1 , range5 , IllegalArgumentException.class );
        assertIntersectingFails( range5 , range1 , IllegalArgumentException.class );
        
        assertEquals( new TextRegion( 5 , 2 ) , intersect( range1 , range6 ) );
        assertEquals( new TextRegion( 5 , 2 ) , intersect( range6 , range1 ) );
        
        assertEquals( new TextRegion( 6 , 2 ) , intersect( range1 , range7 ) );
        assertEquals( new TextRegion( 6 , 2 ) , intersect( range7 , range1 ) );
    } 
    
    private ITextRegion intersect(ITextRegion r1,ITextRegion r2) {
    	ITextRegion result = new TextRegion( r1 );
    	result.intersect( r2 );
    	return result;
    }     
    
    private void assertSubtractingFails(ITextRegion r1,ITextRegion r2,Class<? extends Exception> expected) {
        
        try {
            new TextRegion(r1).subtract( r2 );
            fail("Should've failed");
        } catch(Exception t) {
            assertEquals( expected , t.getClass() );
        }
    }
    
    private void assertIntersectingFails(ITextRegion r1,ITextRegion r2,Class<? extends Exception> expected) {
        
        try {
            TextRegion range = new TextRegion(r1);
			range.intersect( r2 );
            fail("intersecting "+r1+" with "+r2+" should've failed but yielded "+range);
        } catch(Exception t) {
            assertEquals( expected , t.getClass() );
        }
    }    
    
    public void testOverlaps() 
    {
        TextRegion range1 = new TextRegion( 5 , 4 ); // [5,9[
        
        TextRegion range2 = new TextRegion( 5 , 4 );
        TextRegion range3 = new TextRegion( 4 , 2 ); // [4,6[
        TextRegion range4 = new TextRegion( 9 , 2 );
        TextRegion range5 = new TextRegion( 10 , 2 );
        TextRegion range6 = new TextRegion( 5 , 2 );
        TextRegion range7 = new TextRegion( 6 , 2 );

        assertTrue( range1.overlaps( range2 ) );
        assertTrue( range1.overlaps( range3 ) );
        assertFalse( range1.overlaps( range4 ) );
        assertFalse( range1.overlaps( range5 ) );
        assertTrue( range1.overlaps( range6 ) );
        assertTrue( range1.overlaps( range7 ) );
        
        TextRegion range8 = new TextRegion( 75 , 113-75 ); // [5,9[
        TextRegion range9 = new TextRegion( 0 , 638 ); // [5,9[
        assertTrue( range8.overlaps( range9 ) );
        assertTrue( range9.overlaps( range8 ) );
    }     
    
    private boolean assertEquals(ITextRegion r1,ITextRegion r2) {
        return r1.isSame( r2 );
    }
    
    public void testApply() {
        final String string = "0123456789";
        assertEquals( "12345" , new TextRegion(1 , 5 ).apply( string ) );
    }
}
