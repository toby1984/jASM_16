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

import java.io.File;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import de.codesourcery.jasm16.emulator.IEmulator.EmulationSpeed;
import de.codesourcery.jasm16.emulator.ILogger.LogLevel;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultClock;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultFloppyDrive;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultKeyboard;
import de.codesourcery.jasm16.emulator.devices.impl.DefaultScreen;
import de.codesourcery.jasm16.emulator.devices.impl.FileBasedFloppyDisk;

/**
 * Emulation options.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class EmulationOptions {

    private static final EmulationSpeed DEFAULT_EMULATION_SPEED = EmulationSpeed.REAL_SPEED;
    
    /* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * Adjust the following locations when
     * adding/removing configuration options:
     * 
     * - copy constructor !!
     * - loadEmulationOptions()
     * - saveEmulationOptions()
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
	private boolean enableDebugOutput = false;
    private boolean memoryProtectionEnabled = false;
    private boolean ignoreAccessToUnknownDevices = false;
    private boolean useLegacyKeyboardBuffer = false;
    private boolean mapVideoRamUponAddDevice = true;
    private boolean mapFontRamUponAddDevice = false;
    private boolean runFloppyAtFullSpeed = false;
    private EmulationSpeed emulationSpeed = DEFAULT_EMULATION_SPEED;

    private InsertedDisk insertedDisk;

    private boolean newEmulatorInstanceRequired = false;

    public static final class InsertedDisk 
    {
        private final File file;
        private final boolean writeProtected;

        public InsertedDisk(File file, boolean writeProtected)
        {
            this.file = file;
            this.writeProtected = writeProtected;
        }

        public File getFile()
        {
            return file;
        }

        public boolean isWriteProtected()
        {
            return writeProtected;
        }
    }

    public EmulationOptions() {
    }
    
    public EmulationSpeed getEmulationSpeed() {
		return emulationSpeed;
	}
    
    public void setEmulationSpeed(EmulationSpeed emulationSpeed) {
    	if (emulationSpeed == null) {
			throw new IllegalArgumentException("emulationSpeed must not be null");
		}
		this.emulationSpeed = emulationSpeed;
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
        this.mapVideoRamUponAddDevice      = other.mapVideoRamUponAddDevice;
        this.runFloppyAtFullSpeed         = other.runFloppyAtFullSpeed;
        this.newEmulatorInstanceRequired  = other.newEmulatorInstanceRequired;
        this.insertedDisk                 = other.insertedDisk;
        this.emulationSpeed               = other.emulationSpeed;
    }

    public InsertedDisk getInsertedDisk()
    {
        return insertedDisk;
    }

    public void setInsertedDisk(InsertedDisk disk)
    {
        this.insertedDisk = disk;
    }

    public void setRunFloppyAtFullSpeed(boolean runFloppyAtFullSpeed) {
        this.runFloppyAtFullSpeed = runFloppyAtFullSpeed;
    }

    public boolean isRunFloppyAtFullSpeed() {
        return runFloppyAtFullSpeed;
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

        if ( enableDebugOutput ) {
            outLogger.setLogLevel( LogLevel.DEBUG );
        }
        emulator.setOutput( outLogger );
        emulator.setMemoryProtectionEnabled( memoryProtectionEnabled );
        emulator.setIgnoreAccessToUnknownDevices( ignoreAccessToUnknownDevices );
        emulator.setEmulationSpeed( emulationSpeed );
        
        try {
            final DefaultFloppyDrive drive = getFloppyDrive( emulator );
            drive.setRunAtMaxSpeed( runFloppyAtFullSpeed );
            insertDisk( drive );             
        } 
        catch(NoSuchElementException e) {
            // ok , no floppy attached
        }
    }

    public void saveEmulationOptions(Element element,Document document) {

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
        if ( isRunFloppyAtFullSpeed() ) {
            element.setAttribute("runFloppyAtFullSpeed" , "true" );
        }	

        element.setAttribute( "emulationSpeed" , emulationSpeedToString( this.emulationSpeed ) );

        if ( getInsertedDisk() != null ) 
        {
            final Element disks = document.createElement("disks" );
            element.appendChild( disks);

            final Element disk = document.createElement("disk" );
            disks.appendChild( disk );

            disk.setAttribute("writeProtected" , getInsertedDisk().isWriteProtected() ? "true" : "false" );
            disk.setAttribute("file" , getInsertedDisk().getFile().getAbsolutePath() );
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
        result.setRunFloppyAtFullSpeed( isSet(element,"runFloppyAtFullSpeed" ) );	
        result.setEmulationSpeed( emulationSpeedFromString( element.getAttribute("emulationSpeed") ) );

        Element disks = getChildElement( element , "disks");
        if ( disks != null )
        {
            Element disk = getChildElement(element,"disk" );
            if ( disk != null ) {
                final boolean writeProtected = isSet( disk , "writeProtected" );
                final File file = new File( disk.getAttribute( "file" ) );
                result.setInsertedDisk( new InsertedDisk(file,writeProtected ) );
            }
        } 
        return result;
    }	
    
    private static String emulationSpeedToString(EmulationSpeed speed) {
    	switch( speed ) {
    		case MAX_SPEED:
    			return "max";
    		case REAL_SPEED:
    			return "real";
    		default:
    			throw new RuntimeException("Unhandled speed: "+speed);
    	}
    }
    
    private static EmulationSpeed emulationSpeedFromString(String s) 
    {
    	if ( StringUtils.isBlank(s ) ) 
    	{
    		return DEFAULT_EMULATION_SPEED;
    	}
    	
    	switch( s ) {
    		case "max":
    			return EmulationSpeed.MAX_SPEED;
    		case "real":
    			return EmulationSpeed.REAL_SPEED;
    		default:
    			throw new RuntimeException("Unhandled speed: '"+s+"'");
    	}
    }      

    private static Element getChildElement(Element parent,String tagName) 
    {
        final NodeList nodeList = parent.getElementsByTagName( tagName );
        if ( nodeList.getLength() == 1 ) 
        {
            return (Element) nodeList.item(0);
        } 
        if ( nodeList.getLength() > 1 ) {
            throw new RuntimeException("Parse error, more than one <disks/> node in file?");
        }
        return null;
    }

    private static boolean isSet(Element element,String attribute) {
        final String value = element.getAttribute(attribute);
        return "true".equals( value );
    }

    public Emulator createEmulator() 
    {
        final Emulator result = new Emulator();

        result.addDevice( new DefaultClock() );
        result.addDevice( new DefaultKeyboard( useLegacyKeyboardBuffer ) );
        result.addDevice( new DefaultScreen( mapVideoRamUponAddDevice , mapFontRamUponAddDevice ) );
        result.addDevice( new DefaultFloppyDrive( runFloppyAtFullSpeed ) );
        
        apply( result );
        
        newEmulatorInstanceRequired = false;
        return result;
    } 
    
    private void insertDisk(DefaultFloppyDrive diskDrive) 
    {
        final InsertedDisk disk = getInsertedDisk();
        
        if ( disk != null ) {
            diskDrive.setDisk( new FileBasedFloppyDisk( disk.getFile() , disk.isWriteProtected() ) );
        } else {
            diskDrive.eject();
        }
    }

    public DefaultFloppyDrive getFloppyDrive(IEmulator emulator) 
    {
        List<IDevice> result = emulator.getDevicesByDescriptor( DefaultFloppyDrive.DESC );
        if ( result.isEmpty() ) {
            throw new NoSuchElementException("Internal error, found no floppy drive?");
        }
        if ( result.size() > 1 ) {
            throw new RuntimeException("Internal error, found more than one floppy drive?");
        }
        return (DefaultFloppyDrive) result.get(0);
    }    

    public DefaultScreen getScreen(IEmulator emulator) 
    {
        List<IDevice> result = emulator.getDevicesByDescriptor( DefaultScreen.DESC );
        if ( result.isEmpty() ) {
            throw new NoSuchElementException("Internal error, found no default screen?");
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
            throw new NoSuchElementException("Internal error, found no default keyboard ?");
        }
        if ( result.size() > 1 ) {
            throw new RuntimeException("Internal error, found more than one default keyboard ?");
        }
        return (DefaultKeyboard) result.get(0);    	
    }     
}