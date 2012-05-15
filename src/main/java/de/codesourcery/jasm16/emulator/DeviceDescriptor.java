package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.utils.Misc;

public class DeviceDescriptor {

	private final long id;
	private final int version;
	private final long manufacturer;
	
	public DeviceDescriptor(long hardwareId,int hardwareVersion,long manufacturer) {
		this.id = hardwareId;
		this.version = hardwareVersion;
		this.manufacturer = manufacturer;
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
		return linePrefix+format( "ID" , Misc.toHexString( this.id ) )+eol+
			   linePrefix+format( "Version" , Misc.toHexString( this.version ) )+eol+
		       linePrefix+format( "Manufacturer" , Misc.toHexString( this.manufacturer) );		
	}
	
	private static String format(String key,String value) {
		final String col1 = Misc.padRight( key , 12 );
		final String col2 = Misc.padLeft( value  , 12 );
		return col1+": "+col2;
	}
}
