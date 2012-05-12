package de.codesourcery.jasm16.utils;

import de.codesourcery.jasm16.Address;

public interface IMemoryIterator {

    public Address currentAddress();
    
    public int nextWord();

    public boolean hasNext();
}