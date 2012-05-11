package de.codesourcery.jasm16;

/**
 * A memory/ data volume size.
 * 
 * <p>This class provides an abstraction to avoid conversion errors when converting between sizes in bytes and words.</p>
 * 
 * @author tobias.gierke@voipfuture.com
 */
public abstract class Size
{
    private final int value;
    
    public static SizeInBytes sizeInBytes(int size) {
        return new SizeInBytes( size );
    }
    
    public static SizeInWords sizeInWords(int size) {
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
    
    public Size minus(Size other) {
        return new SizeInBytes( this.toSizeInBytes().getValue() - other.toSizeInBytes().getValue() );
    }
    
    public Size plus(Size other) {
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
}
