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
package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.utils.Misc;

public class DebugEmulationListener implements IEmulationListener
{

    @Override
    public void beforeExecution(IEmulator emulator)
    {
    }

    @Override
    public void afterExecution(IEmulator emulator, int commandDuration)
    {
        final ICPU cpu = emulator.getCPU();

        System.out.println("\n");
        int itemsInLine = 0;
        for ( int i = 0 ; i < ICPU.COMMON_REGISTER_NAMES.length ; i++ ) {
            System.out.print( ICPU.COMMON_REGISTER_NAMES[i]+": "+Misc.toHexString( cpu.getCommonRegisters()[i] )+"    ");
            itemsInLine++;
            if ( itemsInLine == 4 ) {
                itemsInLine = 0;
                System.out.println();
            }
        }
        System.out.println("\nPC: "+Misc.toHexString( cpu.getPC().getValue() ));
        System.out.println("EX: "+Misc.toHexString( cpu.getEX() ));
        System.out.println("IA: "+Misc.toHexString( cpu.getInterruptAddress() ));        
        System.out.println("SP: "+Misc.toHexString( cpu.getSP().getValue() ));
    }

    @Override
    public void afterReset(IEmulator emulator)
    {
    }

    @Override
    public void afterMemoryLoad(IEmulator emulator, Address startAddress, int lengthInBytes)
    {
    }

    @Override
    public void onBreakpoint(IEmulator emulator, BreakPoint breakpoint)
    {
    }

}
