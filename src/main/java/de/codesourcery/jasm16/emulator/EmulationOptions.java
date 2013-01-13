package de.codesourcery.jasm16.emulator;

import java.util.List;

import org.w3c.dom.Element;

import de.codesourcery.jasm16.emulator.devices.IDevice;
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
	
	private boolean newEmulatorInstanceRequired = false;

	public EmulationOptions() {
	}
	
	public void setEnableDebugOutput(boolean enableDebugOutput) {
		this.enableDebugOutput = enableDebugOutput;
	}
	
	public boolean isEnableDebugOutput() {
		return enableDebugOutput;
	}
	
	public EmulationOptions(EmulationOptions other) 
	{
		if (other == null) {
			throw new IllegalArgumentException("options must not be null");
		}
		this.enableDebugOutput            = other.enableDebugOutput;
		this.memoryProtectionEnabled      = other.memoryProtectionEnabled;
		this.ignoreAccessToUnknownDevices = other.ignoreAccessToUnknownDevices;
		this.useLegacyKeyboardBuffer      = other.useLegacyKeyboardBuffer;
		this.mapFontRamUponAddDevice      = other.mapFontRamUponAddDevice;
		this.mapFontRamUponAddDevice      = other.mapFontRamUponAddDevice;
		this.newEmulatorInstanceRequired  = other.newEmulatorInstanceRequired;
	}
	
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
		newEmulatorInstanceRequired |= this.useLegacyKeyboardBuffer != useLegacyKeyboardBuffer;
		this.useLegacyKeyboardBuffer = useLegacyKeyboardBuffer;
	}

	public boolean isMapVideoRamUponAddDevice() {
		return mapVideoRamUponAddDevice;
	}

	public void setMapVideoRamUponAddDevice(boolean mapVideoRamUponAddDevice) {
		newEmulatorInstanceRequired |= this.mapVideoRamUponAddDevice != mapVideoRamUponAddDevice;
		this.mapVideoRamUponAddDevice = mapVideoRamUponAddDevice;
	}

	public boolean isMapFontRamUponAddDevice() {
		return mapFontRamUponAddDevice;
	}

	public void setMapFontRamUponAddDevice(boolean mapFontRamUponAddDevice) {
		this.newEmulatorInstanceRequired |= this.mapFontRamUponAddDevice != mapFontRamUponAddDevice;
		this.mapFontRamUponAddDevice = mapFontRamUponAddDevice;
	}
	
	public boolean isNewEmulatorInstanceRequired() {
		return newEmulatorInstanceRequired;
	}
	
	public void apply(IEmulator emulator) 
	{
        final ILogger outLogger = new PrintStreamLogger( System.out );
        
        outLogger.setDebugEnabled( enableDebugOutput );
		emulator.setOutput( outLogger );
        emulator.setMemoryProtectionEnabled( memoryProtectionEnabled );
        emulator.setIgnoreAccessToUnknownDevices( ignoreAccessToUnknownDevices );
	}
	
	public void saveEmulationOptions(Element element) {
		
		if ( isEnableDebugOutput() ) {
			element.setAttribute("debug" , "true" );
		}
		if ( isIgnoreAccessToUnknownDevices() ) {
			element.setAttribute("ignoreAccessToUnknownDevices" , "true" );
		}
		if ( isMapFontRamUponAddDevice() ) {
			element.setAttribute("mapFontRamUponAddDevice" , "true" );
		}	
		if ( isMapVideoRamUponAddDevice() ) {
			element.setAttribute("mapVideoRamUponAddDevice" , "true" );
		}		
		if ( isMemoryProtectionEnabled() ) {
			element.setAttribute("memoryProtectionEnabled" , "true" );
		}		
		if ( isUseLegacyKeyboardBuffer() ) {
			element.setAttribute("useLegacyKeyboardBuffer" , "true" );
		}		
	}
	
	public static EmulationOptions loadEmulationOptions(Element element) 
	{
		final EmulationOptions result = new EmulationOptions();
		result.setEnableDebugOutput( isSet(element,"debug" ) );
		result.setIgnoreAccessToUnknownDevices( isSet(element,"ignoreAccessToUnknownDevices" ) );
		result.setMapFontRamUponAddDevice( isSet(element,"mapFontRamUponAddDevice" ) );
		result.setMapVideoRamUponAddDevice( isSet(element,"mapVideoRamUponAddDevice" ) );
		result.setMemoryProtectionEnabled( isSet(element,"memoryProtectionEnabled" ) );
		result.setUseLegacyKeyboardBuffer( isSet(element,"useLegacyKeyboardBuffer" ) );
		return result;
	}	
	
	private static boolean isSet(Element element,String attribute) {
		final String value = element.getAttribute(attribute);
		return "true".equals( value );
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
    
    public DefaultScreen getScreen(IEmulator emulator) 
    {
    	List<IDevice> result = emulator.getDevicesByDescriptor( DefaultScreen.DESC );
    	if ( result.isEmpty() ) {
    		throw new RuntimeException("Internal error, found no default screen?");
    	}
    	if ( result.size() > 1 ) {
    		throw new RuntimeException("Internal error, found more than one default screen?");
    	}
    	return (DefaultScreen) result.get(0);
    }
    
    public DefaultKeyboard getKeyboard(IEmulator emulator)
    {
    	List<IDevice> result = emulator.getDevicesByDescriptor( DefaultKeyboard.DESC );
    	if ( result.isEmpty() ) {
    		throw new RuntimeException("Internal error, found no default keyboard ?");
    	}
    	if ( result.size() > 1 ) {
    		throw new RuntimeException("Internal error, found more than one default keyboard ?");
    	}
    	return (DefaultKeyboard) result.get(0);    	
    }     
}