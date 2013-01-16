/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
