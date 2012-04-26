package de.codesourcery.jasm16.emulator;

import java.util.Collections;

import junit.framework.TestCase;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.io.ByteArrayObjectCodeWriterFactory;

public class SimpleSimulationTest extends TestCase
{
    public static void main(String[] args) throws InterruptedException
    {
        new SimpleSimulationTest().testEmulator();
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
        
        final SimpleSimulation emu = new SimpleSimulation();
        
        emu.memoryBulkLoad(unit.getObjectCodeStartOffset() , objectCode);
        
        emu.start();
        Thread.sleep( 20 * 1000 );
        emu.stop();
    }
}
