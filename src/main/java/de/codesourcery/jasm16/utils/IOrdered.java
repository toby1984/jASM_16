package de.codesourcery.jasm16.utils;

public interface IOrdered
{
    public enum Priority {
        HIGHEST(10),
        HIGH(9),
        DEFAULT(8),
        LESS(7),
        LEAST(6),
        DONT_CARE(5);
        
        private final int value;
        
        private Priority(int value)
        {
            this.value = value;
        }

        public final boolean isHigherThan(Priority other) {
                return this.value > other.value;
        }
    }
    public Priority getPriority();
}
