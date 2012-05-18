package de.codesourcery.jasm16;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A thread-safe , ordered list of {@link AddressRange} instances that uses a binary
 * search to perform <i>contains(Address)</i> searches. 
 * 
 * <p>
 * This list is always ordered by ascending {@link AddressRange#getStartAddress()}.
 * </p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class AddressRangeList extends AbstractList<AddressRange>
{
    private final List<AddressRange> ranges = new ArrayList<AddressRange>();

    public AddressRangeList() {
    }
    
    @Override
    public void clear()
    {
        ranges.clear();
    }
    
    @Override
    public synchronized boolean add(AddressRange newRange)
    {
        int index = 0;
        AddressRange existing=null;
        for ( Iterator<AddressRange> it = ranges.iterator() ; it.hasNext() ; ) 
        {
            existing = it.next();
            if ( existing.intersectsWith( newRange ) ) 
            {
                if ( existing.contains( newRange ) ) {
                    return true;
                }
                ranges.set( index , existing.mergeWith( newRange ) );
                return true;                
            }
            
            if ( newRange.getStartAddress().isLessThan( existing.getStartAddress() ) ) 
            {
                if ( newRange.getEndAddress().equals( existing.getStartAddress() ) ) {
                    ranges.set( index , newRange.mergeWith( existing ) );
                    return true;
                } 
                ranges.add( index , newRange );
                return true;
            } 
            index++;
        }
        if ( existing != null && newRange.getStartAddress().equals( existing.getEndAddress() ) ) {
            ranges.set( index-1 , existing.mergeWith( newRange )  );
            return true;
        }
        ranges.add( newRange );
        return true;
    }
    
    /**
     * Uses a binary search to locate the address range that contains a given address.
     * 
     * @param address
     * @return
     */
    public synchronized AddressRange findAddressRange(Address address) {
        return binarySearch( address , 0 , ranges.size()-1 );        
    }
    
    private AddressRange binarySearch(Address address,int iStart,int iEnd) 
    {
        int start = iStart;
        int end = iEnd;
        
        while (end >= start)
          {
            final int pivot = (start + end) / 2;
            
            final AddressRange range = ranges.get( pivot );
            if ( range.contains( address ) ) {
                return range;
            }
            if ( range.getStartAddress().isLessThan( address ) ) {
              start = pivot + 1;
            } else {
              end = pivot - 1;
            }
        }
        return null;        
    }    
    
    @Override
    public synchronized AddressRange get(int index)
    {
        return ranges.get(index);
    }

    @Override
    public synchronized int size()
    {
        return ranges.size();
    }
    
}
