package de.codesourcery.jasm16.emulator.devices.impl;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ByteLookupTable;
import java.awt.image.ColorConvertOp;
import java.awt.image.LookupOp;
import java.awt.image.ShortLookupTable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.compiler.io.ClassPathResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.memory.IMemory;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion;
import de.codesourcery.jasm16.emulator.memory.MemUtils;
import de.codesourcery.jasm16.emulator.memory.MemoryRegion;
import de.codesourcery.jasm16.utils.Misc;

public class DefaultScreen implements IDevice {

	private static final int SCREEN_ROWS = 12;
	private static final int SCREEN_COLUMNS = 32;

	private static final int VIDEO_RAM_SIZE_IN_WORDS = SCREEN_ROWS*SCREEN_COLUMNS;	

	private static final int FONT_WIDTH = 4;
	private static final int FONT_HEIGHT = 8;

	private static final int SCREEN_WIDTH = SCREEN_COLUMNS * FONT_WIDTH;
	private static final int SCREEN_HEIGHT = SCREEN_ROWS * FONT_HEIGHT;

	private static final Logger LOG = Logger.getLogger(DefaultScreen.class);

	private final DeviceDescriptor DESC = new DeviceDescriptor("default screen",
			"jASM16 default screen" , 
			0x7349f615,
			0x1802, 
			0x1c6c8b36 );

	private static BufferedImage defaultFontImage;
	
	private final Map<Integer,BufferedImage> fontCache = new HashMap<Integer,BufferedImage>();

	private static synchronized BufferedImage getDefaultFontImage() throws IOException {
		if ( defaultFontImage == null ) 
		{
			final ClassPathResource resource = new ClassPathResource("default_font.png",
					ResourceType.UNKNOWN);
			final InputStream in = resource.createInputStream();
			try {
				return ImageIO.read( in );
			} finally {
				IOUtils.closeQuietly( in );
			}
		}
		return defaultFontImage;
	}

	private volatile Component uiComponent; 
	private volatile BufferedImage offscreenImage;

	private volatile IEmulator emulator = null;
	private volatile int borderColorPaletteIndex=0;

	private volatile boolean useCustomPaletteRAM = false;
	private volatile IMemoryRegion paletteRAM = new MemoryRegion("palette RAM", 
			new AddressRange( WordAddress.ZERO , Size.words( 16 ) ) );

	private volatile IMemoryRegion videoRAM = null;
	private final boolean connectUponAddDevice;

	protected final class VideoRAM extends MemoryRegion {

		public VideoRAM(Address start) {
			super("Video RAM",new AddressRange( start , Size.words( VIDEO_RAM_SIZE_IN_WORDS ) ) );
		}

		@Override
		public void clear() {
			super.clear();
			displayCleared();
		}

		@Override
		public void write(Address address, int value) {
			super.write( address, value);
			displayChanged( address.toWordAddress().getValue() , value );    		
		}

		@Override
		public void write(int wordAddress, int value) {
			super.write( wordAddress , value );
			displayChanged( wordAddress , value );
		}		
	}

	public DefaultScreen(boolean connectUponAddDevice) {
		this.connectUponAddDevice = connectUponAddDevice;
		setupDefaultPaletteRAM();
	}

	protected boolean isUseCustomPaletteRAM() {
		return useCustomPaletteRAM;
	}

	@SuppressWarnings("deprecation")
	protected void setupDefaultPaletteRAM() {
		if ( isUseCustomPaletteRAM() ) 
		{
			emulator.unmapRegion( paletteRAM );
			useCustomPaletteRAM = false;
			paletteRAM = new MemoryRegion("palette RAM", 
					new AddressRange( WordAddress.ZERO, Size.words( 16 ) ) );
		} 
		final int[] defaultPalette = { 0x0000,0x000a,0x00a0,0x00aa,0x0a00,0x0a0a,0x0a50,0x0aaa,
				0x0555,0x055f,0x05f5,0x05ff,0x0f55,0x0f5f,0x0ff5,0x0fff}; 

		for ( int i = 0 ; i < defaultPalette.length ; i++ ) {
			paletteRAM.write( i , defaultPalette[i] );
		}
	}

	protected void mapPaletteRAM(Address address) 
	{
		final IMemoryRegion newRAM = new MemoryRegion("palette RAM", new AddressRange( 
				address , Size.words( 16 ) ) );
		if ( isUseCustomPaletteRAM() ) 
		{
			if ( paletteRAM.getAddressRange().getStartAddress().equals( address ) ) {
				return; // nothing to be done
			}
			// copy video RAM contents to new region before re-assigning
			MemUtils.memCopy( newRAM , newRAM , Address.wordAddress( 0 ) , newRAM.getSize() );
			emulator.unmapRegion( paletteRAM );
		} 
		paletteRAM = newRAM;
		emulator.mapRegion( paletteRAM );
	}

	protected boolean isConnected() {
		return videoRAM != null;
	}

	protected void connect(Address videoRAMAddress) 
	{
		final IMemoryRegion newRAM = new VideoRAM( videoRAMAddress );		
		if ( isConnected() ) 
		{
			if ( videoRAM.getAddressRange().getStartAddress().equals( videoRAMAddress ) ) {
				return; // nothing to be done
			}
			// copy video RAM contents to new region before re-assigning
			MemUtils.memCopy( videoRAM , newRAM , Address.wordAddress( 0 ) , videoRAM.getSize() );
			disconnect();
		} 
		videoRAM = newRAM;
		emulator.mapRegion( newRAM );
		refreshScreen();
	}

	protected void disconnect() 
	{
		if ( isConnected() ) {
			emulator.unmapRegion( videoRAM );
			videoRAM = null;
			renderDisconnectedScreen();
		}
	}

	private void renderDisconnectedScreen() 
	{
		if ( ! isConnected() ) {
			Graphics2D graphics = getGraphics();
			if ( graphics != null ) 
			{
				clearScreen();
				graphics.setColor( Color.RED );
				graphics.drawString( "Screen not connected" , 50 ,50 );					
			}
		}
	}
	
	protected void clearScreen() {
		Graphics2D graphics = getGraphics();
		if ( graphics != null ) 
		{
			graphics.clearRect(0 , 0 , offscreenImage.getWidth() , offscreenImage.getHeight());
		}
	}
	
	@SuppressWarnings("deprecation")
	protected void refreshScreen() {
		
		clearScreen();
		for ( int i = 0 ; i < VIDEO_RAM_SIZE_IN_WORDS ; i++ ) {
			displayChanged( i , videoRAM.read( i ) );
		}
	}

	protected void displayChanged(int wordAddress , int value) 
	{
		final Graphics2D graphics = getGraphics();
		if ( graphics == null ) {
			return;
		}

		if ( ! isConnected() ) 
		{
			renderDisconnectedScreen();
			return;
		}

		final Point p = toScreenCoordinates( wordAddress );
		try 
		{
			/*
			 * The video RAM is made up of 32x12 cells of the following bit format (in LSB-0):
			 * 
			 * ffffbbbbBccccccc
			 *
			 * - The lowest 7 bits (ccccccc) select define character to display.
			 * - If B (bit 7) is set the character color will blink slowly.
			 * - ffff selects which foreground color to use.
			 * - bbbb selects which background color to use.    
			 */
			final int foregroundPalette = ( value >>> 12) & ( 1+2+4+8);
			final int backgroundPalette = ( value >>> 8) & ( 1+2+4+8);

			// TODO: Implement blinking...
			final boolean blink = ( value & ( 1 << 7)) != 0;
			final int asciiCode = value & (1+2+4+8+16+32+64);

			graphics.setColor( getColor( backgroundPalette ) );
			graphics.fillRect( p.x , p.y , FONT_WIDTH , FONT_HEIGHT );

			if ( value != 0 ) {
				graphics.setColor( getColor( foregroundPalette ) );
				graphics.setPaintMode();
				
				BufferedImage character = getFontData( asciiCode );
				character = setColors( character , getColor( foregroundPalette ) , getColor( backgroundPalette ) );
				graphics.drawImage( character , p.x , p.y , null );
			}
			uiComponent.repaint();
		} 
		catch (IOException e) {
			LOG.error("displayChanged(): Something bad happened",e);
		}
	}
	
	private BufferedImage setColors(BufferedImage input , Color fg,Color bg) 
	{
		final byte rlut[] = new byte[256];
		final byte glut[] = new byte[256]; 
		final byte blut[] = new byte[256];
		
		rlut[0] = (byte) bg.getRed();		
        for ( int i = 1 ; i < 256 ; i++ )
        {
            rlut[i] = (byte) fg.getRed();
        }
        
        glut[0] = (byte) bg.getGreen();
        for ( int i = 1 ; i < 256 ; i++ )
        {
            glut[i] = (byte) fg.getGreen();
        }

        blut[0] = (byte) bg.getBlue();        
        for ( int i = 1 ; i < 256 ; i++ )
        {
            blut[i] = (byte) fg.getBlue();
        }        
        
        final ByteLookupTable table=new ByteLookupTable(0,new byte[][]{rlut,glut,blut} );
        final LookupOp lop = new LookupOp(table, null);
        
        final BufferedImage dstImage = 
        	getGraphics().getDeviceConfiguration().createCompatibleImage(input.getWidth() , input.getHeight());
        		
        lop.filter( input , dstImage );
		return dstImage;
	}

	private Color getColor( int paletteIndex ) {

		@SuppressWarnings("deprecation")
		final int colorValue= paletteRAM.read( paletteIndex );

		/*
   The LEM1802 has a default built in palette. If the user chooses, they may
   supply their own palette by mapping a 16 word memory region with one word
   per palette entry in the 16 color palette.
   Each color entry has the following bit format (in LSB-0):
       0000rrrrggggbbbb
   Where r, g, b are the red, green and blue channels. A higher value means a
   lighter color.		 
		 */
		final int r = (colorValue >>> 8) & (1+2+4+8);
		final int g = (colorValue >>> 4) & (1+2+4+8);
		final int b = (colorValue      ) & (1+2+4+8);
		return new Color(r*16,g*16,b*16);
	}

	protected Point toScreenCoordinates(Address address) 
	{
		return toScreenCoordinates( address.getValue() );
	}

	private BufferedImage getFontData(int asciiCode) throws IOException 
	{
		BufferedImage result=fontCache.get( asciiCode );
		if ( result != null ) {
			return result;
		}
		
		/*
		 * I assume that the PNG
		 * contains 32 characters per row 
		 * ( so it's 32 * FONT_WIDTH = 128 pixels wide )  
		 */
		final int IMG_CHARS_PER_ROW = 32;
		final int row = asciiCode / IMG_CHARS_PER_ROW;
		final int col = asciiCode - ( row * IMG_CHARS_PER_ROW );

		final Point p1 = new Point( col * FONT_WIDTH , row * FONT_HEIGHT );

		final BufferedImage img = getGraphics()
				.getDeviceConfiguration().createCompatibleImage(FONT_WIDTH, FONT_HEIGHT );

		final BufferedImage subImage = 
				getDefaultFontImage().getSubimage( p1.x , p1.y , FONT_WIDTH , FONT_HEIGHT );

		img.getGraphics().drawImage( subImage , 0 , 0 , null );
		
		fontCache.put( asciiCode , img );
		return img;
	}

	protected Point toScreenCoordinates(int absAddress) 
	{
		if ( isConnected() ) {
			if ( absAddress < 0 || absAddress >= VIDEO_RAM_SIZE_IN_WORDS ) {
				LOG.error("toScreenCoordinates(): Address "+absAddress+" is outside of current video RAM ("+videoRAM+") ?");
				throw new IllegalArgumentException("Address "+absAddress+" is outside of current video RAM ("+videoRAM+") ?");
			}
			/* The LEM1802 is a 128x96 pixel color display compatible with the DCPU-16.
			 * The display is made up of 32x12 16 bit cells.
			 * Each cell displays one monochrome 4x8 pixel character out of 128 available.
			 */
			final int row = absAddress / SCREEN_COLUMNS;
			final int column = absAddress - ( row * SCREEN_COLUMNS );
			return new Point( column * FONT_WIDTH , row * FONT_HEIGHT );
		}
		return null;
	}

	protected void displayCleared() 
	{
		final Graphics2D graphics = getGraphics();
		if ( graphics == null ) {
			return;
		}

		if ( ! isConnected() ) 
		{
			graphics.clearRect(0 , 0 , offscreenImage.getWidth() , offscreenImage.getHeight());
			graphics.setColor( Color.RED );
			graphics.drawString( "Screen not connected" , 50 ,50 );
			return;
		}

		graphics.setColor(Color.WHITE);
		graphics.clearRect( 0, 0, offscreenImage.getWidth() , offscreenImage.getHeight() );
	}

	private Graphics2D getGraphics() 
	{
		final BufferedImage img = screen();
		return img != null ? (Graphics2D) img.getGraphics() : null;
	}

	protected boolean hasPeer() {
		return screen() != null;
	}

	@Override
	public void afterAddDevice(IEmulator emulator) {
		this.emulator = emulator;
		if ( connectUponAddDevice ) {
			connect( Address.wordAddress( 0x8000 ) );
		}
	}

	@Override
	public void beforeRemoveDevice(IEmulator emulator) 
	{
		if ( isConnected() ) {
			disconnect();
		}
		if ( useCustomPaletteRAM ) {
			emulator.unmapRegion( paletteRAM );
			useCustomPaletteRAM = false;
			paletteRAM = null;
		}		
		this.emulator = null;
	}

	@Override
	public DeviceDescriptor getDeviceDescriptor() {
		return DESC;
	}

	public void attach(Component uiComponent )
	{
		if (uiComponent == null) {
			throw new IllegalArgumentException("uiComponent must not be null");
		}
		this.uiComponent = uiComponent;
		this.offscreenImage = null;
		if ( screen() != null ) {
			displayCleared();
		}
	}

	public BufferedImage screen() 
	{
		if ( uiComponent == null ) {
			return null;
		}

		if ( this.offscreenImage == null ) 
		{
			final GraphicsConfiguration gg = uiComponent.getGraphicsConfiguration();
			if ( gg == null ) {
				return null;
			}
			this.offscreenImage = gg.createCompatibleImage(SCREEN_WIDTH , SCREEN_HEIGHT );
			renderDisconnectedScreen();
		}
		return this.offscreenImage;
	}

	public void detach() {
		this.uiComponent = null;
		this.offscreenImage = null;
	}

	@Override
	public int handleInterrupt(IEmulator emulator) 
	{
		/*
		 * Interrupt behavior:
		 * When a HWI is received by the LEM1802, it reads the A register and does one
		 * of the following actions:
		 */
		final int a = emulator.getCPU().getRegisterValue( Register.A );

		if ( a == 0 ) 
		{
			/*
			 * 0: MEM_MAP_SCREEN
			 *    Reads the B register, and maps the video ram to DCPU-16 ram starting
			 *    at address B. See below for a description of video ram.
			 *    If B is 0, the screen is disconnected.
			 *    When the screen goes from 0 to any other value, the the LEM1802 takes
			 *    about one second to start up. Other interrupts sent during this time
			 *    are still processed.
			 */
			final int b = emulator.getCPU().getRegisterValue( Register.B );
			if ( b == 0 ) {
				disconnect();
			} else {
				final Address ramStart = Address.wordAddress( b );
				// TODO: Behaviour if ramStart + vRAMSize > 0xffff ?
				connect( ramStart );
			}
		} else if ( a== 1 ) 
		{
			/*
			 * 1: MEM_MAP_FONT
			 *    Reads the B register, and maps the font ram to DCPU-16 ram starting
			 *    at address B. See below for a description of font ram.
			 *    If B is 0, the default font is used instead.
			 */
			// TODO: Not implemented
		} 
		else if ( a == 2 ) 
		{
			/*
			 * 2: MEM_MAP_PALETTE
			 *    Reads the B register, and maps the palette ram to DCPU-16 ram starting
			 *    at address B. See below for a description of palette ram.
			 *    If B is 0, the default palette is used instead.
			 */
			final int b = emulator.getCPU().getRegisterValue( Register.B );
			if ( b == 0 ) {
				setupDefaultPaletteRAM();
			} else {
				final Address ramStart = Address.wordAddress( b );
				// TODO: Behaviour if ramStart + vRAMSize > 0xffff ?
				mapPaletteRAM( ramStart );
			}			
		} else if ( a == 3 ) {
			/*
			 * 3: SET_BORDER_COLOR
			 *    Reads the B register, and sets the border color to palette index B&0xF
			 */
			final int b = emulator.getCPU().getRegisterValue( Register.B );
			borderColorPaletteIndex = b & 0xf;
		} else if ( a == 4 ) {
			/*
			 * 4: MEM_DUMP_FONT
			 *    Reads the B register, and writes the default font data to DCPU-16 ram
			 *    starting at address B.
			 *    Halts the DCPU-16 for 256 cycles
			 */

			// TODO: Not implemented

		} else if ( a == 5 ) {
			/*
			 * 5: MEM_DUMP_PALETTE
			 *    Reads the B register, and writes the default palette data to DCPU-16
			 *    ram starting at address B.       
			 *    Halts the DCPU-16 for 16 cycles
			 */
			Address start = Address.wordAddress( emulator.getCPU().getRegisterValue( Register.B ) );
			for ( int words = 0 ; words < 16 ; words++) 
			{
				@SuppressWarnings("deprecation")
				final int value = paletteRAM.read( words );
				((IMemory) emulator.getMemory()).write( start , value );
				start = start.incrementByOne(true);
			}
			return 16;
		} else {
			LOG.warn("handleInterrupt(): "+this+" received unknown interrupt msg "+Misc.toHexString( a ));
		}
		return 0;
	}
}
