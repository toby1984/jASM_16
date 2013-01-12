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
package de.codesourcery.jasm16.emulator.devices;

import de.codesourcery.jasm16.utils.Misc;

/**
 * Holds information that uniquely identifies a hardware device (id,version,manufacturer etc.).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class DeviceDescriptor {

    private final String name;
    private final String description;
	private final long id;
	private final int version;
	private final long manufacturer;
	
	/**
     * Create instance.
     *  
	 * @param name informal name
	 * @param description informal description
	 * @param hardwareId 32-bit hardware ID
	 * @param hardwareVersion 16-bit version number
	 * @param manufacturer 32-bit hardware ID
	 */
	public DeviceDescriptor(String name,String description,long hardwareId,int hardwareVersion,long manufacturer) {
		this.id = hardwareId;
		this.version = hardwareVersion;
		this.manufacturer = manufacturer;
		this.name = name;
		this.description = description;
	}
	
	/**
	 * Compares two <code>DeviceDescriptor</code>s by
	 * hardware ID,version and manufacturer ID.
	 * 
	 * @param other
	 * @return <code>true</code> if both descriptors have the same hardware IDs,versions and manufacturer IDs.
	 */
	public boolean matches(DeviceDescriptor other) {
		return this.id == other.id &&
				this.version == other.version &&
				this.manufacturer == other.manufacturer;
	}
	
	/**
	 * Returns an informational name for this device, suitable for
	 * display to the user.
	 * 
	 * @return
	 */
	public String getName()
    {
        return name;
    }
	
    /**
     * Returns an informational description of this device, suitable for
     * display to the user.
     * 
     * @return
     */	
	public String getDescription()
    {
        return description;
    }
	
	/**
	 * Returns this devices hardware ID.
	 * 
	 * @return 32-bit value (only the lower 32 bits are used) 
	 */
	public long getID() {
		return id;
	}
	
	/**
	 * Returns this devices hardware version.
	 * 
	 * @return 16-bit value (only the lower 16 bits are used)
	 */
	public int getVersion() {
		return this.version;
	}
	
	/**
	 * Returns this devices manufacturer ID.
	 * 
	 * @return 32-bit value (only the lower 32 bits are used) 
	 */
	public long getManufacturer() {
		return this.manufacturer;
	}
	
	public String toString() {
		return "Hardware("+toString("",false)+")"; 
	}
	
	public String toString(String linePrefix,boolean appendNewLines) 
	{
		final String eol = appendNewLines ? "\n" : " , ";
		return linePrefix+format( "name" , this.name )+eol+
		       linePrefix+format( "ID" , Misc.toHexString( this.id ) )+eol+
			   linePrefix+format( "Version" , Misc.toHexString( this.version ) )+eol+
		       linePrefix+format( "Manufacturer" , Misc.toHexString( this.manufacturer) );		
	}
	
	private static String format(String key,String value) {
		final String col1 = Misc.padRight( key , 12 );
		final String col2 = Misc.padLeft( value == null ? "<NULL>" : value  , 12 );
		return col1+": "+col2;
	}
}
