package de.codesourcery.jasm16.emulator.memory;

/**
 * Interface with constants of built-in memory types.
 * 
 * <p>I intentionally do not use an enum here since enums are 
 * not extensible and thus new device implementations could
 * not define their own memory types.</p>
 *
 * @author tobias.gierke@voipfuture.com
 */
public interface IMemoryTypes {

	public static final long TYPE_RAM = 0x12345678;
	
	// video emulation
	public static final long TYPE_VRAM = 0x12345679;
	public static final long TYPE_FONT_RAM = 0x1234567a;
	public static final long TYPE_PALETTE_RAM = 0x1234567b;
	
	// keyboard buffer
	public static final long TYPE_KEYBOARD_BUFFER = 0x1234567c;	
	
}
