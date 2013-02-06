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
package de.codesourcery.jasm16.utils;

import java.text.DecimalFormat;

public final class Vector4 
{
    private float[] data;
    private int offset=0;
    
    public Vector4(Vector4 input) 
    {
        data = new float[4];
        input.copyInto( data , 0 );
    }
    
    public int toRGB() {
        int color = ((int) ( r() *255f) ) << 16;
        color |= ((int) ( g() *255f) ) << 8;
        color |= ((int) ( b() *255f) );
        return color;
    }
    
    public Vector4() {
        data = new float[4];
    }
    
    public Vector4(float[] data) {
        this.data = data;
    }    
    
    public void setData(float[] data,int offset) {
        this.data = data;
        this.offset = offset;
    }
    
    public void copyFrom(Vector4 other)
    {
    	final int otherOffset = other.getDataOffset();
    	final float[] otherData = other.getDataArray();
    	
        this.data[offset] = otherData[ otherOffset ];
        this.data[offset+1] = otherData[ otherOffset+1 ];
        this.data[offset+2] = otherData[ otherOffset+2 ];
        this.data[offset+3] = otherData[ otherOffset+3 ];
    }    
    
    public void copyInto(float[] array,int startingOffset) 
    {
        array[startingOffset] = this.data[offset];
        array[startingOffset+1] = this.data[offset+1];
        array[startingOffset+2] = this.data[offset+2];
        array[startingOffset+3] = this.data[offset+3];
    }
    
    public Vector4(float[] data,int offset) 
    {
        this.data = data;
        this.offset = offset;
    }
    
    public boolean isEquals(Vector4 other) {
        return this.x() == other.x() &&
                this.y() == other.y() &&
                this.z() == other.z() &&
                this.w() == other.w();
    }
    
    public void x(float value) {
        this.data[ offset ] = value;
    }
    
    public void r(float value) {
        this.data[ offset ] = value;
    }    
    
    public void y(float value) {
        this.data[ offset +1 ] = value;        
    }
    
    public void g(float value) {
        this.data[ offset +1 ] = value;        
    }    
    
    public void z(float value) {
        this.data[ offset +2 ] = value;           
    }
    
    public void b(float value) {
        this.data[ offset +2 ] = value;           
    }    
    
    public void w(float value) {
        this.data[ offset + 3 ] = value;  
    }
    
    public void a(float value) {
        this.data[ offset + 3 ] = value;  
    }    
    
    public float x() {
        return this.data[ offset ];
    }
    
    public float r() {
        return this.data[ offset ];
    }    
    
    public float y() {
        return this.data[ offset + 1 ];
    }
    
    public float g() {
        return this.data[ offset + 1 ];
    }    
    
    public float z() {
        return this.data[ offset + 2 ];
    }
    
    public float b() {
        return this.data[ offset + 2 ];
    }    
    
    public float w() {
        return this.data[ offset + 3];
    }
    
    public float a() {
        return this.data[ offset + 3];
    }    
    
    public Vector4 minus(Vector4 other) 
    {
        // TODO: Maybe it's faster to use a loop here ? Needs benchmarking
        return new Vector4( this.x() - other.x() , this.y() - other.y() , this.z() - other.z() , w() );
    }
    
    public float distanceTo(Vector4 point) 
    {
    	float x = this.x() - point.x();
    	float y = this.y() - point.y();
    	float z = this.z() - point.z();
    	return (float) Math.sqrt( x*x + y*y + z*z );
    }
    
    public Vector4 plus(Vector4 other) {
     // TODO: Maybe it's faster to use a loop here ? Needs benchmarking
        return new Vector4( this.x() + other.x() , this.y() + other.y() , this.z() + other.z() , w() );
    }        
    
    public Vector4(float x,float y,float z) {
        this(x,y,z,1);
    }
    
    public Vector4(float x,float y,float z,float w) 
    {
        this.data = new float[] { x , y , z , w };
    }
    
    public Vector4 multiply( Matrix matrix) 
    {
        final float[] result = new float[4];
        
        final float[] thisData = this.data;
        final float[] matrixData = matrix.getData();

        final int offset = this.offset;
        
        result[0] = thisData[ offset ] * matrixData[0] + thisData[offset+1] * matrixData[1]+
                    thisData[offset+2] * matrixData[2]+ thisData[offset+3] * matrixData[3];
        
        result[1] = thisData[ offset ] * matrixData[4] + thisData[offset+1] * matrixData[5] +
                    thisData[offset+2] * matrixData[6] + thisData[offset+3] * matrixData[7];
        
        result[2] = thisData[ offset ] * matrixData[8] + thisData[offset+1] * matrixData[9] +
                    thisData[offset+2] * matrixData[10] + thisData[offset+3] * matrixData[11];
        
        result[3] = thisData[ offset ] * matrixData[12] + thisData[offset+1] * matrixData[13] +
                    thisData[offset+2] * matrixData[14] + thisData[offset+3] * matrixData[15];
        
        return new Vector4( result );
    }
    
    public float[] getDataArray()
    {
        return data;
    }
    
    public int getDataOffset()
    {
        return offset;
    }    
    
    public float length() 
    {
        return (float) Math.sqrt( x()*x() + y()*y() + z()*z() );   
    }
    
    public Vector4 multiply(float value) 
    {
        return new Vector4( x()*value , y()*value , z()*value , w() );
    }
    
    public Vector4 normalize() 
    {
        final float len = length();
        if ( len  == 0 ) {
        	return new Vector4(0,0,0); 
        }
        return new Vector4( x() / len , y() / len , z() / len  , w() );
    }
    
    public void normalizeInPlace() 
    {
        final float len = length();
        if ( len  != 0 && len != 1 ) 
        {
        	this.data[offset] = this.data[offset] / len;
        	this.data[offset+1] = this.data[offset+1] / len;
        	this.data[offset+2] = this.data[offset+2] / len;
        }
    }    
    
    public Vector4 normalizeW() 
    {
        float w = w();
        if ( w != 1.0 ) 
        {
            return new Vector4( x() / w, y() / w , z() / w , 1 );
        }
        return this;
    }    
    
    public void normalizeWInPlace() 
    {
        float w = w();
        if ( w != 1.0 ) 
        {
        	x( x() / w );
        	y( y() / w );
        	z( z() / w );
        }
    }      
    
    // scalar / dot product
    public float dotProduct(Vector4 o) 
    {
        return data[offset]*o.data[o.offset] + data[offset+1]*o.data[o.offset+1]+data[offset+2]*o.data[o.offset+2];
    }
    
    public float angleInRadians(Vector4 o) {
        // => cos
        final float cosine = dotProduct( o ) / ( length() * o.length() );
        return (float) Math.acos( cosine );
    }
    
    public float angleInDegrees(Vector4 o) {
        final float factor = (float) (180.0f / Math.PI);
        return angleInRadians(o)*factor;
    }        
    
    public Vector4 crossProduct(Vector4 other) 
    {
        final float[] thisData = this.data;
        final int thisOffset = this.offset;
        
        final float[] o = other.data;
        final int oOffset = other.offset;
        
        final float x1 = thisData[thisOffset];
        final float y1 = thisData[thisOffset+1];
        final float z1 = thisData[thisOffset+2];
        
        final float x2 = o[ oOffset ];
        final float y2 = o[ oOffset+1 ];
        final float z2 = o[ oOffset+2 ];
        
        float newX = y1 * z2 - y2 * z1;
        float newY = z1 * x2 - z2 * x1;
        float newZ = x1 * y2 - x2 * y1;
        
        return new Vector4( newX ,newY,newZ );
    }
    
    @Override
    public String toString()
    {
        return "("+format( x() ) +","+format( y() ) +","+format( z() )+","+format( w() )+")";
    }
    
    private static String format(float d) {
        return new DecimalFormat("##0.0###").format( d );
    }
}