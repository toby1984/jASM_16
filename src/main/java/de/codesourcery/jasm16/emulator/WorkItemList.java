package de.codesourcery.jasm16.emulator;

import java.util.AbstractList;
import java.util.List;
import java.util.NoSuchElementException;

public final class WorkItemList
{
    private WorkItem first;
    
    public boolean isEmpty() {
        return first == null;
    }
    
    public WorkItem getFirst() {
        return first;
    }
    
    public void executeAllItemsForCurrentCycle(Simulation sim, int currentCycle) 
    {
        WorkItem item=null;        
        while ( first != null && first.executionTime == currentCycle ) 
        {
            item=first;
            removeFirst();
            item.action.execute( sim );                
        }        
    }
    
    public void removeFirst() 
    {
        final WorkItem next = first.next;
        first.next = null;
        first.prev = null;
        first = next;
    }
    
    public void clear() {
        first = null;
    }
    
    public final void insertWorkItem(WorkItem item) 
    {
        WorkItem current = first;
        if ( current == null ) 
        {
            first = item;
            return;
        }
        
        final int newTime = item.executionTime;
        
        while ( true ) 
        {
            if ( newTime <= current.executionTime ) 
            {
                /*
                 *   A <-> B <-> C
                 *   A <-> X <-> B <-> C
                 */
                
                // insert here instead of current node
                if ( current.prev != null ) 
                {
                    item.prev = current.prev;
                    item.next = current;
                    current.prev.next = item;
                    current.prev = item;
                } else {
                    // replacing first node...
                    item.next = current;
                    current.prev = item;                    
                    first = item;
                }
                return;
            }
            
            if ( current.next == null ) 
            {
                item.prev = current;
                current.next=item;
                return;
            }
            current = current.next;
        }
    }    
    
    @Override
    public String toString()
    {
        final StringBuilder result = new StringBuilder("{");
        
        WorkItem current = first;
        while ( current != null ) {
            result.append( current );
            current = current.next;
            if ( current != null ) {
                result.append(" , ");
            }
        }
        result.append("}");
        return result.toString();
    }

    private final List<WorkItem> regularList = new AbstractList<WorkItem>() {

        @Override
        public WorkItem get(int index)
        {
            int i = 0;
            WorkItem current = first;
            while( current != null ) {
                if ( index == i ) {
                    return current;
                }
                i++;
                current = current.next;
            }
            throw new NoSuchElementException("Invalid index "+index);
        }

        @Override
        public int size()
        {
            int size = 0;
            WorkItem current = first;
            while( current != null ) {
                size++;
                current = current.next;
            }
            return size;
        }
    };
    
    public List<WorkItem> listView() 
    {
        return regularList; 
    }
}
