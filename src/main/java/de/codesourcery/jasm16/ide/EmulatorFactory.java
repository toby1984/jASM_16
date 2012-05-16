package de.codesourcery.jasm16.ide;

import de.codesourcery.jasm16.emulator.Emulator;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultClock;

public class EmulatorFactory
{
    public Emulator createEmulator() {
        final Emulator result = new Emulator();
        result.addDevice( new DefaultClock() );
        return result;
    }
}
