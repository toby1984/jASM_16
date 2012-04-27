package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

public interface IEmulator
{

    public abstract void reset(boolean clearMemory);

    public abstract void stop();

    public abstract void start();

    public abstract void loadMemory(Address startingOffset, byte[] data);

    public abstract void calibrate();
    
    public ICPU getCPU();
    
    public IMemory getMemory();
    
    public void addEmulationListener(IEmulationListener listener);
    
    public void removeEmulationListener(IEmulationListener listener);    
    
    public void executeOneInstruction();
    
    public void skipCurrentInstruction();

}