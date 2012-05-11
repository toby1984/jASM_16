package de.codesourcery.jasm16.emulator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;

/**
 * DCPU-16 main memory emulation.
 * 
 * <p>Note that the DCPU-16 only supports word-sized addressing. This memory implementation supports
 * overlay memory (see {@link IMemoryRegion} to enable features like video RAM etc.</p>
 * 
 * @author tobias.gierke@voipfuture.com
 */
public class MainMemory implements IMemory
{
    private final List<IMemoryRegion> regions = new ArrayList<IMemoryRegion>(); 
    
    public MainMemory(int sizeInWords) 
    {
        regions.add( createMainMemory( new AddressRange( WordAddress.ZERO , Size.sizeInWords( 65536 ) ) ) );
    }
    
    private static IMemoryRegion createMainMemory(AddressRange range) {
        return new MemoryRegion( "main memory" , range );
    }
    
    @Override
    public void clear()
    {
        for ( IMemory r : regions ) {
            r.clear();
        }
    }
    
    /**
     * Replaces a mapped memory region with plain (unmapped) main-memory.
     * 
     * @param region
     */
    public void unmapRegion(IMemoryRegion region) {
    
        if (region == null) {
            throw new IllegalArgumentException("region must not be NULL.");
        }
        
        for (Iterator<IMemoryRegion> it = regions.iterator(); it.hasNext();) {
            final IMemoryRegion existing = it.next();
            if ( existing == region ) 
            {
                it.remove();
                
                final IMemoryRegion mainMemory = createMainMemory( existing.getAddressRange() );
                // preserve memory contents
                MemUtils.memCopy( existing , mainMemory , WordAddress.ZERO , existing.getSize() );
                mapRegion( mainMemory );
                return;
            }
        }
        throw new IllegalArgumentException("Cannot unmap unknown region "+region);
    }
    
    /**
     * Maps main memory to a specific region.
     * 
     * @param region
     * @see #unmapRegion(IMemoryRegion)
     */
    public void mapRegion(IMemoryRegion region) 
    {
        if (region == null) {
            throw new IllegalArgumentException("region must not be NULL.");
        }
        boolean intersects = false;
        do 
        {
            intersects = false;
            int index = 0;
            for ( IMemoryRegion existing : regions ) 
            {
                if ( existing.getAddressRange().intersectsWith( region.getAddressRange() ) ) 
                {
                    regions.remove( index );
                    regions.addAll( index , existing.subtract( region.getAddressRange() ) );
                    intersects = true;
                    break;
                }
                index++;
            }
        } while ( intersects );
        
        // no intersection, just insert into the list
        int index = 0;
        for ( IMemoryRegion existing : regions ) 
        {
            if ( region.getAddressRange().getStartAddress().isLessThan( existing.getAddressRange().getStartAddress() ) ) {
                regions.add( index , region );
                return;
            }
            index++;
        }
        regions.add( region );
    }

    @Override
    public int read(int wordAddress)
    {
        final WordAddress address = Address.wordAddress( wordAddress );
        final IMemoryRegion region = getRegion( address );
        return region.read( address.minus( region.getAddressRange().getStartAddress() ) );
    }
    
    private IMemoryRegion getRegion(Address address) 
    {
        for ( IMemoryRegion r : regions ) 
        {
            if ( r.getAddressRange().contains( address ) ) {
                return r;
            }
        }
        
        // address not mapped...
        
        System.err.println( "ERROR: Access to unmapped address "+address);
        System.err.println( "\nMemory layout:\n\n");
        for ( IMemoryRegion r : regions ) 
        {
            System.err.println( r );
        }
        throw new RuntimeException("Address not mapped: "+address);
    }

    @Override
    public int read(Address adr)
    {
        final WordAddress address = adr.toWordAddress();
        final IMemoryRegion region = getRegion( address );
        return region.read( address.minus( region.getAddressRange().getStartAddress() ) );
    }

    @Override
    public void write(int wordAddress, int value)
    {
        final WordAddress address = Address.wordAddress( wordAddress );
        final IMemoryRegion region = getRegion( address );
        region.write( address.minus( region.getAddressRange().getStartAddress() ) , value );           
    }

    @Override
    public void write(Address adr, int value)
    {
        final WordAddress address = adr.toWordAddress();
        final IMemoryRegion region = getRegion( address );
        region.write( address.minus( region.getAddressRange().getStartAddress() ) , value );        
    }

    @Override
    public Size getSize()
    {
        Size result = Size.sizeInBytes( 0 );
        for ( IMemory r : regions ) {
            result = result.plus( r.getSize() );
        }        
        return result;
    }

}
