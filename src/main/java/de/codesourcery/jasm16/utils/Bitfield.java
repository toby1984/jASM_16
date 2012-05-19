package de.codesourcery.jasm16.utils;

import java.util.concurrent.atomic.AtomicIntegerArray;

public final class Bitfield {

	private static final int SHIFT_PER_ELEMENT=5; // = 32 bits per element 
	
	private final AtomicIntegerArray data; // 32 bits per element
	
	public Bitfield(int size) 
	{
		if ( size <= 0 ) {
			throw new IllegalArgumentException("Size must be > 0");
		}
		final int length = (int) Math.ceil( (float) size / ( 1 << SHIFT_PER_ELEMENT ) );
		data = new AtomicIntegerArray(length);
	}

	public void clear() 
	{
		final int len = data.length();
		for ( int i = 0 ; i < len ;i++) {
			data.set( i , 0 );
		}
	}
	
	public boolean isSet(int bit) {
		final int elementOffset = bit >> SHIFT_PER_ELEMENT;
		final int bitOffset = bit - ( elementOffset << SHIFT_PER_ELEMENT );	
		return (data.get(elementOffset) & (1 << bitOffset ) ) != 0;
	}
	
	public void setBit(int bit) {
		final int elementOffset = bit >> SHIFT_PER_ELEMENT; 
		final int bitOffset = bit - ( elementOffset << SHIFT_PER_ELEMENT );
		data.set(elementOffset , data.get(elementOffset) | ( 1 << bitOffset ) );
	}
	
	public void clearBit(int bit) {
		final int elementOffset = bit >> SHIFT_PER_ELEMENT;
		final int bitOffset = bit - ( elementOffset << SHIFT_PER_ELEMENT );
		data.set( elementOffset , data.get(elementOffset) & ~( 1 << bitOffset ) );
	}
	
}
