package de.codesourcery.jasm16.emulator.exceptions;

import de.codesourcery.jasm16.emulator.devices.IDevice;

public final class DeviceErrorException extends EmulationErrorException {

    private final IDevice device;
    public DeviceErrorException(String message,IDevice device)
    {
        super(message);
        this.device = device;
    }

    public IDevice getDevice() {
        return device;
    }
}   