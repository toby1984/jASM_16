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

import static java.lang.Math.round;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.emulator.ICPU;
import de.codesourcery.jasm16.emulator.IEmulator;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.IDevice;
import de.codesourcery.jasm16.emulator.memory.IMemory;
import de.codesourcery.jasm16.emulator.memory.MemoryRegion;
import de.codesourcery.jasm16.utils.LinAlgUtils;
import de.codesourcery.jasm16.utils.Matrix;
import de.codesourcery.jasm16.utils.Misc;
import de.codesourcery.jasm16.utils.Vector4;

public class DefaultVectorDisplay implements IDevice {

	private static final Logger LOG = Logger.getLogger(DefaultVectorDisplay.class);
	
	public static final DeviceDescriptor DEVICE_DESCRIPTOR = new DeviceDescriptor("SPED-3", 
			"Mackapar Suspended Particle Exciter Display", 0x42babf3c, 0x03, 0x1eb37e91); 
	
	public static final boolean DEBUG = false;
	
	public static final Size VERTEX_SIZE_IN_WORDS = Size.words(1);
	
	public static final Color BACKGROUND_COLOR = Color.BLACK;
	
	public static final int MAX_VERTICES = 128;
	
	/**
	 * Target frame rate (30 fps).
	 */
	public static final int FRAMES_PER_SECOND = 25; 
	
	public static final int DISPLAY_WIDTH_PIXELS = 640;
	
	public static final int DISPLAY_HEIGHT_PIXELS = 480;

	private volatile IEmulator emulator;
	
	private volatile DeviceState deviceState=DeviceState.NO_DATA;
	private volatile ErrorCode lastError = ErrorCode.NO_ERROR;
	
	private final Matrix viewMatrix;

	private final AtomicReference<Float> rotationInDegreesPerFrame = new AtomicReference<>(Float.valueOf(0));
	
	private final Object UI_PEER_LOCK = new Object();
	
	// @GuardedBy( UI_PEER_LOCK )
	private final AtomicReference<Component> uiPeer= new AtomicReference<>();
	
	// @GuardedBy( UI_PEER_LOCK )
	private VertexRAM vertexRAM = new VertexRAM(Address.wordAddress(0),MAX_VERTICES);

	private final Object RENDERING_THREAD_LOCK = new Object();
	
	// @GuardedBy( RENDERING_THREAD_LOCK ) 
	private RenderingThread renderingThread = null;
	
	// two backing images (double buffering)
	
	private final RawImage frontBuffer = new RawImage(DISPLAY_WIDTH_PIXELS,DISPLAY_HEIGHT_PIXELS);
	private final RawImage backBuffer = new RawImage(DISPLAY_WIDTH_PIXELS,DISPLAY_HEIGHT_PIXELS);
	
	// @GuardedBy( UI_PEER_LOCK )
	private RawImage visibleBuffer = frontBuffer;
	
	protected static final class VertexRAM extends MemoryRegion 
	{
		private volatile boolean hasChanged = true;
		
		private final Object VERTICES_LOCK = new Object();
		
		// @GuardedBy( VERTICES_LOCK )
		private List<Vertex> vertices = new ArrayList<Vertex>(MAX_VERTICES);
		
		// @GuardedBy( VERTICES_LOCK )		
		private boolean isMapped = false;
		
		private final int vertexCount;
		
		public VertexRAM(Address startAddress,int vertexCount) 
		{
			super("SPED-3 Vertex RAM", 0x12683126 , 
					new AddressRange( startAddress , startAddress.plus( VERTEX_SIZE_IN_WORDS.multiply( vertexCount ), true ) ), Flag.MEMORY_MAPPED_HW);
			this.vertexCount = vertexCount;
			if ( vertexCount <0 || vertexCount > MAX_VERTICES ) {
				throw new IllegalArgumentException("Invalid vertex count "+vertexCount);
			}
		}
		
		public void map(IEmulator emulator) 
		{
			synchronized( VERTICES_LOCK ) 
			{
				if ( ! isMapped )
				{
					emulator.mapRegion( this );
					isMapped = true;
					hasChanged = true;					
				}
			}
		}
		
		public List<Vertex> getVertices() 
		{
			synchronized( VERTICES_LOCK ) 
			{
				if ( hasChanged ) {
					vertices = readVertices();
					hasChanged = false;
				}
				return vertices;
			}
		}
		
		private List<Vertex> readVertices() 
		{
			final List<Vertex> result = new ArrayList<>();
			
			if ( ! isMapped ) {
				return result;
			}
			final int words = vertexCount*2;
			for ( int i = 0 ; i < words ; i+=2 ) {
				int word0 = read( i );
				int word1 = read( i+1 );
				final Vertex vertex = Vertex.fromMemory(word0,word1 );
				System.out.println("READ: "+vertex);
                result.add( vertex );
			}
			return result;
		}
		
		@Override
		public void write(Address address, int value) {
			super.write(address, value);
			hasChanged = true;
		}
		
		@Override
		public void write(int wordAddress, int value) {
			super.write(wordAddress, value);
			hasChanged = true;
		}
		
		@Override
		public void clear() {
			super.clear();
			hasChanged = true;
		}
		
		public void unmap(IEmulator emulator) 
		{
			synchronized( VERTICES_LOCK ) 
			{
				if ( isMapped )
				{
					emulator.unmapRegion( this );
					isMapped = false;						
					hasChanged = true;
				}
			}			
		}
	}
	
	protected static enum ColorCode 
	{
		BLACK(Color.BLACK,new Color(15,15,15)),
		RED(Color.RED.darker() , Color.RED ),
		GREEN(Color.GREEN.darker() , Color.GREEN ),
		BLUE(Color.blue.darker() , Color.BLUE );
		
		private final Color regular;
		private final Color intense;
		
		private ColorCode(Color regular, Color intense) {
			this.regular = regular;
			this.intense = intense;
		}
		
		public Color getColor(boolean intense) {
			return intense ? this.intense : this.regular;
		}

		public static ColorCode valueOf(int bits) {
			switch( bits ) {
				case 0:
					return BLACK;
				case 1:
					return RED;
				case 2:
					return GREEN;
				case 3:
					return BLUE;
				default:
					throw new IllegalArgumentException("Unknown color code: "+bits);
			}
		}
	}
	
	protected final class RenderingThread extends Thread {
		
		private final Object SLEEP_LOCK = new Object();
		private volatile boolean terminate = false;
		
		private final CountDownLatch terminateLatch = new CountDownLatch(1);
		
		private volatile boolean halt = true;
		
		public RenderingThread() {
			setName("SPED-3 rendering thread");
			setDaemon(true);
		}
		
		public void setRunnable(boolean shouldRun) {
			this.halt = !shouldRun;
			synchronized(SLEEP_LOCK) {
				SLEEP_LOCK.notifyAll();
			}
		}

		public void terminate() 
		{
			this.terminate = true;
			synchronized(SLEEP_LOCK) {
				SLEEP_LOCK.notifyAll();
			}	
			
			try {
				terminateLatch.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		
		public void run() 
		{
			try 
			{
				while( ! terminate ) 
				{
					while ( halt && ! terminate ) 
					{
						try 
						{
							logDebug("Rendering thread going to sleep.");							
							synchronized(SLEEP_LOCK) {
								SLEEP_LOCK.wait();
							}
						} catch(InterruptedException e) {
						}
						logDebug("Rendering thread got woken up.");
					}
					
					if ( ! halt && ! terminate ) 
					{
						renderOneFrame();
						LockSupport.parkNanos( (1000 / FRAMES_PER_SECOND ) * 1000000 ); // ~30 fps
					}
				}
			} 
			finally 
			{
				synchronized( UI_PEER_LOCK ) 
				{				
					visibleBuffer.fill( BACKGROUND_COLOR );
				}
				deviceState = DeviceState.NO_DATA;				
				terminateLatch.countDown();
				logDebug("Rendering thread terminated.");
			}
		}
		
		protected void renderOneFrame() 
		{
			synchronized( UI_PEER_LOCK ) 
			{
				// determine buffer to draw to
				final RawImage invisibleBuffer = ( visibleBuffer == frontBuffer ) ? backBuffer : frontBuffer;
				
				// clear buffer
				invisibleBuffer.fill( BACKGROUND_COLOR );				
				
				// render vertices
				if ( uiPeer != null ) 
				{
					final List<Vertex> vertices = vertexRAM.getVertices();
					renderToBuffer(vertices,invisibleBuffer.getGraphics());
				}
				
				// swap buffers
				visibleBuffer = invisibleBuffer;
				
				// tell peer to repaint itself
				if ( uiPeer != null ) {
					uiPeer.get().repaint();
				}
			}
		}

		private void renderToBuffer(final List<Vertex> copy,Graphics graphics) 
		{
			if ( copy.size() < 2 ) {
				deviceState = DeviceState.NO_DATA;				
				return;
			}
			
//			Matrix modelMatrix = LinAlgUtils.rotZ( rotationInDegreesPerFrame.get() );
//			final Matrix mvpMatrix = viewMatrix.multiply( modelMatrix );
			
            Matrix modelMatrix = LinAlgUtils.rotZ( rotationInDegreesPerFrame.get() );
            final Matrix mvpMatrix = modelMatrix.multiply( viewMatrix );
			
			// draw vertices
			for ( int i = 0 ; i< (copy.size()-1) ; i+=1 ) 
			{
				final Vertex p1 = copy.get(i);
				Vector4 p1Point = mvpMatrix.multiply( p1.p);
				
				final Vertex p2 = copy.get(i+1);
				Vector4 p2Point = mvpMatrix.multiply( p2.p );
				
				p1Point = p1Point.normalizeW();
				p2Point = p2Point.normalizeW();
				
                System.out.println( p1Point+" -> "+p2Point );
				
				drawLine(graphics,p1Point,p1.color,p2Point,p2.color);
			}
			deviceState = DeviceState.STATE_RUNNING;
		}
		
		private void drawLine(Graphics graphics,
				Vector4 p1, Color color1, 
				Vector4 p2, Color color2) 
		{
			final Color c = averageColor( color1 , color2 );
			graphics.setColor(c);
			
			final float scaleX = 2f;
			final float scaleY = 2f;
			
			final int halfWidth = DISPLAY_WIDTH_PIXELS/2;
			final int halfHeight = DISPLAY_HEIGHT_PIXELS/2;
			
			final int p1x = halfWidth  + round( p1.x() * scaleX);
			final int p1y = halfHeight - round( p1.y() * scaleY);
			
			final int p2x = halfWidth  + round( p2.x() * scaleX);
			final int p2y = halfHeight - round( p2.y() * scaleY);
			
			graphics.drawLine( p1x , p1y , p2x , p2y );
		}
		
		private Color averageColor(Color c1,Color c2) 
		{
			if (c1 == c2 || c1.equals( c2 ) ) {
				return c1;
			}
			int r = (c1.getRed()+c2.getRed())/2;
			int g = (c1.getGreen()+c2.getGreen())/2;
			int b = (c1.getBlue()+c2.getBlue())/2;
			return new Color(r,g,b);
		}
	}
	
	public DefaultVectorDisplay() 
	{
		viewMatrix = setupPerspectiveProjection( 60, DISPLAY_WIDTH_PIXELS / (float) DISPLAY_HEIGHT_PIXELS , 1 , 300 );
	}
	
    public Matrix setupPerspectiveProjection(float fieldOfView, float aspectRatio ,float zNear, float zFar) 
    {
//        return LinAlgUtils.createOrthoProjection( fieldOfView , aspectRatio , zNear , zFar );
    	return LinAlgUtils.createPerspectiveProjection( fieldOfView , aspectRatio , zNear , zFar );
    }
    
    public void attach(Component peer) 
	{
		synchronized(UI_PEER_LOCK) 
		{
			if ( ! uiPeer.compareAndSet(null,peer) ) {
				throw new IllegalStateException("Vector display already attached to component "+peer);
			}
		}
		logDebug("SPED-3 attached to peer");
		startRenderingThread();
	}
	
	public BufferedImage getImage() 
	{
		synchronized ( UI_PEER_LOCK ) 
		{ 
			return visibleBuffer.getImage();
		}
	}
	
	private void startRenderingThread() 
	{
		synchronized (RENDERING_THREAD_LOCK) 
		{
			if ( renderingThread == null || ! renderingThread.isAlive() ) {
				logDebug("Starting rendering thread.");
				renderingThread = new RenderingThread();
				renderingThread.start();
			} else {
				renderingThread.setRunnable( true );
			}
		}
	}
	
	private void logDebug(String msg) {
		if ( DEBUG ) {
			System.out.println( msg );
		}
	}
	
	public void detach()  
	{
		synchronized(UI_PEER_LOCK) 
		{
			if ( uiPeer.get() == null ) 
			{
				return;
			}
			uiPeer.set(null); 
		}
		logDebug("SPED-3 detached from peer");		
		stopRenderingThread();
	}		
	
	private void stopRenderingThread() 
	{
		synchronized (RENDERING_THREAD_LOCK) 
		{
			if ( renderingThread != null && renderingThread.isAlive() ) 
			{
				logDebug("Stopping rendering thread.");
				renderingThread.terminate();
			}
			renderingThread = null;
		}
	}	
	
	protected static final class Vertex 
	{
		public final Color color;
		public final Vector4 p;
		
		private Vertex(int x, int y, int z, ColorCode color, boolean intensity) 
		{
			this.p = new Vector4(x-128,y-128,-(z+3)); 
			this.color = color.getColor( intensity );
		}
		
		@Override
		public String toString() {
			return "Vertex[ "+p+" , color="+color+" ]";
		}
		
		/*
         * Each vertex occupies two words of information in the main DCPU RAM. The data is
         * encoded as (in LSB-0 format):
         * 
         * First word: YYYYYYYYXXXXXXXX
         * Second word: 00000ICCZZZZZZZZ
         * 
         * => XXXXXXXX is the X-coordinate of the vertex
         * => YYYYYYYY is the Y-coordinate 
         * => ZZZZZZZZ is the Z-coordinate (Z is deeper into the screen, if the device is placed face up on the ground, this translates to "up" relative to the user.)
         * => CC is color (possible color values are 0: black, 1: red, 2: green, 3: blue)
         * => I is intensity (If the intensity bit is set, the color is drawn as more intense. The black color is meant to barely be visible at all, except for in very dim
         * environments, or if the intensity bit is set.
		 */		
		public static Vertex fromMemory(int word0, int word1) 
		{
			final int y = (word0 >> 8) & 0xff;
			final int x = word0 & 0xff;
			final int z = word1 & 0xff;
			final int colorCode = (word1 >>> 8) & 0b11;
			final boolean intense = (word1 & ( 1 << 10) ) != 0;
			return new Vertex( x,y,z, ColorCode.valueOf( colorCode ) , intense );
		}		
	}
	
	protected static enum DeviceState 
	{
		/**
		 * No vertices queued up, device is in stand-by.
		 */
		NO_DATA(0x0000),
		/**
		 * The device is projecting lines.
		 */
		STATE_RUNNING(0x0001),
		/**
		 * The device is projecting lines and turning
		 */
		STATE_TURNING(0x0002);
		
		private int flag;
		
		private DeviceState(int flag) { this.flag = flag; }
		public int getFlag() { return flag; }
	}
	
	protected static enum ErrorCode 
	{
		/**
		 * There's been no error since the last poll.
		 */
		NO_ERROR(0x000), 
		/**
		 * There's been some major software or hardware problem,try turning off and turning on the device again.		
		 */
		DEVICE_BROKEN(0xffff);
		
		private int flag;
		
		private ErrorCode(int flag) { this.flag = flag; }
		public int getFlag() { return flag; }
	}	
	
	@Override
	public void afterAddDevice(IEmulator emulator) {
		this.emulator = emulator;
	}

	@Override
	public void reset() 
	{
		stopRenderingThread();
		
		synchronized(UI_PEER_LOCK) 
		{
			vertexRAM.unmap( emulator );
			vertexRAM = new VertexRAM( WordAddress.ZERO , MAX_VERTICES );
		}
	}

	@Override
	public boolean supportsMultipleInstances() {
		return false;
	}

	@Override
	public void beforeRemoveDevice(IEmulator emulator) {
		stopRenderingThread();
		this.emulator = null;
	}

	@Override
	public DeviceDescriptor getDeviceDescriptor() {
		return DEVICE_DESCRIPTOR;
	}
	
	private void mapVertexRam(IEmulator emulator,Address start,int vertexCount) 
	{
		logDebug("Mapping vertex RAM to "+start+" , vertex count = "+vertexCount);
		synchronized(UI_PEER_LOCK) 
		{
			vertexRAM.unmap( emulator );
			final VertexRAM tmp = new VertexRAM(start , vertexCount );
			tmp.map( emulator );
			vertexRAM = tmp ;
		}
		startRenderingThread();
	}

	@Override
	public int handleInterrupt(IEmulator emulator, ICPU cpu, IMemory memory) 
	{
		final int a = cpu.getRegisterValue(Register.A);
		switch(a) 
		{
			case 0:
				/* 0  => Poll device. Sets B to the current state (see below) and C to the last error
		         *       since the last device poll.
		         */		
				cpu.setRegisterValue(Register.B , deviceState.getFlag() );
				cpu.setRegisterValue(Register.C , lastError.getFlag() );
				lastError = ErrorCode.NO_ERROR;
				break;
			case 1:
		        /* 1  => Map region. Sets the memory map offset to X, and the total number of vertices
		         *       to render to Y. See below for the encoding information.
		         */
				final Address start = Address.wordAddress( cpu.getRegisterValue( Register.X ) );
				final int vertexCount = cpu.getRegisterValue( Register.Y );
				mapVertexRam(emulator,start,vertexCount);
				break;
			case 2:
		        // 2  => Rotate device. Sets the target rotation for the device to X%360 degrees.
				final float rotationsPerSecond = FRAMES_PER_SECOND;
				final float rotationDegreesPerSecond = (cpu.getRegisterValue(Register.X) % 360);
				logDebug("Changing rotation speed to "+cpu.getRegisterValue(Register.X)+" degrees / sec");
				rotationInDegreesPerFrame.set( (float) (rotationDegreesPerSecond / rotationsPerSecond ) );
				break;
			default:
				LOG.error("handleInterrupt(): Received unknown interrupt , register A = "+Misc.toHexString( a ) );
		}
		return 0;
	}
}