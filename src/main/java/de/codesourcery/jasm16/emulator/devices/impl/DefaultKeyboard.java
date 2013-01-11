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
package de.codesourcery.jasm16.emulator.devices.impl;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.emulator.EmulationListener;
import de.codesourcery.jasm16.emulator.IEmulationListener;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.HardwareInterrupt;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.memory.IMemory;
import de.codesourcery.jasm16.emulator.memory.MemoryRegion;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Default keyboard device.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class DefaultKeyboard implements IDevice {

	private static final Logger LOG = Logger.getLogger(DefaultKeyboard.class);
	
	private volatile Component inputComponent;

	private final Object BUFFER_LOCK = new Object();

	// @GuardedBy( BUFFER_LOCK )
	private final List<Integer> keysTyped = new ArrayList<Integer>();

	// @GuardedBy( BUFFER_LOCK )
	private final List<Integer> keysPressed = new ArrayList<Integer>();	

	private volatile IEmulator emulator;
	private final boolean useLegacyMemoryBuffer; // use keyboard buffer at 0x9000
	private volatile LegacyKeyboardBuffer legacyKeyboardBuffer;
	private volatile Integer interruptMessage = null; 
	
	private volatile boolean receivedAtLeastOneInterrupt = false;
	
	private final DeviceDescriptor desc = new DeviceDescriptor( "keyboard" , "default keyboard", 0x30cf7406 , 0x01 , Constants.JASM16_MANUFACTURER );	

	private final AtomicBoolean emulationRunning = new AtomicBoolean(false);
	
	private final IEmulationListener myEmulationListener = new EmulationListener() {
	    
	    public boolean requiresExplicitRemoval() {
	        return true;
	    }
	    
	    protected void beforeContinuousExecutionHook() {
	        emulationRunning.set( true );
	    }
	    
	    public void onStopHook(IEmulator emulator, Address previousPC, Throwable emulationError) {
	        emulationRunning.set( false);
	    }
	};
	
	protected final class LegacyKeyboardBuffer extends MemoryRegion {

        public LegacyKeyboardBuffer(Address range)
        {
            super("keyboard buffer (legacy)", new AddressRange( range , Size.words( 1 ) ) , false );
        }
	
        public void writeKeyEvent(int keyCode) 
        {
        	final IMemory mem = emulator.getMemory();
        	final Address address = getStartAddress();
        	
        	if ( mem.read( address ) == 0 ) {
        		mem.write(  address , keyCode );
        	}
        }
        
        private Address getStartAddress() {
        	return getAddressRange().getStartAddress();        	
        }
        
        public void reset() {
        	emulator.getMemory().write( getStartAddress() , 0 );
        }
	}
	
	private final KeyListener keyListener = new KeyListener() {

		@Override
		public void keyTyped(KeyEvent e) 
		{
		    if ( ! emulationRunning.get() ) {
		        return;
		    }
		    
			final int c = e.getKeyChar();
			if ( c >= 0x20 && c <= 0x7f ) {
				keyTyped( c , true );
			}
		}
		
		private void keyTyped(int mappedKeyCode,boolean sendInterrupt) 
		{
			if ( useLegacyMemoryBuffer && legacyKeyboardBuffer != null ) {
				legacyKeyboardBuffer.writeKeyEvent( mappedKeyCode );
			}
			
			if ( ! receivedAtLeastOneInterrupt ) {
				return;
			}
			
			synchronized( BUFFER_LOCK ) 
			{
				keysTyped.add( mappedKeyCode ); 
			}	

			if ( sendInterrupt ) {
				sendInterrupt();
			}
		}
		
		private int mapKeyCode(KeyEvent e) 
		{
			final int result = internalMapKeyCode( e );
			return result;
		}
		
		private int internalMapKeyCode(KeyEvent e) 
		{
			 /* Key numbers are:
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
				 * 	0x91: Control */
			switch( e.getKeyCode() ) {
			case KeyEvent.VK_BACK_SPACE:
				return 0x10;
			case KeyEvent.VK_ENTER:
				return 0x11;
			case KeyEvent.VK_INSERT:
			return 0x12;	
			case KeyEvent.VK_DELETE:
			return 0x13;	
			case KeyEvent.VK_UP:
			return 0x80;
			case KeyEvent.VK_DOWN:
			return 0x81;
			case KeyEvent.VK_LEFT:
			return 0x82;
			case KeyEvent.VK_RIGHT:
			return 0x83;	
			case KeyEvent.VK_SHIFT:
			return 0x90;				
			case KeyEvent.VK_CONTROL:
			return 0x91;			
			}
			final int c = e.getKeyChar();
			if ( c >= 0x20 && c <= 0x7f ) {
				return c;
			}
			return -1;
		}
		
		private boolean isSpecialKey(int mappedKey) 
		{
			switch(mappedKey) {
				 case 0x10: // Backspace
				 case 0x11: // Return
				 case 0x12: // Insert
				 case 0x13: // Delete
				 case 0x80: // Arrow up
				 case 0x81: // Arrow down
				 case 0x82: // Arrow left
				 case 0x83: // Arrow right
				 case 0x90: // Shift
				 case 0x91: // Control */
					 return true;
				default:
					return false;
			}
		}

		@Override
		public void keyReleased(KeyEvent e) 
		{
            if ( ! emulationRunning.get() ) {
                return;
            }
            
			final int mapped = mapKeyCode(e);
			
			if ( mapped == -1 ) {
				return;
			}
			
			if ( isSpecialKey( mapped ) ) {
				keyTyped( mapped , false );				
			}
			
			if ( ! receivedAtLeastOneInterrupt ) {
				return;
			}
			
			synchronized(BUFFER_LOCK ) {
				keysPressed.remove( Integer.valueOf( mapped ) );
			}
			sendInterrupt();
		}

		@Override
		public void keyPressed(KeyEvent e) 
		{
            if ( ! emulationRunning.get() ) {
                return;
            }
            
			if ( ! receivedAtLeastOneInterrupt ) {
				return;
			}			
			synchronized( BUFFER_LOCK ) {
				final int mapped = mapKeyCode(e);
				if ( mapped != -1 ) {					
					keysPressed.add( mapped );
				}
			}	
			sendInterrupt();
		}
		
		private void sendInterrupt() 
		{
			if ( interruptMessage != null ) 
			{
				emulator.triggerInterrupt( new HardwareInterrupt( DefaultKeyboard.this , interruptMessage ) );
			}			
		}
	};

	public DefaultKeyboard(boolean useLegacyMemoryBuffer) {
	    this.useLegacyMemoryBuffer = useLegacyMemoryBuffer;
	}
	
	@Override
	public void reset() {
	    synchronized (BUFFER_LOCK) {
	        keysTyped.clear();
	        keysPressed.clear();
	        interruptMessage = null;
	        receivedAtLeastOneInterrupt = false;
	        if ( legacyKeyboardBuffer != null ) {
	        	legacyKeyboardBuffer.reset();
	        }
        }
	}
	
	public void setInputComponent(Component comp) 
	{
		if ( this.inputComponent != null ) {
			this.inputComponent.removeKeyListener( keyListener );
		}		
		this.inputComponent = comp;
		if ( this.inputComponent != null ) {
			this.inputComponent.addKeyListener( keyListener );
		}
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
	 */
	@Override
	public void afterAddDevice(IEmulator emulator) 
	{
		if ( this.emulator != null ) {
			throw new IllegalStateException("Device "+this+" is already added to emulator "+this.emulator);
		}
		
		this.emulator = emulator;
		this.emulator.addEmulationListener( myEmulationListener );
		
		if ( useLegacyMemoryBuffer ) {
			legacyKeyboardBuffer = new LegacyKeyboardBuffer(Address.wordAddress( 0x9000 ) );
			emulator.mapRegion( legacyKeyboardBuffer );
		}
	}

	@Override
	public void beforeRemoveDevice(IEmulator emulator) {
		if ( inputComponent != null ) {
			inputComponent.removeKeyListener( keyListener );
			this.inputComponent = null;
		}
		if ( legacyKeyboardBuffer != null ) {
			emulator.unmapRegion( legacyKeyboardBuffer );
		}
		this.emulator.removeEmulationListener( myEmulationListener );		
		this.emulator = null;
	}

	@Override
	public DeviceDescriptor getDeviceDescriptor() {
		return desc;
	}

	@Override
	public int handleInterrupt(IEmulator emulator) 
	{
		receivedAtLeastOneInterrupt = true;
		
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
			return 0;
		case 1:
			Integer keyCode = readTypedKey();
			final int msg = keyCode != null ? keyCode.intValue() : 0;
			emulator.getCPU().setRegisterValue( Register.C , msg );
			return 0;
		case 2:
			final int key = emulator.getCPU().getRegisterValue( Register.B );
			emulator.getCPU().setRegisterValue( Register.C , isKeyPressed( key ) ? 1 : 0 );
			return 0;
		case 3:
			final int irqMsg = emulator.getCPU().getRegisterValue( Register.B );
			interruptMessage = irqMsg != 0 ? irqMsg : null;
			return 0;
		default:
			LOG.warn("handleInterrupt(): Received unknown interrupt msg "+Misc.toHexString( value ) );
			return 0;
		}
	}

}