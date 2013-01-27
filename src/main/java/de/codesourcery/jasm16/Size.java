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
    
    public static final SizeInWords ZERO_WORDS = new SizeInWords(0);    
    public static final SizeInWords ONE_WORD = new SizeInWords(1);
    public static final SizeInWords TWO_WORDS = new SizeInWords(2);
    public static final SizeInWords THREE_WORDS = new SizeInWords(3);
    
    public static final SizeInBytes ZERO_BYTES = new SizeInBytes(0);    
    public static final SizeInBytes ONE_BYTES = new SizeInBytes(1);
    public static final SizeInBytes TWO_BYTES = new SizeInBytes(2);
    public static final SizeInBytes THREE_BYTES = new SizeInBytes(3);  
    public static final SizeInBytes FOUR_BYTES = new SizeInBytes(4);
    public static final SizeInBytes FIVE_BYTES = new SizeInBytes(5);
    public static final SizeInBytes SIX_BYTES = new SizeInBytes(6);      
    
    public static SizeInBytes bytes(int size) 
    {
    	switch( size ) {
    	case 0:
    		return ZERO_BYTES;
    	case 1:
    		return ONE_BYTES;
    	case 2:
    		return TWO_BYTES;
    	case 3:
    		return THREE_BYTES;
    	case 4:
    		return FOUR_BYTES;
    	case 5:
    		return FIVE_BYTES;
    	case 6:
    		return SIX_BYTES;
    	default:
    		return new SizeInBytes( size );
    	}
    }
    
    public static SizeInWords words(int size) 
    {
    	switch(size) {
    	case 0:
    		return ZERO_WORDS;
    	case 1:
    		return ONE_WORD;
    	case 2:
    		return TWO_WORDS;
    	case 3:
    		return THREE_WORDS;
    	default:
    		return new SizeInWords( size );
    	}
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
    
    public abstract int getSizeInBytes();
    
    public abstract int getSizeInWords();    
    
    @Override
    public final boolean equals(Object obj) 
    {
    	if ( obj == this ) {
    		return true;
    	}
    	if ( obj instanceof Size) {
    	    return getSizeInBytes() == ((Size) obj).getSizeInBytes();
    	}
    	return false;
    }
    
    public abstract Size multiply(int factor);
    
    @Override
    public final int hashCode() {
    	return getSizeInBytes();
    }
    
    public final Size minus(Size other) {
        return new SizeInBytes( this.getSizeInBytes() - other.getSizeInBytes() );
    }
    
    public final Size plus(Size other) {
        return new SizeInBytes( this.getSizeInBytes() + other.getSizeInBytes() );
    }    
    
    public abstract SizeInBytes toSizeInBytes();
    
    public abstract SizeInWords toSizeInWords();
    
    public static final class SizeInBytes extends Size {
    
        protected SizeInBytes(int size) {
            super(size);
        }
        
        public int getSizeInBytes() {
            return getValue();
        }
        
        public int getSizeInWords() {
            int result = getValue() >>> 1;
            if ( (result << 1) != getValue() ) {
                throw new RuntimeException("Internal error, converting byte-size "+getValue()+" to words causes loss of precision");
            }
            return result;
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

		@Override
		public Size multiply(int factor) 
		{
			if ( factor < 0 ) {
				throw new IllegalArgumentException("Factor must be positive");
			}
			return Size.bytes( getSizeInBytes() * factor );
		}         
    }
    
    public static final class SizeInWords extends Size 
    {
        protected SizeInWords(int size) {
            super(size);
        }
        
        public int getSizeInBytes() {
            return getValue() << 1;
        }
        
        public int getSizeInWords() {
            return getValue();
        }        

        @Override
        public SizeInBytes toSizeInBytes()
        {
            return new SizeInBytes( getValue() << 1);
        }

		@Override
		public Size multiply(int factor) 
		{
			if ( factor < 0 ) {
				throw new IllegalArgumentException("Factor must be positive");
			}
			return Size.words( getSizeInWords() * factor );
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
		return getSizeInBytes() > availableSize.getSizeInBytes();
	}    
	
	public final boolean isLessThan(Size availableSize) 
	{
		return getSizeInBytes() < availableSize.getSizeInBytes();
	}
	
	@Override
	public final int compareTo(Size o) 
	{
		final int s1 = getSizeInBytes();
		final int s2 = o.getSizeInBytes();
		if ( s1 < s2 ) {
			return -1;
		}
		if ( s1 > s2 ) {
			return 1;
		}
		return 0;
	}
}