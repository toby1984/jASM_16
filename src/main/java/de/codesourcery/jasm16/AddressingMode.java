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
 * Enumeration of supported DCPU-16 addressing modes.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public enum AddressingMode
{
    /**
     * SET a
     */
    REGISTER, 
    /**
     * SET [a] , 10
     */
    INDIRECT_REGISTER, 
    /**
     * SET [SP++] , 10
     */
    INDIRECT_REGISTER_POSTINCREMENT, 
    /**
     * SET a , [--SP]
     */
    INDIRECT_REGISTER_PREDECREMENT,
    /**
     * SET [0x2000+a] , 10
     */
    INDIRECT_REGISTER_OFFSET,
    /**
     * SET  [0x2000]
     */
    INDIRECT,  
    /**
     * SET a, 10
     */
    IMMEDIATE, 
    /**
     * INTERNAL USE ONLY.
     */
    INTERNAL_EXPRESSION; 
}
