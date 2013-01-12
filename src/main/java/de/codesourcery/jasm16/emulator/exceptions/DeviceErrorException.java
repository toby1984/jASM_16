package de.codesourcery.jasm16.emulator.exceptions;

import de.codesourcery.jasm16.emulator.devices.IDevice;

public final class DeviceErrorException extends EmulationErrorException {

    private final IDevice device;
    
    public DeviceErrorException(String message,IDevice device)
    {
    	this(message,device,null);
    }
    
    public DeviceErrorException(String message,IDevice device,Throwable t)
    {
        super(message,t);
        this.device = device;
    }    

    public IDevice getDevice() {
        return device;
    }
}   