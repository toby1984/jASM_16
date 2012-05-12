package de.codesourcery.jasm16.emulator;

import java.util.NoSuchElementException;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.utils.IMemoryIterator;

public class MemoryIterator implements IMemoryIterator
{
	private final IReadOnlyMemory memory;
	private final boolean wrap;
	
    private Address current;
    private int wordsAvailable;

    public MemoryIterator(IReadOnlyMemory memory , Address startingAddress ,boolean wrap) 
    {
    	this(memory,startingAddress,memory.getSize() , wrap );
    }
    
    public MemoryIterator(IReadOnlyMemory memory , Address startingAddress ,Size size,boolean wrap) 
    {
    	if ( memory == null ) {
			throw new IllegalArgumentException("memory must not be null");
		}
    	if ( startingAddress == null ) {
			throw new IllegalArgumentException("startingAddress must not be null");
		}
    	this.memory = memory;
    	this.current = startingAddress.toWordAddress();
    	wordsAvailable = size.toSizeInWords().getValue();
    	this.wrap = wrap;
    }
    
    @Override
    public int nextWord()
    {
        if ( wordsAvailable <= 0 ) {
            throw new NoSuchElementException();
        }
        wordsAvailable--;
        final Address old = current;
        current = current.incrementByOne(wrap);
        return memory.read( old );
    }

    @Override
    public boolean hasNext()
    {
        return wordsAvailable > 0;
    }

    @Override
    public Address currentAddress()
    {
        return current;
    }
};