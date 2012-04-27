package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

public interface IMemory
{
    public int readWord(Address address);
    
    public void bulkLoad(Address startingOffset, byte[] data);

    public byte[] getBytes(Address startAddress, int lengthInBytes);    
}
