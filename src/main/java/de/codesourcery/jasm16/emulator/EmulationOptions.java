package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.emulator.devices.impl.DefaultClock;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultKeyboard;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultScreen;

/**
 * Emulation options.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class EmulationOptions {

	private boolean enableDebugOutput = false;
	private boolean memoryProtectionEnabled = false;
	private boolean ignoreAccessToUnknownDevices = false;
	private boolean useLegacyKeyboardBuffer = false;
	private boolean mapVideoRamUponAddDevice = true;
	private boolean mapFontRamUponAddDevice = false;
	
	private boolean newEmulatorInstanceRequired = true;

	public boolean isMemoryProtectionEnabled() {
		return memoryProtectionEnabled;
	}

	public void setMemoryProtectionEnabled(boolean memoryProtectionEnabled) {
		this.memoryProtectionEnabled = memoryProtectionEnabled;
	}

	public boolean isIgnoreAccessToUnknownDevices() {
		return ignoreAccessToUnknownDevices;
	}

	public void setIgnoreAccessToUnknownDevices(boolean ignoreAccessToUnknownDevices) {
		this.ignoreAccessToUnknownDevices = ignoreAccessToUnknownDevices;
	}

	public boolean isUseLegacyKeyboardBuffer() {
		return useLegacyKeyboardBuffer;
	}

	public void setUseLegacyKeyboardBuffer(boolean useLegacyKeyboardBuffer) {
		newEmulatorInstanceRequired = this.useLegacyKeyboardBuffer != useLegacyKeyboardBuffer;
		this.useLegacyKeyboardBuffer = useLegacyKeyboardBuffer;
	}

	public boolean isMapVideoRamUponAddDevice() {
		return mapVideoRamUponAddDevice;
	}

	public void setMapVideoRamUponAddDevice(boolean mapVideoRamUponAddDevice) {
		newEmulatorInstanceRequired = this.mapVideoRamUponAddDevice != mapVideoRamUponAddDevice;
		this.mapVideoRamUponAddDevice = mapVideoRamUponAddDevice;
	}

	public boolean isMapFontRamUponAddDevice() {
		return mapFontRamUponAddDevice;
	}

	public void setMapFontRamUponAddDevice(boolean mapFontRamUponAddDevice) {
		this.newEmulatorInstanceRequired = this.mapFontRamUponAddDevice != mapFontRamUponAddDevice;
		this.mapFontRamUponAddDevice = mapFontRamUponAddDevice;
	}
	
	public boolean isNewEmulatorInstanceRequired() {
		return newEmulatorInstanceRequired;
	}
	
	public void apply(IEmulator result) 
	{
        final ILogger outLogger = new PrintStreamLogger( System.out );
        
        outLogger.setDebugEnabled( enableDebugOutput );
		result.setOutput( outLogger );
		
        result.setMemoryProtectionEnabled( false );
        result.setIgnoreAccessToUnknownDevices( false );
	}
	
    public Emulator createEmulator() 
    {
        final Emulator result = new Emulator();

        apply( result );
        
        result.addDevice( new DefaultClock() );
        result.addDevice( new DefaultKeyboard( useLegacyKeyboardBuffer ) );
        result.addDevice( new DefaultScreen( mapVideoRamUponAddDevice , mapFontRamUponAddDevice ) );
        newEmulatorInstanceRequired = false;
        return result;
    } 		
}