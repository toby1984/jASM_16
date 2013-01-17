package de.codesourcery.jasm16.emulator;

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

import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.emulator.devices.IInterrupt;

/**
 * DCPU-16 CPU.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IReadOnlyCPU
{
    public static final int COMMON_REGISTER_COUNT=8;
    
    public static final Register[] COMMON_REGISTERS = { Register.A, Register.B, Register.C, Register.X, Register.Y, Register.Z, Register.I, Register.J  };
    public static final String[] COMMON_REGISTER_NAMES = {"A","B","C","X","Y","Z","I","J"};
    
    public static final int REG_A=0;
    public static final int REG_B=1;
    public static final int REG_C=2;
    public static final int REG_X=3;
    public static final int REG_Y=4;
    public static final int REG_Z=5;
    public static final int REG_I=6;
    public static final int REG_J=7;
    
    public Address getPC();
    
    public Address getSP();
    
    public int getEX();
    
    public Address getInterruptAddress();
    
    public int getCurrentCycleCount();

    public int getRegisterValue(Register reg);
    
    public boolean isQueueInterrupts();
    
    public boolean interruptsEnabled();
    
    public List<IInterrupt> getInterruptQueue();
}