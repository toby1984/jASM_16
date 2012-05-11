package de.codesourcery.jasm16.emulator;

import java.util.List;

import de.codesourcery.jasm16.AddressRange;

/**
 * A memory region.
 * 
 * @author tobias.gierke@voipfuture.com
 */
public interface IMemoryRegion extends IMemory
{
    /**
     * The address range covered by this memory region.
     * 
     * @return
     */
    public AddressRange getAddressRange();
    
    /**
     * Returns an informal name for this memory region.
     * 
     * @return
     */
    public String getRegionName();
    
    /**
     * Removes a specific address range from the address range
     * currently covered by this region.
     * 
     * <p>
     * Note that this method does NOT modify this memory region but
     * instead returns COPIES of this region.
     * </p>  
     * @param gap
     * @return 
     * @throws IllegalArgumentException if gap is <code>null</code> or does not intersect with this memory region
     */
    public List<IMemoryRegion> subtract(AddressRange gap) throws IllegalArgumentException;
}
