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

import java.io.IOException;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.disassembler.ByteArrayMemoryAdapter;
import de.codesourcery.jasm16.emulator.memory.IMemory;

/**
 * Abstract super-class for floppy disk media implementations.
 * 
 * <p>Subclasses need to implement {@link #read(byte[], long)} and
 * {@link #write(byte[], long)}.</p>
 *
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class FloppyDisk {

    public static final int CAPACITY_IN_WORDS = 737280;

    public static final int TRACKS = 80;
    public static final int SECTORS_PER_TRACK = 18;

    private static final int SECTOR_COUNT = TRACKS * SECTORS_PER_TRACK;

    public static final int WORDS_PER_SECTOR = 512;

    private final String identifier;
    private volatile boolean writeProtected;

    public FloppyDisk(String identifier,boolean writeProtected) 
    {
        if (StringUtils.isBlank(identifier)) {
            throw new IllegalArgumentException("identifier must not be blank/null");
        }
        this.identifier = identifier;
        this.writeProtected = writeProtected;
    }

    public FloppyDisk(String identifier) 
    {
        this(identifier,false);
    }

    @SuppressWarnings("deprecation")
    public final void readSector(int sector,IMemory target,Address targetAddress) throws IOException 
    {
        final long byteOffset = sector * 512 * 2;
        final byte[] buffer = new byte[ WORDS_PER_SECTOR * 2 ];

        read( buffer , byteOffset );

        final ByteArrayMemoryAdapter mem = new ByteArrayMemoryAdapter( buffer );

        // TODO: Requires atomic memory write in IMemoryRegion

        int len = WORDS_PER_SECTOR;
        for ( int src = 0 , dst = targetAddress.getWordAddressValue() ; len > 0 ; len-- ) 
        {
            target.write( dst++ , mem.read( src++ ) );
        }
    }

    public abstract void read(byte[] buffer,long byteOffset) throws IOException;

    @SuppressWarnings("deprecation")
    public final void writeSector(int sector,IMemory source,Address sourceAddress) throws IOException 
    {
        final long byteOffset = sector * 512 * 2;
        final byte[] buffer = new byte[ WORDS_PER_SECTOR * 2 ];
        final ByteArrayMemoryAdapter mem = new ByteArrayMemoryAdapter( buffer );

        // TODO: Requires atomic memory write in IMemoryRegion

        int len = WORDS_PER_SECTOR;
        for ( int src = sourceAddress.getWordAddressValue() , dst = 0 ; len > 0 ; len-- ) 
        {
            mem.write( dst++ , source.read( src++ ) );
        }		
        write( buffer , byteOffset );
    }	

    public abstract void write(byte[] buffer, long byteOffset ) throws IOException;	

    public boolean isWriteProtected() {
        return writeProtected;
    }

    public void setWriteProtected(boolean writeProtected) {
        this.writeProtected = writeProtected;
    }

    public int getSectorCount() {
        return SECTOR_COUNT;
    }

    public Size getSectorSize() {
        return Size.words( WORDS_PER_SECTOR );
    }

    @Override
    public String toString() {
        return "disk '"+identifier+"'";
    }
}