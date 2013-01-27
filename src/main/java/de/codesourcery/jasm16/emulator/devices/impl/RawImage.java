package de.codesourcery.jasm16.emulator.devices.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;

/**
 * 24-bit RGB iamge backed by a <code>IntegerInterleavedRaster</code>.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class RawImage 
{
    private final BufferedImage image;
    private final int[] data;

    public RawImage(int width,int height) 
    {
        data = new int[ width*height ];

        final DataBufferInt dataBuffer = new DataBufferInt(data, width * height);

        final ColorModel cm = new DirectColorModel(24, 0xff0000, 0xff00, 0xff ); 
        final SampleModel sm = cm.createCompatibleSampleModel( width , height );
        final WritableRaster wr = Raster.createWritableRaster(sm, dataBuffer, null);

        image = new BufferedImage(cm, wr, false, null);
    }       

    public int[] getUniqueColors() 
    {
        final Set<Integer> result = new HashSet<Integer>();
        for ( int i = 0 ; i < data.length ; i++ ) {
            result.add( data[i] );
        }
        return ArrayUtils.toPrimitive( result.toArray( new Integer[ result.size() ] ));
    }

    public void fill(Color fillColor) {
    	getGraphics().setColor( fillColor );
    	getGraphics().fillRect( 0 , 0 ,getWidth() , getHeight() );
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