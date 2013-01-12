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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.TestCase;
import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.OpCode;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.io.ByteArrayObjectCodeWriterFactory;
import de.codesourcery.jasm16.emulator.IEmulator.EmulationSpeed;
import de.codesourcery.jasm16.emulator.exceptions.UnknownOpcodeException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.Misc;

public abstract class AbstractEmulatorTest extends TestCase
{
    /**
     * Max. time a single test may take before it is assumed
     * to be stuck in an endless loop and forcefully aborted.
     */
    public static final long MAX_TIME_PER_TEST_MILLIS = 2 * 1000; // 2 seconds 
            
    protected static final IEmulator emulator;
    
    protected CompiledCode compiledCode; 

    static {
        emulator = new Emulator();     
        emulator.calibrate();
    }
    
    protected final class CompiledCode 
    {
        public final byte[] objectCode;
        public final ICompilationUnit compilationUnit;

        protected CompiledCode(ICompilationUnit compilationUnit, byte[] objectCode)
        {
            this.compilationUnit = compilationUnit;
            this.objectCode = objectCode;
        }

        public IEmulator loadEmulator() {
            return loadEmulator(null);
        }
        
        public IEmulator loadEmulator(IEmulationListener l) {
            emulator.reset(true);
            emulator.loadMemory(compilationUnit.getObjectCodeStartOffset() , objectCode);      
            emulator.setEmulationSpeed( EmulationSpeed.REAL_SPEED );
            if ( l != null ) {
                emulator.addEmulationListener( l );
            }
            return emulator;
        }
    }      
    
    @Override
    protected final void setUp() throws Exception
    {
        setUpHook();
    }
    
    protected void setUpHook() throws Exception {
        
    }

    @Override
    protected final void tearDown() throws Exception
    {
        try {
            compiledCode = null;
            emulator.reset(true);
            emulator.removeAllEmulationListeners();
        } finally {
            tearDownHook();
        }
    }
    
    protected void tearDownHook() throws Exception {
    }
    
    // ==================== helper code ====================

    protected final void execute(String source) throws TimeoutException, InterruptedException {
        execute(source,MAX_TIME_PER_TEST_MILLIS,true);
    }
    
    protected final void execute(String source,long maxWaitTimeMillis) throws TimeoutException, InterruptedException {
        execute(source,maxWaitTimeMillis,true);
    }    
    
    protected final void execute(String source,long maxWaitTimeMillis,boolean waitForEmulatorToStop) throws TimeoutException, InterruptedException {

        compiledCode = compile(source);

        final CountDownLatch stopped = new CountDownLatch(1);
        final IEmulator emu;
        if (waitForEmulatorToStop) 
        {
            final IEmulationListener listener = new EmulationListener() {

                @Override
                protected void beforeContinuousExecutionHook()
                {
//                    System.out.println("*** Emulator started. ***");
                }
                
                @Override
                public void onStopHook(IEmulator emulator, Address previousPC, Throwable emulationError)
                {
                    boolean suppressError = false;
                    if ( emulationError instanceof UnknownOpcodeException)
                     {
                        final UnknownOpcodeException ex = (UnknownOpcodeException) emulationError;
                        if ( OpCode.isHaltInstruction( ex.getInstructionWord() ) ) 
                        {
                            suppressError = true;
                        }
                    }
                    if ( ! suppressError ) {
                        System.out.println("*** Emulator stopped "+( emulationError != null ? "("+emulationError.getMessage() +")": "" )+" ***");
                    }
                    stopped.countDown();
                }
            };
            emu = compiledCode.loadEmulator(  listener );
        } else {
            emu = compiledCode.loadEmulator();            
        }
        emu.start();
        
        if ( ! waitForEmulatorToStop ) {
            return;
        }

        while( true ) 
        {
            try 
            {
                if ( ! stopped.await( maxWaitTimeMillis , TimeUnit.MILLISECONDS) ) {
                    emu.stop();
                    throw new TimeoutException("Emulator did not stop after "+MAX_TIME_PER_TEST_MILLIS+" milliseconds - maybe stuck in an infinite loop?");
                }
                break;
            } catch (InterruptedException e) {
                emu.stop();
                throw e;
            }
        }
    }

    private CompiledCode compile(String source) 
    {
        final ICompiler c = new de.codesourcery.jasm16.compiler.Compiler();
        c.setCompilerOption(CompilerOption.RELAXED_PARSING,true);

        final ByteArrayObjectCodeWriterFactory factory = new ByteArrayObjectCodeWriterFactory();
        c.setObjectCodeWriterFactory( factory );

        final ICompilationUnit unit = CompilationUnit.createInstance("string" , source );
        c.compile( Collections.singletonList( unit ) );

        if ( unit.hasErrors() ) 
        {
            Misc.printCompilationErrors(unit, source, false );
            fail("Failed to compile source");
        }

        final byte[] objectCode = factory.getBytes();
        assertNotNull( "NULL object code" , objectCode );
        assertTrue( "no object code generated?" , objectCode.length > 0 );       
        return new CompiledCode( unit , objectCode );
    }

    protected final void assertOnTopOfStack(int value) {
        final int actualValue = emulator.getMemory().read( emulator.getCPU().getSP() );
        assertEquals( "Expected a different value on top-of-stack" , value , actualValue );
    }
    
    protected final void assertMemoryValue(int value,String label) 
    {
        final int actualValue = emulator.getMemory().read( getLabelAddress(label) );
        assertEquals( "Expected a different value in memory at label '"+label+"'", value , actualValue );
    }   
    
    protected final Address getLabelAddress(String label) 
    {
        final ISymbol symbol;
        try {
            symbol = compiledCode.compilationUnit.getSymbolTable().getSymbol(new Identifier(label) );
        } catch (ParseException e) {
            throw new RuntimeException("Not a valid label: '"+label+"'",e);
        }

        assertNotNull("Unknown symbol '"+label+"'",symbol);
        assertTrue("Symbol '"+label+"' is no label but a "+symbol,symbol instanceof Label);
        
        final Label l = (Label) symbol;
        assertNotNull( "Address of "+l+" has no been resolved?" , l.getAddress() );
        return l.getAddress();
    }
    
    protected final void assertMemoryValue(Address expectedValue,Address address) 
    {
        final Address actualValue = Address.wordAddress( emulator.getMemory().read( address ) );    	
        assertEquals( "Expected a different value in memory at address "+address , expectedValue , actualValue );
    }
    
    protected final void assertMemoryValue(int value,Address address) 
    {
        final int actualValue = emulator.getMemory().read( address );
        assertEquals( "Expected a different value in memory at address "+address , value , actualValue );
    }
    
    protected final void assertRegIA(int value) {
    	assertEquals("Interrupt handler register has unexpected value" , Address.wordAddress( value ) , emulator.getCPU().getInterruptAddress());
    }    
    protected final void assertRegA(int value) { assertRegister(Register.A,value) ; }
    protected final void assertRegB(int value) { assertRegister(Register.B,value) ; }
    protected final void assertRegC(int value) { assertRegister(Register.C,value) ; }
    protected final void assertRegX(int value) { assertRegister(Register.X,value) ; }
    protected final void assertRegY(int value) { assertRegister(Register.Y,value) ; }
    protected final void assertRegZ(int value) { assertRegister(Register.Z,value) ; }
    protected final void assertRegI(int value) { assertRegister(Register.I,value) ; }
    protected final void assertRegJ(int value) { assertRegister(Register.J,value) ; }
    protected final void assertRegSP(int value) { assertRegister(Register.SP,value) ; }
    
    protected final void assertRegA(Address value) { assertRegister(Register.A,value) ; }
    protected final void assertRegB(Address value) { assertRegister(Register.B,value) ; }
    protected final void assertRegC(Address value) { assertRegister(Register.C,value) ; }
    protected final void assertRegX(Address value) { assertRegister(Register.X,value) ; }
    protected final void assertRegY(Address value) { assertRegister(Register.Y,value) ; }
    protected final void assertRegZ(Address value) { assertRegister(Register.Z,value) ; }
    protected final void assertRegI(Address value) { assertRegister(Register.I,value) ; }
    protected final void assertRegJ(Address value) { assertRegister(Register.J,value) ; }
    protected final void assertRegSP(Address value) { assertRegister(Register.SP,value) ; }    
    
    protected final void assertRegASigned(int value) { assertRegisterSigned(Register.A,value) ; }
    protected final void assertRegBSigned(int value) { assertRegisterSigned(Register.B,value) ; }
    protected final void assertRegCSigned(int value) { assertRegisterSigned(Register.C,value) ; }
    protected final void assertRegXSigned(int value) { assertRegisterSigned(Register.X,value) ; }
    protected final void assertRegYSigned(int value) { assertRegisterSigned(Register.Y,value) ; }
    protected final void assertRegZSigned(int value) { assertRegisterSigned(Register.Z,value) ; }
    protected final void assertRegISigned(int value) { assertRegisterSigned(Register.I,value) ; }
    protected final void assertRegJSigned(int value) { assertRegisterSigned(Register.J,value) ; }
    
    protected final void assertRegEX(int value) { assertRegister(Register.EX,value) ; }
    
    protected final void assertRegister(Register reg,int expected) {
        assertEquals( "Register "+reg+" has unexpected value", expected , emulator.getCPU().getRegisterValue(reg) );
    }   
    
    protected final void assertRegister(Register reg,Address expected) {
        assertEquals( "Register "+reg+" has unexpected value", expected , Address.wordAddress( emulator.getCPU().getRegisterValue(reg) ) );
    }     
    
    protected final void assertRegisterSigned(Register reg,int expected) 
    {
        final int actual = signExtend16(  emulator.getCPU().getRegisterValue(reg) );
        assertEquals( "Register "+reg+" has unexpected value", expected , actual );
    }      
    
    protected final int signExtend16(int value) {
        if ( (value & 1<<15) != 0 ) { // MSB set ?
            return 0xffff0000 | value;
        }
        return value;
    }
}
