package de.codesourcery.jasm16.emulator;

public interface IEmulationOptionsProvider {

    public EmulationOptions getEmulationOptions();
    
    public void setEmulationOptions(EmulationOptions emulationOptions); 
}
