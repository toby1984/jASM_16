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
 * Enumeration of all DCPU-16 registers.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public enum Register
{
    /*
* 8 registers (A, B, C, X, Y, Z, I, J)
* program counter (PC)
* stack pointer (SP)
* overflow (O)     
* extra/excess (EX)
     */
    A("a"),
    B("b"),
    C("c"),
    X("X"),
    Y("Y"),
    Z("Z"),
    I("I"),
    J("J"),    
    SP("SP") {
        public boolean supportsIndirectWithPostIncrement() {
        	return true;
        }
        
        public boolean supportsIndirectWithPreDecrement() {
        	return true;
        }    	
    },
    PC("PC"),
    EX("EX");
    
    private final String identifier;
    
    private Register(String identifier) 
    {
        this.identifier = identifier;
    }
    
    public boolean supportsIndirectWithPostIncrement() {
    	return false;
    }
    
    public boolean supportsIndirectWithPreDecrement() {
    	return false;
    }    
    
    /**
     * Resolves a string identifier to a {@link Register} instance.
     * 
     * @param s
     * @return register or <code>null</code> if the input did not resemble a register identifier
     * @see {@link #isRegisterIdentifier(String)}
     */
    public static Register fromString(String s)  {
        for ( Register r : values() ) {
            if ( r.identifier.toLowerCase().equals( s ) ||
                 r.identifier.toUpperCase().equals( s ) ) {
                return r;
            }
        }
        return null;
    }
    
    /**
     * Check whether a string is a valid register identifier.
     * @param s
     * @return
     * @throws IllegalArgumentException
     * @see {@link #fromString(String)}
     */
    public static boolean isRegisterIdentifier(String s) throws IllegalArgumentException 
    {
        return fromString(s) != null;
    }    
}
