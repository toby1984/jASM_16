package de.codesourcery.jasm16.emulator;

import java.util.Collections;

import junit.framework.TestCase;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.io.ByteArrayObjectCodeWriterFactory;

public class EmulatorTest extends TestCase
{
    public void testEmulator() throws InterruptedException {
        
        final String source = ":label SET a,1\n"+
                              "       SET PC,label";
        
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
        emu.load(unit.getObjectCodeStartOffset() , objectCode);
        
        emu.start();
        Thread.sleep( 3 * 1000 );
        emu.stop();
    }
}
