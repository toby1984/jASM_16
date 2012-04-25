package de.codesourcery.jasm16.emulator;

import junit.framework.TestCase;

public class WorkItemListTest extends TestCase
{
    private WorkItemList list;

    @Override
    protected void setUp() throws Exception
    {
        list = new WorkItemList();
    }
    
    public void testAddOne() {
        
        assertTrue( list.isEmpty() );
        final WorkItem item1 = new WorkItem( 0 , MicroOp.LOAD_A );
        list.insertWorkItem( item1 );
        
        assertFalse( list.isEmpty() );
        assertEquals( 1 , list.listView().size() );
        assertSame( item1 , list.listView().get(0) );
        assertSame( item1 , list.getFirst() );
    }
    
    public void testAddTwo() {
        
        assertTrue( list.isEmpty() );
        final WorkItem item1 = new WorkItem( 0 , MicroOp.LOAD_A );
        final WorkItem item2 = new WorkItem( 1 , MicroOp.LOAD_B );
        
        list.insertWorkItem( item1 );
        
        assertFalse( list.isEmpty() );
        assertEquals( 1 , list.listView().size() );
        assertSame( item1 , list.listView().get(0) );
        assertSame( item1 , list.getFirst() );
        
        list.insertWorkItem( item2 );  
        
        assertFalse( list.isEmpty() );
        assertEquals( 2 , list.listView().size() );
        assertSame( item2 , list.listView().get(1) );
        assertSame( item1 , list.getFirst() );        
    }   
    
    public void testAddThree() {
        
        assertTrue( list.isEmpty() );
        final WorkItem item1 = new WorkItem( 0 , MicroOp.LOAD_A );
        final WorkItem item2 = new WorkItem( 1 , MicroOp.LOAD_B );
        final WorkItem item3 = new WorkItem( 2 , MicroOp.LOAD_C );        
        
        list.insertWorkItem( item1 );
        
        assertFalse( list.isEmpty() );
        assertEquals( 1 , list.listView().size() );
        assertSame( item1 , list.listView().get(0) );
        assertSame( item1 , list.getFirst() );
        
        list.insertWorkItem( item2 );  
        
        assertFalse( list.isEmpty() );
        assertEquals( 2 , list.listView().size() );
        assertSame( item2 , list.listView().get(1) );
        assertSame( item1 , list.getFirst() );      
        
        list.insertWorkItem( item3 );  
        
        assertFalse( list.isEmpty() );
        assertEquals( 3 , list.listView().size() );
        assertSame( item3 , list.listView().get(2) );        
        assertSame( item2 , list.listView().get(1) );
        assertSame( item1 , list.getFirst() );           
    }     
    
    public void testAddThreeDifferentOrder1() {
        
        assertTrue( list.isEmpty() );
        final WorkItem item3 = new WorkItem( 0 , MicroOp.LOAD_A );
        final WorkItem item2 = new WorkItem( 1 , MicroOp.LOAD_B );
        final WorkItem item1 = new WorkItem( 2 , MicroOp.LOAD_C );        
        
        list.insertWorkItem( item1 );
        list.insertWorkItem( item2 );  
        list.insertWorkItem( item3 );  
        
        assertFalse( list.isEmpty() );
        assertEquals( 3 , list.listView().size() );
        
        assertSame( item1 , list.listView().get(2) );          
        assertSame( item2 , list.listView().get(1) );
        assertSame( item3 , list.getFirst() );      
    }  
    
    public void testAddThreeDifferentOrder2() {
        
        assertTrue( list.isEmpty() );
        final WorkItem item3 = new WorkItem( 0 , MicroOp.LOAD_A );
        final WorkItem item2 = new WorkItem( 1 , MicroOp.LOAD_B );
        final WorkItem item1 = new WorkItem( 2 , MicroOp.LOAD_C );        
        
        list.insertWorkItem( item1 );
        list.insertWorkItem( item3 );
        list.insertWorkItem( item2 );          
        
        assertFalse( list.isEmpty() );
        assertEquals( 3 , list.listView().size() );
        
        assertSame( item1 , list.listView().get(2) );          
        assertSame( item2 , list.listView().get(1) );
        assertSame( item3 , list.getFirst() );      
    }   
    
    public void testAddThreeDifferentOrder3() {
        
        assertTrue( list.isEmpty() );
        final WorkItem item3 = new WorkItem( 0 , MicroOp.LOAD_A );
        final WorkItem item2 = new WorkItem( 1 , MicroOp.LOAD_B );
        final WorkItem item1 = new WorkItem( 2 , MicroOp.LOAD_C );        

        list.insertWorkItem( item2 );         
        list.insertWorkItem( item1 );
        list.insertWorkItem( item3 );
        
        assertFalse( list.isEmpty() );
        assertEquals( 3 , list.listView().size() );
        
        assertSame( item1 , list.listView().get(2) );          
        assertSame( item2 , list.listView().get(1) );
        assertSame( item3 , list.getFirst() );      
    }   
    
    public void testRemove() {
        
        assertTrue( list.isEmpty() );
        final WorkItem item3 = new WorkItem( 0 , MicroOp.LOAD_A );
        final WorkItem item2 = new WorkItem( 1 , MicroOp.LOAD_B );
        final WorkItem item1 = new WorkItem( 2 , MicroOp.LOAD_C );        

        list.insertWorkItem( item2 );         
        list.insertWorkItem( item1 );
        list.insertWorkItem( item3 );
        
        assertFalse( list.isEmpty() );
        assertEquals( 3 , list.listView().size() );
        
        assertSame( item1 , list.listView().get(2) );          
        assertSame( item2 , list.listView().get(1) );
        assertSame( item3 , list.getFirst() );     
        
        list.removeFirst();
        
        assertFalse( list.isEmpty() );
        assertEquals( 2 , list.listView().size() );
        
        assertSame( item1 , list.listView().get(1) );
        assertSame( item2 , list.getFirst() );             
        
        list.removeFirst();
        
        assertFalse( list.isEmpty() );
        assertEquals( 1 , list.listView().size() );
        
        assertSame( item1 , list.getFirst() );      
        
        list.removeFirst();
        
        assertTrue( list.isEmpty() );
        assertEquals( 0 , list.listView().size() );
    }     
    
    
}
