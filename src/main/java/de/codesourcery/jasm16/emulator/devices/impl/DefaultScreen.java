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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.compiler.io.ClassPathResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.ILogger;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.memory.IMemory;
import de.codesourcery.jasm16.emulator.memory.IMemoryRegion;
import de.codesourcery.jasm16.emulator.memory.MemUtils;
import de.codesourcery.jasm16.emulator.memory.MemoryRegion;
import de.codesourcery.jasm16.utils.Misc;

public final class DefaultScreen implements IDevice {

	private static final Logger LOG = Logger.getLogger(DefaultScreen.class);
	
    public static final int STANDARD_SCREEN_ROWS = 12;
    public static final int STANDARD_SCREEN_COLUMNS = 32;
    
    private final int SCREEN_ROWS;
    private final int SCREEN_COLUMNS;    

    private final int VIDEO_RAM_SIZE_IN_WORDS;

    public static final int GLYPH_WIDTH = 4;
    public static final int GLYPH_HEIGHT = 8;
    
    public static final int PALETTE_COLORS = 16;

    public static final int IMG_CHARS_PER_ROW = 32;
    
    public static final int BORDER_WIDTH = 10;
    public static final int BORDER_HEIGHT = 10;    
    
    private final int SCREEN_WIDTH;
    private final int SCREEN_HEIGHT;

    private volatile ILogger out;
    
    private final DeviceDescriptor DESC = new DeviceDescriptor("default screen",
            "jASM16 default screen" , 
            0x7349f615,
            0x1802, 
            0x1c6c8b36 );
    
    private volatile Component uiComponent; 
    private volatile ConsoleScreen consoleScreen;

    private volatile IEmulator emulator = null;

    private volatile int borderPaletteIndex = 3;
    private volatile boolean useCustomPaletteRAM = false;
    private volatile PaletteRAM paletteRAM = new PaletteRAM( WordAddress.ZERO );

    private volatile boolean requiresFullVRAMRendering = true;
    private volatile IMemoryRegion videoRAM = null;
    private final boolean connectUponAddDevice;    

    private BufferedImage defaultFontImage;    
    
    public DefaultScreen(boolean connectUponAddDevice) {
    	this( STANDARD_SCREEN_COLUMNS , STANDARD_SCREEN_ROWS , connectUponAddDevice );
    }
    
    public DefaultScreen(int screenColumns,int screenRows, boolean connectUponAddDevice) 
    {
    	if ( screenColumns < STANDARD_SCREEN_COLUMNS ) {
    		throw new IllegalArgumentException("Illegal column count "+screenColumns+", must be at least "+
    	STANDARD_SCREEN_COLUMNS);
    	}
    	if ( screenRows < STANDARD_SCREEN_ROWS ) {
    		throw new IllegalArgumentException("Illegal row count "+screenRows+", must be at least "+
    	STANDARD_SCREEN_ROWS);
    	}    
    	
    	this.SCREEN_COLUMNS = screenColumns;
    	this.SCREEN_ROWS = screenRows;
    	this.VIDEO_RAM_SIZE_IN_WORDS = SCREEN_ROWS*SCREEN_COLUMNS;
        this.SCREEN_WIDTH = (SCREEN_COLUMNS * GLYPH_WIDTH)+2*(BORDER_WIDTH);
        this.SCREEN_HEIGHT = (SCREEN_ROWS * GLYPH_HEIGHT)+2*(BORDER_HEIGHT);
        
        this.connectUponAddDevice = connectUponAddDevice;
        setupDefaultPaletteRAM();
    }
    
    private synchronized BufferedImage getDefaultFontImage(Graphics2D target) throws IOException 
    {
        if ( defaultFontImage == null ) 
        {
            final ClassPathResource resource = new ClassPathResource("default_font.png",ResourceType.UNKNOWN);
            final InputStream in = resource.createInputStream();
            try {
                defaultFontImage = ImageIO.read( in );
            } finally {
                IOUtils.closeQuietly( in );
            }
        }
        return defaultFontImage;
    }

    @Override
    public void reset()
    {
        if ( useCustomPaletteRAM ) {
            emulator.unmapRegion( paletteRAM );
            useCustomPaletteRAM = false;
        }
        paletteRAM.setDefaultPalette();
        requiresFullVRAMRendering=true;
        
        if ( videoRAM != null && ! connectUponAddDevice ) {
            emulator.unmapRegion( videoRAM );
            videoRAM = null;
        }
        consoleScreen = null;
        renderScreenDisconnectedMessage();
    }
    
    protected final class PaletteRAM extends MemoryRegion 
    {
        private final AtomicReferenceArray<Color> cache = new AtomicReferenceArray<Color>( PALETTE_COLORS );
        
        public PaletteRAM(Address start) {
            super("Palette RAM",new AddressRange( start , Size.words( PALETTE_COLORS ) ) );
        }

        public void setDefaultPalette() 
        {
            final int[] defaultPalette = { 
                    0x0000,0x000a,0x00a0,0x00aa,0x0a00,0x0a0a,0x0a50,0x0aaa,
                    0x0555,0x055f,0x05f5,0x05ff,0x0f55,0x0f5f,0x0ff5,0x0fff
            }; 
            
            if ( defaultPalette.length != PALETTE_COLORS ) {
                throw new RuntimeException("Internal error, default palette color count mismatch");
            }

            for ( int i = 0 ; i < defaultPalette.length ; i++ ) {
                write( i , defaultPalette[i] );
            }            
        }
        
        @Override
        public void clear() 
        {
            final int size = getSize().toSizeInWords().getValue();
            for ( int i = 0 ; i < size ; i++ ) {
                write( 0 , 0 );
            }
        }
        
        private Color toJavaColor( int colorValue ) {

            /*
       The LEM1802 has a default built in palette. If the user chooses, they may
       supply their own palette by mapping a 16 word memory region with one word
       per palette entry in the 16 color palette.
       Each color entry has the following bit format (in LSB-0):
           0000rrrrggggbbbb
       Where r, g, b are the red, green and blue channels. A higher value means a
       lighter color.        
             */
            final int r = ((colorValue >>> 8) & (1+2+4+8) ) << 4; // multiply by 16 to get full 0...255 (8-bit) range
            final int g = ((colorValue >>> 4) & (1+2+4+8) ) << 4; // multiply by 16 to get full 0...255 (8-bit) range
            final int b = ((colorValue      ) & (1+2+4+8) ) << 4; // multiply by 16 to get full 0...255 (8-bit) range
            return new Color( r , g , b );
        }
        
        public Color getColor(int index) {
            return cache.get( index );
        }

        @Override
        public void write(Address address, int value) {
            super.write( address, value);
            cache.set( address.getValue() , toJavaColor( value ) );
        }

        @Override
        public void write(int wordAddress, int value) {
            super.write( wordAddress , value );
            cache.set( wordAddress , toJavaColor( value ) );            
        }       
    }    

    protected final class VideoRAM extends MemoryRegion {

        public VideoRAM(Address start) {
            super("Video RAM",new AddressRange( start , Size.words( VIDEO_RAM_SIZE_IN_WORDS ) ) );
        }

        @Override
        public void clear() {
            super.clear();
            doFullVRAMRendering();
        }

        @Override
        public void write(Address address, int value) {
            super.write( address, value);
            vramMemoryUpdated( address.toWordAddress().getValue() , value );    		
        }

        @Override
        public void write(int wordAddress, int value) {
            super.write( wordAddress , value );
            vramMemoryUpdated( wordAddress , value );
        }		
    }

    protected boolean isUseCustomPaletteRAM() {
        return useCustomPaletteRAM;
    }

    protected void setupDefaultPaletteRAM() 
    {
        if ( isUseCustomPaletteRAM() ) 
        {
            emulator.unmapRegion( paletteRAM );
            useCustomPaletteRAM = false;
            paletteRAM = new PaletteRAM(Address.wordAddress( 0) );
        } 

        paletteRAM.setDefaultPalette();
    }

    protected void mapPaletteRAM(Address address) 
    {
        final PaletteRAM newRAM = new PaletteRAM( address );
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

    private boolean isConnected() {
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
        
        requiresFullVRAMRendering = true;
        doFullVRAMRendering();
    }
    
    @SuppressWarnings("deprecation")
	private void doFullVRAMRendering()
    {
        ConsoleScreen screen = screen();
        if ( screen == null ) {
            return;
        }

        screen.fillScreen(Color.BLACK);
        for ( int i = 0 ; i < VIDEO_RAM_SIZE_IN_WORDS ; i++ ) {
            renderMemoryValue( i , videoRAM.read( i ) );
        }        
        requiresFullVRAMRendering = false;
    }

    protected void disconnect() 
    {
        if ( isConnected() ) {
            emulator.unmapRegion( videoRAM );
            videoRAM = null;
            renderScreenDisconnectedMessage();
        }
    }

    private void renderScreenDisconnectedMessage() 
    {
        Graphics2D graphics = getGraphics();
        if ( graphics != null ) 
        {
            screen().renderMessage("Screen offline" , Color.BLACK,Color.WHITE);
            uiComponent.repaint();
        }
    }
    
    protected void vramMemoryUpdated(int wordAddress , int memoryValue) 
    {
        if ( requiresFullVRAMRendering ) {
            doFullVRAMRendering();
        }
        renderMemoryValue( wordAddress , memoryValue );
    }

    protected void renderMemoryValue(int wordAddress , int memoryValue) 
    {
        final Graphics2D graphics = getGraphics();
        if ( graphics == null ) {
            return;
        }

        if ( ! isConnected() ) 
        {
            renderScreenDisconnectedMessage();
            return;
        }
        
        /* The LEM1802 is a 128x96 pixel color display compatible with the DCPU-16.
         * The display is made up of 32x12 16 bit cells.
         * Each cell displays one monochrome 4x8 pixel character out of 128 available.
         */
        final int row = wordAddress / SCREEN_COLUMNS;
        final int column = wordAddress - ( row * SCREEN_COLUMNS );

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
        final int foregroundPalette = ( memoryValue >>> 12) & ( 1+2+4+8);
        final int backgroundPalette = ( memoryValue >>> 8) & ( 1+2+4+8);

        // TODO: Implement blinking...
        final boolean blink = ( memoryValue & ( 1 << 7)) != 0;
        final int asciiCode = memoryValue & (1+2+4+8+16+32+64);

        if ( asciiCode != 0 ) {
            final Color fg = paletteRAM.getColor( foregroundPalette );
            final Color bg = paletteRAM.getColor( backgroundPalette );
            consoleScreen.putChar( column , row , asciiCode , fg , bg );
        } else {
            consoleScreen.clearChar( column , row , paletteRAM.getColor( backgroundPalette ) );
        }
        uiComponent.repaint();
    }

    private Graphics2D getGraphics() 
    {
        ConsoleScreen screen = screen();
        return screen != null ? screen().getGraphics() : null;
    }

    protected boolean hasPeer() {
        return screen() != null;
    }

    @Override
    public void afterAddDevice(IEmulator emulator) {
    	if ( this.emulator != null ) {
    		throw new IllegalStateException("Device "+this+" is already associated with an emulator?");
    	}
        this.emulator = emulator;
        if ( connectUponAddDevice ) {
            connect( Address.wordAddress( 0x8000 ) );
        }
        this.out = emulator.getOutput();
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
        this.consoleScreen = null;
    }

    public BufferedImage getScreenImage() 
    {
        final ConsoleScreen screen = screen();
        return screen != null ? screen.getImage() : null;
    }
    
    protected ConsoleScreen screen() 
    {
        if ( uiComponent == null ) {
            return null;
        }

        if ( this.consoleScreen == null ) 
        {
            final Graphics2D gg = (Graphics2D) uiComponent.getGraphics();
            if ( gg == null ) {
                return null;
            }
            BufferedImage image;
            try {
                image = getDefaultFontImage( gg );
            } catch (IOException e) {
                LOG.error("screen(): Failed to load default font",e);
                throw new RuntimeException("Failed to load default font",e);
            }
            final Color borderColor = paletteRAM.getColor( borderPaletteIndex );
            this.consoleScreen =  new ConsoleScreen( image , SCREEN_WIDTH , SCREEN_HEIGHT , borderColor );
            renderScreenDisconnectedMessage();
        }
        return this.consoleScreen;
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
            this.borderPaletteIndex = b & 0x0f;
            final ConsoleScreen screen = screen();
            if ( screen != null ) {
            	screen.setBorderColor( paletteRAM.getColor( this.borderPaletteIndex ) );
            }
        } else if ( a == 4 ) {
            /*
             * 4: MEM_DUMP_FONT
             *    Reads the B register, and writes the default font data to DCPU-16 ram
             *    starting at address B.
             *    Halts the DCPU-16 for 256 cycles
             */

            // TODO: Not implemented
        	return 256;
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
                final int value = paletteRAM.read( words );
                ((IMemory) emulator.getMemory()).write( start , value );
                start = start.incrementByOne(true);
            }
            return 16;
        } else {
            out.warn("Clock "+this+" received unknown interrupt msg "+Misc.toHexString( a ));
        }
        return 0;
    }

    protected static final class ConsoleScreen {

        // array holding image data from the generated image
        private final RawImage screen;
 
        private volatile Color borderColor;
        
        // an image containing the glyphs for our font
        private final RawImage glyphBitmap;
        private final int glyphBackgroundColor;
        
        private final int screenWidth;
        private final int screenHeight;
        
        public ConsoleScreen(BufferedImage fontImage,
        		int screenWidth,
        		int screenHeight,Color borderColor) 
        {
        	this.screenWidth = screenWidth;
        	this.screenHeight=screenHeight;
            this.glyphBitmap = new RawImage( "glyphs" , fontImage.getWidth() , fontImage.getHeight() );
            this.glyphBitmap.getGraphics().drawImage( fontImage , 0 , 0, null );
            
            // choose darkest color as background color
            int[] colors = glyphBitmap.getUniqueColors();
            int candidate = 0xffffff;
            for ( int col : colors ) {
                if ( col < candidate ) {
                    candidate = col;
                }
            }
            this.borderColor = borderColor;
            glyphBackgroundColor=candidate;
            this.screen = new RawImage( "console" , screenWidth , screenHeight );
            renderBorder();
        }
        
        protected void renderBorder() {
        	final Graphics2D graphics = getGraphics();
        	graphics.setColor( borderColor );
        	graphics.fillRect( 0 , 0 , screenWidth , BORDER_HEIGHT ); // top border
        	graphics.fillRect( 0 , 0 , BORDER_WIDTH , screenHeight ); // left border 
        	graphics.fillRect( 0 , screenHeight-BORDER_HEIGHT , screenWidth , screenHeight ); // bottom border
        	graphics.fillRect( screenWidth-BORDER_WIDTH , 0 , BORDER_WIDTH , screenHeight ); // right border
        }
        
        public void setBorderColor(Color color) {
        	if (color == null) {
				throw new IllegalArgumentException("color must not be null");
			}
        	this.borderColor = color;
        	renderBorder();
        }
        
        public void clearChar(int column, int row, Color color)
        {
            final int screenX = BORDER_WIDTH + GLYPH_WIDTH * column;
            final int screenY = BORDER_HEIGHT + GLYPH_HEIGHT * row;    
            fillRect( screenX,screenY , GLYPH_WIDTH, GLYPH_HEIGHT , color );
        }
        
        public void fillScreen(Color col) 
        {
            fillRect(BORDER_WIDTH,
            		BORDER_HEIGHT,
            		screenWidth-(2*BORDER_WIDTH),
            		screenHeight-(2*BORDER_HEIGHT)
            		,col);
        }
        
        public void fillRect(int screenX, int screenY, int width,int height, Color color)
        {
            final int[] targetPixels = screen.getBackingArray();
            final int screenBitmapWidth = screen.getWidth();
            
            int firstTargetPixel = screenY * screenBitmapWidth + screenX;             
            final int col = color.getRGB();
            
            for (int i = 0; i < height; i++) 
            {
                int dst = firstTargetPixel;
                for (int j = 0; j < width ; j++) 
                {
                    targetPixels[dst++] = col;
                }
                firstTargetPixel += screenBitmapWidth;
            }            
        }

        public Graphics2D getGraphics() {
            return screen.getGraphics();
        }
        
        public int getWidth()
        {
            return screenWidth;
        }
        
        public int getHeight()
        {
            return screenHeight;
        }
        
        public BufferedImage getImage() {
            return screen.getImage();
        }
        
        public void renderMessage(String s,Color foreground,Color background) {
            
            Graphics2D graphics = getGraphics();
            
            Rectangle2D bounds = graphics.getFontMetrics().getStringBounds( s , graphics );
            
            final int x = (int) ( screen.getWidth() - bounds.getWidth() ) / 2;
            final int y = (int) ( screen.getHeight() - bounds.getHeight() ) / 2;
            
            graphics.setColor( background );
            graphics.fillRect( 0 , 0, screen.getWidth() , screen.getHeight() );
            
            graphics.setColor( foreground );
            graphics.drawString( s , x, y );
        }
        
        public void putChar(int screenColumn, int screenRow, int glyphIndex, Color fg, Color bg) 
        {
            // start of character in font img raster
            final int glyphRow = glyphIndex / IMG_CHARS_PER_ROW;
            final int glyphColumn = glyphIndex - ( glyphRow * IMG_CHARS_PER_ROW );

            final int glyphX = GLYPH_WIDTH  * glyphColumn;            
            final int glyphY = GLYPH_HEIGHT * glyphRow;
            
            final int screenX = BORDER_WIDTH + GLYPH_WIDTH * screenColumn;
            final int screenY = BORDER_HEIGHT + GLYPH_HEIGHT * screenRow;
            
            final int glyphBitmapWidth = glyphBitmap.getWidth();
            final int screenBitmapWidth = screen.getWidth();
            
            int firstGlyphPixel = glyphY * glyphBitmapWidth + glyphX;
            int firstTargetPixel = screenY * screenBitmapWidth + screenX;            
            
            final int[] glyphPixels = glyphBitmap.getBackingArray();
            final int[] targetPixels = screen.getBackingArray();
            
            for (int i = 0; i < GLYPH_HEIGHT; i++) 
            {
                int src = firstGlyphPixel;
                int dst = firstTargetPixel;
                for (int j = 0; j < GLYPH_WIDTH ; j++) 
                {
                    targetPixels[dst++] = glyphPixels[src++] != glyphBackgroundColor ? fg.getRGB() : bg.getRGB();
                }
                firstGlyphPixel += glyphBitmapWidth;
                firstTargetPixel += screenBitmapWidth;
            }
        }
    }
    
    protected static final class RawImage 
    {
        private final BufferedImage image;
        private final int[] data;
        
        public RawImage(String name,int width,int height) 
        {
            data = new int[ width*height ];
            
            Arrays.fill(data, 0xff000000);

            final DataBufferInt dataBuffer = new DataBufferInt(data, width * height);
            final ColorModel cm = ColorModel.getRGBdefault();
            final SampleModel sm = cm.createCompatibleSampleModel(width, height);
            final WritableRaster wr = Raster.createWritableRaster(sm, dataBuffer, null);
            image = new BufferedImage(cm, wr, false, null);
        }       
        
        public int[] getUniqueColors() {
            final Set<Integer> result = new HashSet<Integer>();
            for ( int i = 0 ; i < data.length ; i++ ) {
                result.add( data[i] );
            }
            return ArrayUtils.toPrimitive( result.toArray( new Integer[ result.size() ] ));
        }
        
        public int getWidth() {
            return image.getWidth();
        }
        
        public int getHeight() {
            return image.getHeight();
        }
        
        public BufferedImage getImage() {
            return image;
        }
        
        public Graphics2D getGraphics() {
            return (Graphics2D) image.getGraphics();
        }
        
        public int[] getBackingArray() {
            return data;
        }
    }
}