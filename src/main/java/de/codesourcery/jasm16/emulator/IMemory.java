package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

/**
 * Memory that is both readable and writeable.
 * 
 * <p>Implementations need to be THREAD-SAFE.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IMemory extends IReadOnlyMemory {
    
    public void clear();
    
    /**
     * 
     * @param wordAddress
     * @param value
     * @deprecated This method only exists for performance reasons in tight loops (so no intermediate {@link Address} objects need to be created) , try to use
     * {@link #write(Address,int)} wherever possible to avoid conversion errors between byte- and word-sized addresses.    
     */
    public void write(int wordAddress,int value);
    
    public void write(Address address,int value);
    
}