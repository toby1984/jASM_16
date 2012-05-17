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
package de.codesourcery.jasm16;

/**
 * A memory/ data volume size.
 * 
 * <p>This class provides an abstraction to avoid conversion errors when converting between sizes in bytes and words.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class Size implements Comparable<Size>
{
    private final int value;
    
    public static SizeInBytes bytes(int size) {
        return new SizeInBytes( size );
    }
    
    public static SizeInWords words(int size) {
        return new SizeInWords( size );
    }    
    
    protected Size(int value) 
    {
        if ( value < 0 ) {
            throw new IllegalArgumentException("size must not be negative (size: "+value+")");
        }
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    @Override
    public final boolean equals(Object obj) 
    {
    	if ( obj == this ) {
    		return true;
    	}
    	if ( obj instanceof Size) {
    		return toSizeInBytes().getValue() == ((Size) obj).toSizeInBytes().getValue();
    	}
    	return false;
    }
    
    @Override
    public final int hashCode() {
    	return toSizeInBytes().getValue();
    }
    
    public final Size minus(Size other) {
        return new SizeInBytes( this.toSizeInBytes().getValue() - other.toSizeInBytes().getValue() );
    }
    
    public final Size plus(Size other) {
        return new SizeInBytes( this.toSizeInBytes().getValue() + other.toSizeInBytes().getValue() );
    }    
    
    public abstract SizeInBytes toSizeInBytes();
    
    public abstract SizeInWords toSizeInWords();
    
    public static final class SizeInBytes extends Size {
    
        protected SizeInBytes(int size) {
            super(size);
        }

        @Override
        public SizeInBytes toSizeInBytes()
        {
            return this;
        }

        @Override
        public SizeInWords toSizeInWords()
        {
            int convertedValue = getValue() >>> 1;
            if ( ( convertedValue << 1 ) != getValue() ) {
                throw new RuntimeException("Internal error, converting uneven value "+getValue()+" to a size in words would cause data loss");
            }
            return new SizeInWords( getValue() >>> 1);
        }
        
        @Override
        public String toString()
        {
            return getValue()+" bytes";
        }         
    }
    
    public static final class SizeInWords extends Size 
    {
        protected SizeInWords(int size) {
            super(size);
        }

        @Override
        public SizeInBytes toSizeInBytes()
        {
            return new SizeInBytes( getValue() << 1);
        }

        @Override
        public SizeInWords toSizeInWords()
        {
            return this;
        }
        
        @Override
        public String toString()
        {
            return getValue()+" words";
        } 
        
    }

	public final boolean isGreaterThan(Size availableSize) 
	{
		return toSizeInBytes().getValue() > availableSize.toSizeInBytes().getValue();
	}    
	
	public final boolean isLessThan(Size availableSize) 
	{
		return toSizeInBytes().getValue() < availableSize.toSizeInBytes().getValue();
	}
	
	@Override
	public final int compareTo(Size o) 
	{
		final int s1 = toSizeInBytes().getValue();
		final int s2 = o.toSizeInBytes().getValue();
		if ( s1 < s2 ) {
			return -1;
		}
		if ( s1 > s2 ) {
			return 1;
		}
		return 0;
	}
}