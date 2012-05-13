package de.codesourcery.jasm16.emulator.devices;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.emulator.HardwareInterrupt;
import de.codesourcery.jasm16.emulator.IDevice;
import de.codesourcery.jasm16.emulator.IEmulator;

public class DefaultKeyboard implements IDevice {

	private final Component inputComponent;

	private final Object BUFFER_LOCK = new Object();

	// @GuardedBy( BUFFER_LOCK )
	private final List<Integer> keysTyped = new ArrayList<Integer>();

	// @GuardedBy( BUFFER_LOCK )
	private final List<Integer> keysPressed = new ArrayList<Integer>();	

	private volatile IEmulator emulator;
	private volatile Integer interruptMessage = null; 

	private final KeyListener keyListener = new KeyListener() {

		@Override
		public void keyTyped(KeyEvent e) 
		{
			synchronized( BUFFER_LOCK ) {
				keysTyped.add( e.getKeyCode() );
			}	
			
			if ( interruptMessage != null ) {
				emulator.triggerInterrupt( new HardwareInterrupt( DefaultKeyboard.this , interruptMessage ) );
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
			int keyCode = e.getKeyCode();
			synchronized(BUFFER_LOCK ) {
				keysPressed.remove( keyCode );
			}
		}

		@Override
		public void keyPressed(KeyEvent e) {
			synchronized( BUFFER_LOCK ) {
				keysPressed.add( e.getKeyCode() );
			}				
		}
	};

	public DefaultKeyboard(Component inputComponent) {
		if (inputComponent == null) {
			throw new IllegalArgumentException("inputComponent must not be null");
		}
		this.inputComponent = inputComponent;
	}

	private void clearKeyboardBuffers() {
		synchronized ( BUFFER_LOCK ) {
			keysPressed.clear();
			keysTyped.clear();
		}
	}

	private Integer readTypedKey() 
	{
		synchronized (BUFFER_LOCK) {
			if ( keysTyped.isEmpty() ) {
				return null;
			}
			return keysTyped.remove(0);
		}
	}

	private boolean isKeyPressed(int keyCode) 
	{
		synchronized (BUFFER_LOCK) 
		{
			return keysPressed.contains( keyCode );
		}
	}	

	/*
	 * Name: Generic Keyboard (compatible)
	 * ID: 0x30cf7406
	 * Version: 1
	 * 
	 * Interrupts do different things depending on contents of the A register:
	 * 
	 *  A | BEHAVIOR
	 * ---+----------------------------------------------------------------------------
	 *  0 | Clear keyboard buffer
	 *  1 | Store next key typed in C register, or 0 if the buffer is empty
	 *  2 | Set C register to 1 if the key specified by the B register is pressed, or
	 *    | 0 if it's not pressed
	 *  3 | If register B is non-zero, turn on interrupts with message B. If B is zero,
	 *    | disable interrupts
	 * ---+----------------------------------------------------------------------------
	 * 
	 * When interrupts are enabled, the keyboard will trigger an interrupt when one or
	 * more keys have been pressed, released, or typed.
	 * 
	 * Key numbers are:
	 * 	0x10: Backspace
	 * 	0x11: Return
	 * 	0x12: Insert
	 * 	0x13: Delete
	 * 	0x20-0x7f: ASCII characters
	 * 	0x80: Arrow up
	 * 	0x81: Arrow down
	 * 	0x82: Arrow left
	 * 	0x83: Arrow right
	 * 	0x90: Shift
	 * 	0x91: Control	 
	 */
	@Override
	public void afterAddDevice(IEmulator emulator) 
	{
		if ( this.emulator != null ) {
			throw new IllegalStateException("Device "+this+" is already added to emulator "+this.emulator);
		}
		this.emulator = emulator;
		inputComponent.addKeyListener( keyListener );
	}

	@Override
	public void beforeRemoveDevice(IEmulator emulator) {
		inputComponent.removeKeyListener( keyListener );
		this.emulator = null;
	}

	@Override
	public long getHardwareID() {
		return 0x30cf7406;
	}

	@Override
	public int getHardwareVersion() {
		return 1;
	}

	@Override
	public long getManufacturer() {
		return 0;
	}

	@Override
	public void handleInterrupt(IEmulator emulator) 
	{
		final int value = emulator.getCPU().getRegisterValue( Register.A );

		/*
		 * Interrupts do different things depending on contents of the A register:
		 * 
		 *  A | BEHAVIOR
		 * ---+----------------------------------------------------------------------------
		 *  0 | Clear keyboard buffer
		 *  1 | Store next key typed in C register, or 0 if the buffer is empty
		 *  2 | Set C register to 1 if the key specified by the B register is pressed, or
		 *    | 0 if it's not pressed
		 *  3 | If register B is non-zero, turn on interrupts with message B. If B is zero,
		 *    | disable interrupts
		 * ---+----------------------------------------------------------------------------		 
		 */

		switch( value ) {
		case 0:
			clearKeyboardBuffers();
			return;
		case 1:
			Integer keyCode = readTypedKey();
			if ( keyCode != null ) {
				emulator.getCPU().setRegisterValue( Register.C , keyCode.intValue() );
			} else {
				emulator.getCPU().setRegisterValue( Register.C , 0 );
			}
			return;
		case 2:
			final int key = emulator.getCPU().getRegisterValue( Register.B );
			emulator.getCPU().setRegisterValue( Register.C , isKeyPressed( key ) ? 1 : 0 );
			return;
		case 3:
			final int irqMsg = emulator.getCPU().getRegisterValue( Register.B );
			interruptMessage = irqMsg != 0 ? irqMsg : null;
			return;
		default:
			return;
		}
	}

}