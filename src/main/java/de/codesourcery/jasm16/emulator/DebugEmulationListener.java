package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.utils.Misc;

public class DebugEmulationListener implements IEmulationListener
{

    @Override
    public void beforeExecution(Emulator emulator)
    {

    }

    @Override
    public void afterExecution(Emulator emulator, int commandDuration)
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
    public void onReset(Emulator emulator)
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onMemoryLoad(Emulator emulator, Address startAddress, int lengthInBytes)
    {
        // TODO Auto-generated method stub
        
    }

}
