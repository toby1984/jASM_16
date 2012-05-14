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
package de.codesourcery.jasm16.emulator;

import java.util.Collections;

import junit.framework.TestCase;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.io.ByteArrayObjectCodeWriterFactory;

public class EmulatorTest extends TestCase
{
    public static void main(String[] args) throws InterruptedException
    {
        new EmulatorTest().testEmulator();
    }
    
    public void testEmulator() throws InterruptedException {
        
        final String source = ":label SET a,1\n"+
                              "       ADD b ,1\n"+
                              "       ADD [stuff],1\n"+
                              "       SET c , [stuff]\n"+
                              "       SET PC,label\n"+
                              ":stuff .dat 0x000";
        
        final ICompiler c = new de.codesourcery.jasm16.compiler.Compiler();
        final ByteArrayObjectCodeWriterFactory factory = new ByteArrayObjectCodeWriterFactory();
        c.setObjectCodeWriterFactory( factory );
        
        final ICompilationUnit unit = CompilationUnit.createInstance("string" , source );
        
        c.compile( Collections.singletonList( unit ) );
        
        assertFalse( unit.hasErrors() );
        
        final byte[] objectCode = factory.getBytes();
        assertNotNull( objectCode );
        assertTrue( "bad size: "+objectCode.length , objectCode.length > 0 );
        
        final Emulator emu = new Emulator();
        emu.calibrate();
        emu.loadMemory(unit.getObjectCodeStartOffset() , objectCode);      
        emu.setRunAtRealSpeed( true );
        emu.start();
        Thread.sleep( 5 * 1000 );
        emu.stop();
    }
}
