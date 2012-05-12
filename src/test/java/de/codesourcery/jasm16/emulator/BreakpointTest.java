package de.codesourcery.jasm16.emulator;

import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import junit.framework.TestCase;
import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.exceptions.ParseException;

public class BreakpointTest extends TestCase {

	public void testConditionalBreakpoint1() throws ParseException {
		
		final Breakpoint bp = new Breakpoint( Address.wordAddress( 1 ) , "1" );
		
		final IEmulator emulator = createMock(IEmulator.class);
		
		replay( emulator );
		
		assertTrue( bp.matches( emulator ) );
		verify( emulator );
	}
	
	public void testConditionalBreakpoint2() throws ParseException {
		
		final Breakpoint bp = new Breakpoint( Address.wordAddress( 1 ) , "1+2" );
		
		final IEmulator emulator = createMock(IEmulator.class);
		
		replay( emulator );
		
		assertTrue( bp.matches( emulator ) );
		verify( emulator );
	}	
	
	public void testConditionalBreakpointWithRegister() throws ParseException {
		
		final Breakpoint bp = new Breakpoint( Address.wordAddress( 1 ) , "1+a" );
		
		final IEmulator emulator = createMock(IEmulator.class);
		
		final ICPU cpu = createMock(ICPU.class );
		expect( cpu.getRegisterValue( Register.A ) ).andReturn( 42 );
		replay( cpu );
		expect( emulator.getCPU() ).andReturn( cpu );
		replay( emulator );
		
		assertTrue( bp.matches( emulator ) );
		
		verify( cpu );
		verify( emulator );
	}	
	
	public void testConditionalBreakpointWithRegisterIndirect() throws ParseException {
		
		final Breakpoint bp = new Breakpoint( Address.wordAddress( 1 ) , "1+[a]" );
		
		final ICPU cpu = createMock(ICPU.class );
		
		expect( cpu.getRegisterValue( Register.A ) ).andReturn( 42 );
		replay( cpu );
		
		final IReadOnlyMemory memory = createMock(IReadOnlyMemory.class);
		
		expect( memory.read( 42 ) ).andReturn( 1 );
		replay( memory );
		
		final IEmulator emulator = createMock(IEmulator.class);
		expect( emulator.getCPU() ).andReturn( cpu );
		expect( emulator.getMemory() ).andReturn( memory );
		replay( emulator );
		
		assertTrue( bp.matches( emulator ) );
		
		verify( cpu );
		verify( emulator );
		verify( memory );
	}	
	
	public void testConditionalBreakpointWithMemoryDirect() throws ParseException {
		
		final Breakpoint bp = new Breakpoint( Address.wordAddress( 1 ) , "1+[0x4000]" );
		
		final IReadOnlyMemory memory = createMock(IReadOnlyMemory.class);
		
		expect( memory.read( 0x4000 ) ).andReturn( 42 );
		replay( memory );
		
		final IEmulator emulator = createMock(IEmulator.class);
		expect( emulator.getMemory() ).andReturn( memory );
		replay( emulator );
		
		assertTrue( bp.matches( emulator ) );
		
		verify( emulator );
		verify( memory );
	}	
	
	public void testComplexConditional() throws ParseException {
		
		final Breakpoint bp = new Breakpoint( Address.wordAddress( 1 ) , "1+0x42+[a]+x+[0x4000]" );
		
		final ICPU cpu = createMock(ICPU.class );
		
		expect( cpu.getRegisterValue( Register.A ) ).andReturn( 42 );
		expect( cpu.getRegisterValue( Register.X ) ).andReturn( 43 );
		replay( cpu );
		
		final IReadOnlyMemory memory = createMock(IReadOnlyMemory.class);
		
		expect( memory.read( 42 ) ).andReturn( 1 );
		expect( memory.read( 0x4000 ) ).andReturn( 49 );
		replay( memory );
		
		final IEmulator emulator = createMock(IEmulator.class);
		expect( emulator.getCPU() ).andReturn( cpu ).times(2);
		expect( emulator.getMemory() ).andReturn( memory ).times(2);
		replay( emulator );
		
		assertTrue( bp.matches( emulator ) );
		
		verify( cpu );
		verify( emulator );
		verify( memory );
	}	
	
}
