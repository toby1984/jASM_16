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

import org.apache.commons.lang.StringUtils;

public class Matrix 
{
 public static final int SIZE = 4; // 4x4 matrix
    /*
     * Matrix coefficients are stored in column-major order:
     * 
     * 0 4  8 12
     * 1 5  9 13
     * 2 6 10 14
     * 3 7 11 15
     * 
     */
    private final float[] data;

    public Matrix(Matrix other) 
    {
        this.data = new float[16];
        for ( int i = 0 ; i < 16 ;i++) {
            this.data[i] = other.data[i];
        }
    }

    /**
     * Creates an empty 4x4 matrix.
     */
    public Matrix() {
        this.data = new float[SIZE*SIZE];
    }

    public Matrix(float[] data) 
    {
        if ( data.length != SIZE*SIZE ) {
            throw new IllegalArgumentException("Invalid array length: "+data.length);
        }
        this.data = data;
    }

    public Vector4 getColumn(int col) 
    {
        int offset = col*4;
        return new Vector4( data[offset] , data[offset+1] , data[offset+2] , data[offset+3] );
    }

    public Vector4 getRow(int row) 
    {
        int offset = row;
        return new Vector4( data[offset] , data[offset+4] , data[offset+8] , data[offset+12] );
    }

    /**
     * Creates a 4x4 matrix from COLUMN vectors.
     * 
     * @param v1
     * @param v2
     * @param v3
     * @param v4
     */
    public Matrix(Vector4 v1,Vector4 v2,Vector4 v3,Vector4 v4) 
    {
        this.data = new float[ SIZE*SIZE ];
        v1.copyInto( this.data , 0 );
        v2.copyInto( this.data , 4 );
        v3.copyInto( this.data , 8 );
        v4.copyInto( this.data , 12 );
    }

    public void set(int column,int row,float value) {
        this.data[ column*SIZE + row ] = value;
    }

    public float get(int column,int row) {
        return this.data[ column*SIZE + row ];
    }

    /**
     * Multiply by another 4x4 matrix.
     * 
     * @param other
     * @return
     */
    public Matrix multiply(Matrix other) {
        return new Matrix( multiply( other , new float[ SIZE*SIZE ] ) );
    }
    
    /**
     * Multiply by another 4x4 matrix.
     * 
     * @param other
     * @param target target array where results should be stored (in column-major order)
     * @return
     */
    public float[] multiply(Matrix other,float[] target) {

        target[ 0 ] = this.data[ 0 ] * other.data[ 0 ] + this.data[ 4 ] * other.data[ 1 ] + 
                this.data[ 8 ] * other.data[ 2 ] + this.data[ 12 ] * other.data[ 3 ];

        target[ 1 ] = this.data[ 1 ] * other.data[ 0 ] + this.data[ 5 ] * other.data[ 1 ] + 
                this.data[ 9 ] * other.data[ 2 ] + this.data[ 13 ] * other.data[ 3 ];

        target[ 2 ] = this.data[ 2 ] * other.data[ 0 ] + this.data[ 6 ] * other.data[ 1 ] + 
                this.data[ 10 ] * other.data[ 2 ] + this.data[ 14 ] * other.data[ 3 ];

        target[ 3 ] = this.data[ 3 ] * other.data[ 0 ] + this.data[ 7 ] * other.data[ 1 ] + 
                this.data[ 11 ] * other.data[ 2 ] + this.data[ 15 ] * other.data[ 3 ];

        target[ 4 ] = this.data[ 0 ] * other.data[ 4 ] + this.data[ 4 ] * other.data[ 5 ] + 
                this.data[ 8 ] * other.data[ 6 ] + this.data[ 12 ] * other.data[ 7 ];

        target[ 5 ] = this.data[ 1 ] * other.data[ 4 ] + this.data[ 5 ] * other.data[ 5 ] + 
                this.data[ 9 ] * other.data[ 6 ] + this.data[ 13 ] * other.data[ 7 ];

        target[ 6 ] = this.data[ 2 ] * other.data[ 4 ] + this.data[ 6 ] * other.data[ 5 ] + 
                this.data[ 10 ] * other.data[ 6 ] + this.data[ 14 ] * other.data[ 7 ];

        target[ 7 ] = this.data[ 3 ] * other.data[ 4 ] + this.data[ 7 ] * other.data[ 5 ] + 
                this.data[ 11 ] * other.data[ 6 ] + this.data[ 15 ] * other.data[ 7 ];

        target[ 8 ] = this.data[ 0 ] * other.data[ 8 ] + this.data[ 4 ] * other.data[ 9 ] + 
                this.data[ 8 ] * other.data[ 10 ] + this.data[ 12 ] * other.data[ 11 ];

        target[ 9 ] = this.data[ 1 ] * other.data[ 8 ] + this.data[ 5 ] * other.data[ 9 ] + 
                this.data[ 9 ] * other.data[ 10 ] + this.data[ 13 ] * other.data[ 11 ];

        target[ 10 ] = this.data[ 2 ] * other.data[ 8 ] + this.data[ 6 ] * other.data[ 9 ] + 
                this.data[ 10 ] * other.data[ 10 ] + this.data[ 14 ] * other.data[ 11 ];

        target[ 11 ] = this.data[ 3 ] * other.data[ 8 ] + this.data[ 7 ] * other.data[ 9 ] + 
                this.data[ 11 ] * other.data[ 10 ] + this.data[ 15 ] * other.data[ 11 ];

        target[ 12 ] = this.data[ 0 ] * other.data[ 12 ] + this.data[ 4 ] * other.data[ 13 ] + 
                this.data[ 8 ] * other.data[ 14 ] + this.data[ 12 ] * other.data[ 15 ];

        target[ 13 ] = this.data[ 1 ] * other.data[ 12 ] + this.data[ 5 ] * other.data[ 13 ] + 
                this.data[ 9 ] * other.data[ 14 ] + this.data[ 13 ] * other.data[ 15 ];

        target[ 14 ] = this.data[ 2 ] * other.data[ 12 ] + this.data[ 6 ] * other.data[ 13 ] + 
                this.data[ 10 ] * other.data[ 14 ] + this.data[ 14 ] * other.data[ 15 ];

        target[ 15 ] = this.data[ 3 ] * other.data[ 12 ] + this.data[ 7 ] * other.data[ 13 ] + 
                this.data[ 11 ] * other.data[ 14 ] + this.data[ 15 ] * other.data[ 15 ];

        return target;
    }

    // code used to generate the above:
    
    //	public static void main(String[] args)
    //    {
    //	    int targetPtr = 0;
    //        for ( int dstCol = 0 ; dstCol < 4 ; dstCol ++) 
    //        {
    //            for ( int srcRow = 0 ; srcRow < 4 ; srcRow++ ) {
    //                System.out.print("target[ "+targetPtr+" ] = this.data[ "+srcRow+" ] * other.data[ "+(dstCol*SIZE)+" ] + ");
    //                System.out.print("this.data[ "+(srcRow+SIZE)+" ] * other.data[ "+(dstCol*SIZE+1)+" ] + \n");
    //                System.out.print("              this.data[ "+(srcRow+SIZE*2)+" ] * other.data[ "+(dstCol*SIZE+2)+" ] + ");
    //                System.out.print("this.data[ "+(srcRow+SIZE*3)+" ] * other.data[ "+(dstCol*SIZE+3)+" ];\n\n");
    //                targetPtr++;
    //            }
    //        }
    //    }

    public static Matrix identity() 
    {
        Matrix result = new Matrix();
        result.setIdentity();
        return result;
    }

    public void setIdentity() 
    {
        for ( int i = 0 ; i < data.length ; i++ ) {
            data[i] =0;
        }

        /* 1 0 0 0
         * 0 1 0 0
         * 0 0 1 0
         * 0 0 0 1
         */
        set( 0 , 0 , 1 );
        set( 1 , 1 , 1 );
        set( 2 , 2 , 1 );
        set( 3 , 3 , 1 );
    }	

    public static Matrix scale(float factor) 
    {
        float[] data = new float[] {
                factor , 0 , 0 , 0 ,
                0 , factor , 0 , 0 ,
                0 , 0 , factor , 0 ,
                0 , 0 , 0 , 1 ,
        };
        return new Matrix( data );
    }   

    public static Matrix scale(float scaleX,float scaleY,float scaleZ) 
    {
        float[] data = new float[] {
                scaleX , 0 , 0 , 0 ,
                0 , scaleY , 0 , 0 ,
                0 , 0 , scaleZ , 0 ,
                0 , 0 , 0 , 1 ,
        };
        return new Matrix( data );
    }     

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        for ( int y = 0 ; y < SIZE ; y++ ) 
        {
            for ( int x = 0 ; x < SIZE ; x++ ) {
                builder.append( format( get( x , y ) ) );
            }
            if ( (y+1) < SIZE ) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private static String format(float v) {
        final DecimalFormat df = new DecimalFormat("##0.0##");
        return StringUtils.leftPad( df.format( v ) , 6 );
    }

    public Matrix transpose() {

        Matrix result = new Matrix();

        for ( int row = 0 ; row < SIZE ; row++ ) {
            for ( int col = 0 ; col < SIZE ; col++ ) {
                result.set( row , col , get( col , row ) );
            }
        }
        return result;
    }

    public Vector4[] multiply(Vector4[] input)
    {
        final Vector4[] transformed = new Vector4[ input.length ];
        int i = 0;
        for ( Vector4 vector4 : input) 
        {
            final float[] result = new float[4];
            final float[] thisData = this.data;

            final int offset = vector4.getDataOffset(); 
            final float[] data = vector4.getDataArray();

            result[0] = this.data[ 0 ] * data[ offset ] + thisData[ 0 + SIZE ] * data[ offset+1 ] +  
                    thisData[ 0 + SIZE*2 ] * data[ offset+2 ] + thisData[ 0 + SIZE*3 ] * data[ offset+3 ];

            result[ 1 ] = thisData[ 1 ] * data[ offset ] + thisData[ 1 + SIZE ] * data[ offset+1 ] + thisData[ 1 + SIZE*2 ] * data[ offset+2 ]+
                    thisData[ 1 + SIZE*3 ] * data[ offset+3 ];

            result[ 2 ] = thisData[ 2 ] * data[ offset ] + thisData[ 2 + SIZE ] * data[ offset+1 ]+
                    thisData[ 2 + SIZE*2 ] * data[ offset+2 ] + thisData[ 2 + SIZE*3 ] * data[ offset+3 ];

            result [ 3] = thisData[ 3 ] * data[ offset+0 ]+ thisData[ 3 + SIZE ] * data[ offset+1 ]+ thisData[ 3 + SIZE*2 ] * data[ offset+2 ]+
                    thisData[ 3 + SIZE*3 ] * data[ offset+3 ];
            
            transformed[i++] = new Vector4( result );
        }
        return transformed;
    }
    
    public float[] multiply(float[] vectorData)
    {
        final float[] result = new float[ vectorData.length ];

        final float[] thisData = this.data;
        
        int i = 0;
        for ( int offset = 0 ; offset < vectorData.length ; offset += 4) 
        {
            result[i++] = this.data[ 0 ] * vectorData[ offset ] + thisData[ 0 + SIZE ] * vectorData[ offset+1 ] +  
                    thisData[ 0 + SIZE*2 ] * vectorData[ offset+2 ] + thisData[ 0 + SIZE*3 ] * vectorData[ offset+3 ];

            result[ i++ ] = thisData[ 1 ] * vectorData[ offset ] + thisData[ 1 + SIZE ] * vectorData[ offset+1 ] + thisData[ 1 + SIZE*2 ] * vectorData[ offset+2 ]+
                    thisData[ 1 + SIZE*3 ] * vectorData[ offset+3 ];

            result[ i++ ] = thisData[ 2 ] * vectorData[ offset ] + thisData[ 2 + SIZE ] * vectorData[ offset+1 ]+
                    thisData[ 2 + SIZE*2 ] * vectorData[ offset+2 ] + thisData[ 2 + SIZE*3 ] * vectorData[ offset+3 ];

            result [ i++ ] = thisData[ 3 ] * vectorData[ offset+0 ]+ thisData[ 3 + SIZE ] * vectorData[ offset+1 ]+ thisData[ 3 + SIZE*2 ] * vectorData[ offset+2 ]+
                    thisData[ 3 + SIZE*3 ] * vectorData[ offset+3 ];
        }
        return result;
    }     

    public Vector4 multiply(Vector4 vector4)
    {
        final float[] result = new float[4];
        final float[] thisData = this.data;

        final int offset = vector4.getDataOffset(); 
        final float[] data = vector4.getDataArray();

        result[0] = this.data[ 0 ] * data[ offset ] + thisData[ 0 + SIZE ] * data[ offset+1 ] +  
                thisData[ 0 + SIZE*2 ] * data[ offset+2 ] + thisData[ 0 + SIZE*3 ] * data[ offset+3 ];

        result[ 1 ] = thisData[ 1 ] * data[ offset ] + thisData[ 1 + SIZE ] * data[ offset+1 ] + thisData[ 1 + SIZE*2 ] * data[ offset+2 ]+
                thisData[ 1 + SIZE*3 ] * data[ offset+3 ];

        result[ 2 ] = thisData[ 2 ] * data[ offset ] + thisData[ 2 + SIZE ] * data[ offset+1 ]+
                thisData[ 2 + SIZE*2 ] * data[ offset+2 ] + thisData[ 2 + SIZE*3 ] * data[ offset+3 ];

        result [ 3] = thisData[ 3 ] * data[ offset+0 ]+ thisData[ 3 + SIZE ] * data[ offset+1 ]+ thisData[ 3 + SIZE*2 ] * data[ offset+2 ]+
                thisData[ 3 + SIZE*3 ] * data[ offset+3 ];        

        return new Vector4( result );
    }

    public void multiplyInPlace(Vector4[] data) 
    {
        for ( Vector4 v : data ) {
            multiplyInPlace( v );
        }
    }

    public void multiplyInPlaceAndNormalizeW(Vector4[] data) 
    {
        for ( Vector4 v : data ) {
            multiplyInPlace( v );
            v.normalizeWInPlace();
        }
    }    

    public void multiplyInPlace(Vector4 vector4)
    {
        final float[] thisData = this.data;

        final int offset = vector4.getDataOffset(); 
        final float[] data = vector4.getDataArray();
        
        final float x = this.data[ 0 ] * data[ offset ] + thisData[ 0 + SIZE ] * data[ offset+1 ] +  
                thisData[ 0 + SIZE*2 ] * data[ offset+2 ] + thisData[ 0 + SIZE*3 ] * data[ offset+3 ];

        final float y = thisData[ 1 ] * data[ offset ] + thisData[ 1 + SIZE ] * data[ offset+1 ] + thisData[ 1 + SIZE*2 ] * data[ offset+2 ]+
                thisData[ 1 + SIZE*3 ] * data[ offset+3 ];

        final float z = thisData[ 2 ] * data[ offset ] + thisData[ 2 + SIZE ] * data[ offset+1 ]+
                thisData[ 2 + SIZE*2 ] * data[ offset+2 ] + thisData[ 2 + SIZE*3 ] * data[ offset+3 ];

        final float w = thisData[ 3 ] * data[ offset ]+ thisData[ 3 + SIZE ] * data[ offset+1 ]+ thisData[ 3 + SIZE*2 ] * data[ offset+2 ]+
                thisData[ 3 + SIZE*3 ] * data[ offset+3 ];        

        data[ offset ] = x;
        data[ offset + 1 ] = y;
        data[ offset + 2 ] = z;
        data[ offset + 3 ] = w;
    }    

    public Matrix invert() {

        final float[] m = this.data;
        final float[] invOut = new float[16];

        float[] inv = new float[16];
        float det=0;
        int i = 0;

        inv[0] = m[5]  * m[10] * m[15] - 
                m[5]  * m[11] * m[14] - 
                m[9]  * m[6]  * m[15] + 
                m[9]  * m[7]  * m[14] +
                m[13] * m[6]  * m[11] - 
                m[13] * m[7]  * m[10];

        inv[4] = -m[4]  * m[10] * m[15] + 
                m[4]  * m[11] * m[14] + 
                m[8]  * m[6]  * m[15] - 
                m[8]  * m[7]  * m[14] - 
                m[12] * m[6]  * m[11] + 
                m[12] * m[7]  * m[10];

        inv[8] = m[4]  * m[9] * m[15] - 
                m[4]  * m[11] * m[13] - 
                m[8]  * m[5] * m[15] + 
                m[8]  * m[7] * m[13] + 
                m[12] * m[5] * m[11] - 
                m[12] * m[7] * m[9];

        inv[12] = -m[4]  * m[9] * m[14] + 
                m[4]  * m[10] * m[13] +
                m[8]  * m[5] * m[14] - 
                m[8]  * m[6] * m[13] - 
                m[12] * m[5] * m[10] + 
                m[12] * m[6] * m[9];

        inv[1] = -m[1]  * m[10] * m[15] + 
                m[1]  * m[11] * m[14] + 
                m[9]  * m[2] * m[15] - 
                m[9]  * m[3] * m[14] - 
                m[13] * m[2] * m[11] + 
                m[13] * m[3] * m[10];

        inv[5] = m[0]  * m[10] * m[15] - 
                m[0]  * m[11] * m[14] - 
                m[8]  * m[2] * m[15] + 
                m[8]  * m[3] * m[14] + 
                m[12] * m[2] * m[11] - 
                m[12] * m[3] * m[10];

        inv[9] = -m[0]  * m[9] * m[15] + 
                m[0]  * m[11] * m[13] + 
                m[8]  * m[1] * m[15] - 
                m[8]  * m[3] * m[13] - 
                m[12] * m[1] * m[11] + 
                m[12] * m[3] * m[9];

        inv[13] = m[0]  * m[9] * m[14] - 
                m[0]  * m[10] * m[13] - 
                m[8]  * m[1] * m[14] + 
                m[8]  * m[2] * m[13] + 
                m[12] * m[1] * m[10] - 
                m[12] * m[2] * m[9];

        inv[2] = m[1]  * m[6] * m[15] - 
                m[1]  * m[7] * m[14] - 
                m[5]  * m[2] * m[15] + 
                m[5]  * m[3] * m[14] + 
                m[13] * m[2] * m[7] - 
                m[13] * m[3] * m[6];

        inv[6] = -m[0]  * m[6] * m[15] + 
                m[0]  * m[7] * m[14] + 
                m[4]  * m[2] * m[15] - 
                m[4]  * m[3] * m[14] - 
                m[12] * m[2] * m[7] + 
                m[12] * m[3] * m[6];

        inv[10] = m[0]  * m[5] * m[15] - 
                m[0]  * m[7] * m[13] - 
                m[4]  * m[1] * m[15] + 
                m[4]  * m[3] * m[13] + 
                m[12] * m[1] * m[7] - 
                m[12] * m[3] * m[5];

        inv[14] = -m[0]  * m[5] * m[14] + 
                m[0]  * m[6] * m[13] + 
                m[4]  * m[1] * m[14] - 
                m[4]  * m[2] * m[13] - 
                m[12] * m[1] * m[6] + 
                m[12] * m[2] * m[5];

        inv[3] = -m[1] * m[6] * m[11] + 
                m[1] * m[7] * m[10] + 
                m[5] * m[2] * m[11] - 
                m[5] * m[3] * m[10] - 
                m[9] * m[2] * m[7] + 
                m[9] * m[3] * m[6];

        inv[7] = m[0] * m[6] * m[11] - 
                m[0] * m[7] * m[10] - 
                m[4] * m[2] * m[11] + 
                m[4] * m[3] * m[10] + 
                m[8] * m[2] * m[7] - 
                m[8] * m[3] * m[6];

        inv[11] = -m[0] * m[5] * m[11] + 
                m[0] * m[7] * m[9] + 
                m[4] * m[1] * m[11] - 
                m[4] * m[3] * m[9] - 
                m[8] * m[1] * m[7] + 
                m[8] * m[3] * m[5];

        inv[15] = m[0] * m[5] * m[10] - 
                m[0] * m[6] * m[9] - 
                m[4] * m[1] * m[10] + 
                m[4] * m[2] * m[9] + 
                m[8] * m[1] * m[6] - 
                m[8] * m[2] * m[5];

        det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];

        if (det == 0) {
            return null;
        }

        det = 1.0f / det;

        for (i = 0; i < 16; i++) {
            invOut[i] = inv[i] * det;
        }

        return new Matrix( invOut );
    }

    public float[] getData()
    {
        return this.data;
    }

	public void setColumns(Vector4 col0, Vector4 col1, Vector4 col2, Vector4 col3) 
	{
		int ptr = 0;
		for (int i = 0 ; i < 4 ; i++ ) {
			this.data[ ptr++ ] = col0.getDataArray()[ col0.getDataOffset() +i ];
		}
		for (int i = 0 ; i < 4 ; i++ ) {
			this.data[ ptr++ ] = col1.getDataArray()[ col1.getDataOffset() +i ];
		}
		for (int i = 0 ; i < 4 ; i++ ) {
			this.data[ ptr++ ] = col2.getDataArray()[ col2.getDataOffset() +i ];
		}
		for (int i = 0 ; i < 4 ; i++ ) {
			this.data[ ptr++ ] = col3.getDataArray()[ col3.getDataOffset() +i ];
		}		
	}

}
