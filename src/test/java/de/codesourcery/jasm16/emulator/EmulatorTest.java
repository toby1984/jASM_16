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

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.emulator.devices.DeviceDescriptor;
import de.codesourcery.jasm16.emulator.devices.IDevice;

public class EmulatorTest extends AbstractEmulatorTest
{
	/* ==========================
	 * ============= SET ========
	 * ========================== */

	@Override
	protected void setUpHook() throws Exception
	{
		emulator.setOutput( ILogger.NOP_LOGGER );
	}

	public void testRegisterSetImmediate() throws InterruptedException, TimeoutException {

		final String source = "      SET a,0x0a\n"+ // 10
				"       SET b,1\n"+
				"       SET c,b10\n"+ // 2
				"       SET x,3\n"+
				"       SET y,4\n"+
				"       SET z,5\n"+
				"       SET i,6\n"+
				"       SET j,7\n"+
				"       HCF 0";

		execute(source);

		assertRegA( 10 );
		assertRegB( 1 );
		assertRegC( 2 );
		assertRegX( 3 );
		assertRegY( 4 );
		assertRegZ( 5 );
		assertRegI( 6 );
		assertRegJ( 7 );
	}  

	public void testRegisterSetRegisterIndirectWithNegativeOffset() throws Exception {
		final String source = "\n" + 
				"SET a,label2\n" + 
				"SET b,[a-1]\n" + 
				"loop:\n" + 
				"HCF 1\n" + 
				":label\n" + 
				".dat 0xbeef\n" + 
				":label2\n" + 
				".dat 0";

		execute(source);

		assertRegB( 0xbeef );
	}

	public void testRegisterSetRegisterIndirect() throws InterruptedException, TimeoutException {

		final String source = "      SET a,value\n"+ // 10
				"       SET b,[a]\n"+
				"       SET c,[a]\n"+
				"       SET x,[a]\n"+
				"       SET y,[a]\n"+
				"       SET z,[a]\n"+
				"       SET i,[a]\n"+
				"       SET j,[a]\n"+
				"       HCF 0\n"+ // illegal opcode
				"value: .dat 0xbeef";

		execute(source);

		assertRegB( 0xbeef );
		assertRegC( 0xbeef );
		assertRegX( 0xbeef );
		assertRegY( 0xbeef );
		assertRegZ( 0xbeef );
		assertRegI( 0xbeef );
		assertRegJ( 0xbeef );
	}  

	public void testRegisterSetRegisterIndirectWithOffset() throws InterruptedException, TimeoutException {

		final String source = "      SET a,value\n"+ // 10
				"       SET b,[a+1]\n"+
				"       SET c,[a+1]\n"+
				"       SET x,[a+1]\n"+
				"       SET y,[a+1]\n"+
				"       SET z,[a+1]\n"+
				"       SET i,[a+1]\n"+
				"       SET j,[a+1]\n"+
				"       HCF 0\n"+ // illegal opcode
				"value: .dat 0\n"+
				"       .dat 0xbeef\n";

		execute(source);

		assertRegB( 0xbeef );
		assertRegC( 0xbeef );
		assertRegX( 0xbeef );
		assertRegY( 0xbeef );
		assertRegZ( 0xbeef );
		assertRegI( 0xbeef );
		assertRegJ( 0xbeef );
	}     

	public void testRegisterSetIndirect() throws InterruptedException, TimeoutException {

		final String source = "      SET a,[value]\n"+ // 10
				"       SET b,[value]\n"+
				"       SET c,[value]\n"+
				"       SET x,[value]\n"+
				"       SET y,[value]\n"+
				"       SET z,[value]\n"+
				"       SET i,[value]\n"+
				"       SET j,[value]\n"+
				"       HCF 0\n"+ // illegal opcode
				"value: .dat 0xbeef";

		execute(source);

		assertRegA( 0xbeef );
		assertRegB( 0xbeef );
		assertRegC( 0xbeef );
		assertRegX( 0xbeef );
		assertRegY( 0xbeef );
		assertRegZ( 0xbeef );
		assertRegI( 0xbeef );
		assertRegJ( 0xbeef );
	}   

	public void testRegisterSetFromStack() throws InterruptedException, TimeoutException {

		final String source = "      SET PUSH,0xdead\n"+ // 10 
				"      SET PUSH,1\n"+ // 10
				"      SET PUSH,2\n"+ // 10
				"      SET PUSH,3\n"+ // 10
				"      SET PUSH,4\n"+ // 10
				"      SET PUSH,5\n"+ // 10
				"      SET PUSH,6\n"+ // 10
				"      SET PUSH,7\n"+ // 10
				"      SET PUSH,8\n"+ // 10                
				"       SET A,POP\n"+                
				"       SET b,POP\n"+
				"       SET c,POP\n"+
				"       SET x,POP\n"+
				"       SET y,POP\n"+
				"       SET z,POP\n"+
				"       SET i,POP\n"+
				"       SET j,POP\n"+
				"       HCF 0\n"+ // illegal opcode
				"value: .dat 0xbeef";

		execute(source);

		assertRegA( 8 );
		assertRegB( 7 );
		assertRegC( 6 );
		assertRegX( 5 );
		assertRegY( 4 );
		assertRegZ( 3 );
		assertRegI( 2 );
		assertRegJ( 1 );

		assertOnTopOfStack( 0xdead );
	}    

	public void testMemorySetRegisterIndirectImmediate() throws InterruptedException, TimeoutException {

		final String source = 
				"       SET a,value\n"+
						"       SET [a],0xbeef\n"+ // 10
						"       HCF 0\n"+ // illegal opcode
						"value: .dat 0";

		execute(source);
		assertMemoryValue( 0xbeef, "value" );
	} 

	public void testSetStackImmediate() throws InterruptedException, TimeoutException {

		final String source = 
				"       SET PUSH,0xbeef\n"+ // 10
						"       HCF 0\n"; // illegal opcode

		execute(source);

		assertOnTopOfStack( 0xbeef );
	}     
	
    public void testSetFromPC() throws InterruptedException, TimeoutException {

        final String source = 
                "       SET a, 0x01\n" +
                "       SET a,PC\n"+ 
                        "       HCF 0\n"; // illegal opcode

        execute(source);
        assertRegA( 0x01 );
    }  	

	public void testMemorySetRegisterIndirectWithOffsetImmediate() throws InterruptedException, TimeoutException {

		final String source = 
				"       SET a,value\n"+
						"       SET [a+1],0xbeef\n"+ // 10
						"       HCF 0\n"+ // illegal opcode
						"value: .dat 0\n"+
						"value2: .dat 0";

		execute(source);
		assertMemoryValue( 0xbeef, "value2" );
	}     

	public void testMemorySetIndirectImmediate() throws InterruptedException, TimeoutException {

		final String source = "      SET [value],0xbeef\n"+ // 10
				"       HCF 0\n"+ // illegal opcode
				"value: .dat 0";

		execute(source);
		assertMemoryValue( 0xbeef, "value" );
	}     

	public void testMemorySetIndirectIndirect() throws InterruptedException, TimeoutException {

		final String source = "      SET [value2],[value1]\n"+ // 10
				"       HCF 0\n"+ // illegal opcode
				"value1: .dat 0xbeef\n"+                
				"value2: .dat 0";

		execute(source);

		assertMemoryValue( 0xbeef, "value1" );        
		assertMemoryValue( 0xbeef, "value2" );
	}     

	public void testMemorySetIndirectFromRegisterWithOffset() throws InterruptedException, TimeoutException {

		final String source = 
				"       SET a,value1\n" +
						"       SET [value2],[a+1]\n"+ // 10
						"       HCF 0\n"+ // illegal opcode
						"value1: .dat 0,0xbeef\n"+                
						"value2: .dat 0";

		execute(source);
		assertMemoryValue( 0xbeef, "value2" );
	}    

	public void testMemorySetIndirectFromRegisterIndirect() throws InterruptedException, TimeoutException {

		final String source = 
				"       SET a,value1\n" +
						"       SET [value2],[a]\n"+ // 10
						"       HCF 0\n"+ // illegal opcode
						"value1: .dat 0xbeef\n"+                
						"value2: .dat 0";

		execute(source);
		assertMemoryValue( 0xbeef, "value2" );
	}    

	public void testMemorySetIndirectFromStack() throws InterruptedException, TimeoutException {

		final String source = 
				"       SET PUSH,0xbeef\n" +
						"       SET [value],POP\n"+ // 10
						"       HCF 0\n"+ // illegal opcode
						"value: .dat 0";

		execute(source);
		assertMemoryValue( 0xbeef, "value" );
	}    

	public void testMemorySetIndirectFromRegister() throws InterruptedException, TimeoutException {

		final String source = "      SET a,0xbeef\n"+
				"      SET [value],a\n"+ // 10
				"       HCF 0\n"+ // illegal opcode
				"value: .dat 0";
		execute(source);
		assertMemoryValue( 0xbeef, "value" );
	}     
	
	/* ==========================
	 * ============= ADX ========
	 * ========================== */    

	public void testADX() throws InterruptedException, TimeoutException {

		final String source = "       SET b,1\n"+
				"       SET a ,2\n"+
				"       SET ex,3\n"+
				"       ADX b,a\n"+
				"       HCF 0";
		
		execute(source);
		
		// ADX b,a => sets b to b+a+EX, sets EX to 0x0001 if there is an over-flow, 0x0 otherwise
		
		assertRegA(2);
		assertRegEX(0);
		assertRegB(6);
	}
	
	public void testADXWithOverflow() throws InterruptedException, TimeoutException {

		final String source = "       SET b,0\n"+
				"       SET a ,65535\n"+
				"       SET ex,3\n"+
				"       ADX b,a\n"+
				"       HCF 0";
		
		execute(source);
		
		assertRegA(0xffff);
		assertRegEX(1);
		assertRegB((65535+3) & 0xffff );
	}	
	
	/* ==========================
	 * ============= SBX ========
	 * ========================== */    

	public void testSBX() throws InterruptedException, TimeoutException {

		final String source = "       SET b,6\n"+
				"       SET a ,2\n"+
				"       SET ex,3\n"+
				"       SBX b,a\n"+
				"       HCF 0";
		
		execute(source);
		
		// sets b to b-a+EX, sets EX to 0xFFFF if there is an under-flow, 0x0001 if there's an overflow, 0x0 otherwise
		
		assertRegA(2);
		assertRegEX(0);
		assertRegB(7);
	}
	
	public void testSBXWithUnderflow() throws InterruptedException, TimeoutException {

		final String source = "       SET b,0\n"+
				"       SET a ,1\n"+
				"       SET ex,3\n"+
				"       SBX b,a\n"+
				"       HCF 0";
		
		execute(source);
		
		// sets b to b-a+EX, sets EX to 0xFFFF if there is an under-flow, 0x0001 if there's an overflow, 0x0 otherwise
		
		assertRegA( 1 );
		assertRegEX(0xffff);
		assertRegB((65535+3) & 0xffff );
	}
	
	public void testSBXWithOverflow() throws InterruptedException, TimeoutException {

		final String source = "       SET b,0x7fff\n"+
				"       SET a ,0\n"+
				"       SET ex,0xffff\n"+
				"       SBX b,a\n"+
				"       HCF 0";
		
		execute(source, 10 * 60 * 1000 );
		
		// sets b to b-a+EX, sets EX to 0xFFFF if there is an under-flow, 0x0001 if there's an overflow, 0x0 otherwise
		
		assertRegA( 0 );
		assertRegEX(1);
		assertRegB( 0x17ffe & 0xffff );
	}		

	/* ==========================
	 * ============= ADD ========
	 * ========================== */    

	public void testRegisterAddImmediate() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0\n"+
				"       ADD a ,1\n"+
				"       HCF 0";
		execute(source);
		assertRegA(1);
		assertRegEX(0);
	}
	
    public void testAddWithPC() throws InterruptedException, TimeoutException {

        final String source = "       SET a,0\n"+
                "       ADD PC ,2\n"+
                "       HCF 0\n"+
                "       SET a,0x42\n"+
                "       HCF 0";
        
        execute(source);
        assertRegA(0x42);
        assertRegEX(0);        
    }  	

	public void testRegisterAddOverflow() throws InterruptedException, TimeoutException {

		final String source = "       SET a,65535\n"+
				"       ADD a ,1\n"+
				"       HCF 0";
		execute(source);
		assertRegA(0);
		assertRegEX(1);
	}

	public void testMemoryIndirectAddOverflow() throws InterruptedException, TimeoutException {

		final String source = "       ADD [value] ,1\n"+
				"       HCF 0\n"+
				"value: .dat 65535";
		execute(source);
		assertMemoryValue( 0 , "value" );
		assertRegEX(1);
	}    

	public void testAddRegisterRegister() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0\n"+
				"       SET b,1\n"+
				"       ADD a ,b\n"+
				"       HCF 0";
		execute(source);
		assertRegA(1);
		assertRegEX(0);
	}    

	public void testRegisterAddIndirect() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0\n"+
				"       ADD a ,[value]\n"+
				"       HCF 0\n"+
				"value: .dat 1";
		execute(source);
		assertRegA(1);
		assertRegEX(0);
	}    

	public void testRegisterAddRegisterRegisterIndirect() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0\n"+
				"       SET b,value\n"+
				"       ADD a ,[b]\n"+
				"       HCF 0\n"+
				"value: .dat 1";
		execute(source);
		assertRegA(1);
		assertRegEX(0);
	}   

	public void testRegisterAddRegisterRegisterIndirectWithOffset() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0\n"+
				"       SET b,value\n"+
				"       ADD a ,[b+1]\n"+
				"       HCF 0\n"+
				"value: .dat 0,1";
		execute(source);
		assertRegA(1);
		assertRegEX(0);
	}     

	public void testRegisterAddStack() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0\n" +
				"       SET PUSH,1\n"+
				"       ADD a ,POP\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA(1);
		assertRegEX(0);
	}  

	/* ==========================
	 * ============= SUB ========
	 * ========================== */

	public void testRegisterSubImmediate() throws InterruptedException, TimeoutException {

		final String source = "       SET a,2\n" +
				"       SUB a,1\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA(1);
		assertRegEX(0);
	}    

	public void testRegisterSubImmediateWithUnderflow() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0\n" +
				"       SUB a,1\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA(0xffff);
		assertRegEX(0xffff);
	}   

	/* ==========================
	 * ============= MUL ========
	 * ========================== */

	public void testRegisterMulImmediate() throws InterruptedException, TimeoutException {

		final String source = "       SET a,2\n" +
				"       MUL a,3\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA(6);
		assertRegEX(0);
	}  

	public void testRegisterMulImmediateWithOverflow() throws InterruptedException, TimeoutException {

		int a = 65535;
		int b = 3;

		final String source = "       SET a,"+a+"\n" +
				"       MUL a,"+b+"\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA(0xfffd);

		// sets EX to ((b*a)>>16) & 0xffff (treats b, a as unsigned)
		assertRegEX( ((b*a)>>16) & 0xffff );
	}   

	/* ==========================
	 * ============= MLI ========
	 * ========================== */    

	public void testRegisterMLIImmediatePositiveResult() throws InterruptedException, TimeoutException {

		final String source = "       SET a,2\n" +
				"       MLI a,3\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA(6);
		assertRegEX(0);
	}  

	public void testRegisterMLIImmediateNegativeResult() throws InterruptedException, TimeoutException {

		final String source = "       SET a,-2\n" +
				"       MLI a,3\n"+
				"       HCF 0\n";
		execute(source);
		assertRegASigned(-6);
		assertRegEX( (( signExtend16(-2) * signExtend16(3) )>>16) & 0xffff);
	}    

	public void testRegisterMLIImmediateWithOverflow() throws InterruptedException, TimeoutException {

		int a = 0b0111111111111111;
		int b = 3;

		final String source = "       SET a,"+a+"\n" +
				"       MLI a,"+b+"\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA(0x7ffd);

		// sets EX to ((b*a)>>16) & 0xffff (treats b, a as unsigned)
		assertRegEX( ((b*a)>>16) & 0xffff );
	}     

	/* ==========================
	 * ============= DIV ========
	 * ========================== */    

	public void testRegisterDIVImmediateWithRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 7;
		final String source = "       SET a,"+b+"\n" +
				"       DIV a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		/* DIV b,a.
		 * 
		 * Sets b to b/a, sets EX to ((b<<16)/a)&0xffff. 
		 * if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 */
		assertRegA(1);
		assertRegEX( ((b<<16)/a)&0xffff );
	}  

	public void testRegisterDIVImmediateWithoutRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 6;
		final String source = "       SET a,"+b+"\n" +
				"       DIV a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		/* DIV b,a.
		 * 
		 * Sets b to b/a, sets EX to ((b<<16)/a)&0xffff. 
		 * if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 */
		assertRegA(2);
		assertRegEX( ((b<<16)/a)&0xffff );
	}   

	public void testRegisterDIVImmediateDivideByZero() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 0;
		final String source = "       SET a,"+b+"\n" +
				"       DIV a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		/* DIV b,a.
		 * 
		 * Sets b to b/a, sets EX to ((b<<16)/a)&0xffff. 
		 * if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 */
		assertRegA(0);
		assertRegEX(0);
	}     

	/* ==========================
	 * ============= DVI ========
	 * ========================== */    

	public void testRegisterDVIImmediateWithRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 7;
		final String source = "       SET a,"+b+"\n" +
				"       DVI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		/* DIV b,a.
		 * 
		 * Sets b to b/a, sets EX to ((b<<16)/a)&0xffff. 
		 * if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 */
		assertRegA(1);
		assertRegEX( ((b<<16)/a)&0xffff );
	}  

	public void testRegisterDVIImmediateWithoutRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 6;
		final String source = "       SET a,"+b+"\n" +
				"       DVI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		/* DIV b,a.
		 * 
		 * Sets b to b/a, sets EX to ((b<<16)/a)&0xffff. 
		 * if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 */
		assertRegA(2);
		assertRegEX( ((b<<16)/a)&0xffff );
	}   

	public void testRegisterDVIImmediateDivideByZero() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 0;
		final String source = "       SET a,"+b+"\n" +
				"       DVI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		/* DIV b,a.
		 * 
		 * Sets b to b/a, sets EX to ((b<<16)/a)&0xffff. 
		 * if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 */
		assertRegA(0);
		assertRegEX(0);
	}    

	public void testRegisterDVIImmediateNegativeResultWithoutRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = -6;
		final String source = "       SET a,"+b+"\n" +
				"       DVI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		/* DVI b,a.
		 * 
		 * Like DIV, but treat b, a as signed. Rounds towards 0
		 */
		assertRegASigned(-2);
		assertRegEX( (( signExtend16(b) << 16 ) / signExtend16(a) ) &0xffff );
	}   

	public void testRegisterDVIImmediateNegativeResultWithRemainderRoundsToZero() throws InterruptedException, TimeoutException {

		final int b = 24;
		final int a = -20;

		// 24 / -20 = -1.2

		final String source = "       SET a,"+b+"\n" +
				"       DVI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		/* DVI b,a.
		 * 
		 * Like DIV, but treat b, a as signed. Rounds towards 0
		 */
		assertRegASigned(-1);
		assertRegEX( (( signExtend16(b) << 16 ) / signExtend16(a) ) &0xffff );
	}    

	/* ==========================
	 * ============= MOD ========
	 * ========================== */    

	public void testRegisterMODImmediateWithoutRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 6;
		final String source = "       SET a,"+b+"\n" +
				"       MOD a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		// sets b to b%a. if a==0, sets b to 0 instead.        
		assertRegA( b % a );
	}    

	public void testRegisterMODImmediateWithRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 7;
		final String source = "       SET a,"+b+"\n" +
				"       MOD a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA( b % a );
	}   

	public void testRegisterMODImmediateWithZero() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 0;
		final String source = "       SET a,"+b+"\n" +
				"       MOD a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA( 0 );
	}    

	/* ==========================
	 * ============= MDI ========
	 * ========================== */    

	public void testRegisterMDIImmediateWithoutRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 6;
		final String source = "       SET a,"+b+"\n" +
				"       MDI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		// like MOD, but treat b, a as signed. (MDI -7, 16 == -7)
		assertRegASigned( b % a );
	}    

	public void testRegisterMDIImmediateWithRemainder() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 7;
		final String source = "       SET a,"+b+"\n" +
				"       MDI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);
		assertRegASigned( b % a );
	}   

	public void testRegisterMDIImmediateWithZero() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = 0;
		final String source = "       SET a,"+b+"\n" +
				"       MDI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);
		assertRegASigned(0);
	}   

	public void testRegisterMDIImmediateWithoutRemainderSigned() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = -6;
		final String source = "       SET a,"+b+"\n" +
				"       MDI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);

		// like MOD, but treat b, a as signed. (MDI -7, 16 == -7)
		assertRegASigned( b % a  );
	}    

	public void testRegisterMDIImmediateWithRemainderSigned() throws InterruptedException, TimeoutException {

		final int b = 12;
		final int a = -7;
		final String source = "       SET a,"+b+"\n" +
				"       MDI a,"+a+"\n"+
				"       HCF 0\n";
		execute(source);
		assertRegASigned( b % a  );
	}     

	/* ==========================
	 * ============= AND ========
	 * ========================== */    

	public void testRegisterANDImmediate() throws InterruptedException, TimeoutException {

		final String source = "       SET a,b1010101010101010\n" +
				"       AND a,b0101010101010101\n"+
				"       HCF 0\n";
		execute(source);
		assertRegA( 0 );
	}     

	/* ==========================
	 * ============= BOR ========
	 * ========================== */    

	public void testRegisterBORImmediate() throws InterruptedException, TimeoutException {

		final String source = "       SET a,b1010101010101010\n" +
				"      BOR a,b0101010101010101\n"+
				"      HCF 0\n";
		execute(source);
		assertRegA( 0xffff );
	}  

	/* ==========================
	 * ============= XOR ========
	 * ========================== */    

	public void testRegisterXORImmediate() throws InterruptedException, TimeoutException {

		final String source = "       SET a,b1010101010101010\n" +
				"      XOR a,b0101010101010101\n"+
				"      HCF 0\n";
		execute(source);
		assertRegA( 0b1010101010101010 ^ 0b0101010101010101 );
	}     

	/* ==========================
	 * ============= SHR ========
	 * ========================== */    

	public void testRegisterSHRImmediate1() throws InterruptedException, TimeoutException {

		int b = 0b1010101010101010;
		int a = 1;
		final String source = "       SET a,"+b+"\n" +
				"      SHR a,"+a+"\n"+
				"      HCF 0\n";
		execute(source);

		// sets b to b>>>a, sets EX to ((b<<16)>>a)&0xffff (logical shift,MSB is populated with zero)        
		assertRegA( 0b0101010101010101 );
		assertRegEX( ((b<<16)>>a)&0xffff );
	}  

	public void testRegisterSHRImmediate2() throws InterruptedException, TimeoutException {

		int b = 0b1010101010101010;
		int a = 1;
		final String source = "       SET a,"+b+"\n" +
				"      SHR a,"+a+"\n"+
				"      HCF 0\n";
		execute(source);

		// sets b to b>>>a, sets EX to ((b<<16)>>a)&0xffff (logical shift,MSB is populated with zero)        
		assertRegA( 0b0101010101010101 );
		assertRegEX( ((b<<16)>>a)&0xffff);
	}    

	/* ==========================
	 * ============= ASR ========
	 * ========================== */    

	public void testRegisterASRImmediate1() throws InterruptedException, TimeoutException {

		int a = 1;
		int b = -12;

		final String source = "       SET a,"+b+"\n" +
				"      ASR a,"+a+"\n"+
				"      HCF 0\n";
		execute(source);

		// sets b to b>>a, sets EX to ((b<<16)>>>a) & 0xffff , (arithmetic shift) (treats b as signed)        
		assertRegASigned( b >> a  );
		assertRegEX( ((signExtend16(b)<<16)>>>a) & 0xffff );
	}     

	public void testRegisterASRImmediate2() throws InterruptedException, TimeoutException {

		int a = 1;
		int b = -13;

		final String source = "       SET a,"+b+"\n" +
				"      ASR a,"+a+"\n"+
				"      HCF 0\n";
		execute(source);

		// sets b to b>>a, sets EX to ((b<<16)>>>a) & 0xffff , (arithmetic shift) (treats b as signed)        
		assertRegASigned( b >> a  );
		assertRegEX( ((signExtend16(b)<<16)>>>a) & 0xffff );
	}  

	/* ==========================
	 * ============= SHL ========
	 * ========================== */    

	public void testRegisterSHLImmediate1() throws InterruptedException, TimeoutException {

		int a = 1;        
		int b = 0b0101010101010101;

		final String source = "       SET a,"+b+"\n" +
				"      SHL a,"+a+"\n"+
				"      HCF 0\n";
		execute(source);

		// sets b to b<<a, sets EX to ((b<<a)>>16)&0xffff
		assertRegA( 0b1010101010101010 );
		assertRegEX( ((b<<a)>>16)&0xffff );        
	}  

	public void testRegisterSHLImmediate2() throws InterruptedException, TimeoutException {

		int a = 1;
		int b = 0b1010101010101010;

		final String source = "       SET a,"+b+"\n" +
				"      SHL a,"+a+"\n"+
				"      HCF 0\n";
		execute(source);
		assertRegA( 0b0101010101010100 );
		assertRegEX( ((b<<a)>>16)&0xffff );
	}  

	/* ==========================
	 * ============= IFB ========
	 * ========================== */    

	public void testRegisterIFBImmediateConditionTrue() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFB b,2\n"+
				"      SET a,0xbeef\n"+
				"      HCF 0\n";
		execute(source);

		// performs next instruction only if (b&a)!=0
		assertRegA( 0xbeef );
	}     

	public void testRegisterIFBImmediateConditionFalse() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFB b,4\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                               
				"      HCF 0\n";
		execute(source);
		assertRegA( 0 );
		assertRegC( 0xdead );
	}      

	/* ==========================
	 * ============= IFC ========
	 * ========================== */    

	public void testRegisterIFCImmediateConditionFalse() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFC b,2\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                  
				"      HCF 0\n";
		execute(source);

		// performs next instruction only if (b&a)==0
		assertRegA( 0 );
		assertRegC( 0xdead );
	}     

	public void testRegisterIFBCImmediateConditionTrue() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFC b,4\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                               
				"      HCF 0\n";
		execute(source);
		assertRegA( 0xbeef );
	}    

	/* ==========================
	 * ============= IFE ========
	 * ========================== */    

	public void testRegisterIFEImmediateConditionFalse() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFE b,2\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                  
				"      HCF 0\n";
		execute(source);

		// performs next instruction only if b==a
		assertRegA( 0 );
		assertRegC( 0xdead );
	}     

	public void testRegisterIFECImmediateConditionTrue() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFE b,3\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                               
				"      HCF 0\n";
		execute(source);
		assertRegA( 0xbeef );
	}    


	/* ==========================
	 * ============= IFE ========
	 * ========================== */    

	public void testRegisterIFNImmediateConditionTrue() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFN b,2\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                  
				"      HCF 0\n";
		execute(source);

		// performs next instruction only if b==a
		assertRegA( 0xbeef );        
	}     

	public void testRegisterIFNImmediateConditionFalse() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFN b,3\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                               
				"      HCF 0\n";
		execute(source);
		assertRegA( 0 );
		assertRegC( 0xdead );
	}    

	/* ==========================
	 * ============= IFG ========
	 * ========================== */    

	public void testRegisterIFGImmediateConditionTrue() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFG b,2\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                  
				"      HCF 0\n";
		execute(source);

		// performs next instruction only if b>a
		assertRegA( 0xbeef );        
	}     

	public void testRegisterIFGImmediateConditionFalse() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFG b,3\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                               
				"      HCF 0\n";
		execute(source);
		assertRegA( 0 );
		assertRegC( 0xdead );
	}     

	/* ==========================
	 * ============= IFL ========
	 * ========================== */    

	public void testRegisterIFLImmediateConditionTrue() throws InterruptedException, TimeoutException {

		final String source = "       SET b,2\n" +
				"      IFL b,3\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                  
				"      HCF 0\n";
		execute(source);

		// performs next instruction only if b<a
		assertRegA( 0xbeef );        
	}     

	public void testRegisterIFLImmediateConditionFalse() throws InterruptedException, TimeoutException {

		final String source = "       SET b,3\n" +
				"      IFL b,3\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                               
				"      HCF 0\n";
		execute(source);
		assertRegA( 0 );
		assertRegC( 0xdead );
	}   

	/* ==========================
	 * ============= IFU ========
	 * ========================== */    

	public void testRegisterIFUImmediateConditionTrue() throws InterruptedException, TimeoutException {

		final String source = "       SET b,-3\n" +
				"      IFU b,-2\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                  
				"      HCF 0\n";
		execute(source);

		// performs next instruction only if b < a (signed)
		assertRegA( 0xbeef );        
	}     

	public void testRegisterIFUImmediateConditionFalse() throws InterruptedException, TimeoutException {

		final String source = "       SET b,-2\n" +
				"      IFU b,-3\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                               
				"      HCF 0\n";
		execute(source);
		assertRegA( 0 );
		assertRegC( 0xdead );
	}    

	/* ==========================
	 * ============= IFA ========
	 * ========================== */    

	public void testRegisterIFAImmediateConditionTrue() throws InterruptedException, TimeoutException {

		final String source = "       SET b,-2\n" +
				"      IFA b,-3\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                  
				"      HCF 0\n";
		execute(source);

		// performs next instruction only if b>a (signed)
		assertRegA( 0xbeef );        
	}     

	public void testRegisterIFAImmediateConditionFalse() throws InterruptedException, TimeoutException {

		final String source = "       SET b,-3\n" +
				"      IFA b,-2\n"+
				"      SET a,0xbeef\n"+
				"      SET c,0xdead\n"+                               
				"      HCF 0\n";
		execute(source);
		assertRegA( 0 );
		assertRegC( 0xdead );
	}

	/* ==========================
	 * ============= JSR ========
	 * ========================== */    

	public void testJSR() throws InterruptedException, TimeoutException {

		final String source = "       JSR subroutine\n" +
				"      SET b,0xbeef\n"+
				"      HCF 0\n"+
				"subroutine:  SET a ,0xdead\n"+
				"      SET PC,POP";
		execute(source);

		assertRegA( 0xdead );
		assertRegB( 0xbeef );
	}     
	
	/* ==========================
	 * ============= STI ========
	 * ========================== */    

	public void testSTI() throws InterruptedException, TimeoutException {

		final String source = 
				"      SET a,5\n" +
		        "      SET i , value1\n"+
			    "      SET j , value2\n"+
		        "loop: \n"+
			    "      STI [j],[i]\n"+
		        "      SUB a,1\n"+
			    "      IFN a,0\n"+
		        "      SET PC,loop\n"+
				"      HCF 0\n"+
				"value1: .dat 1,2,3,4,5\n"+
				"value2: .dat 0,0,0,0,0";
		
		execute(source);
		assertRegA( 0 );
		int mem = getLabelAddress("value2").getWordAddressValue();
		assertRegI( mem );
		assertRegJ( mem+5 );
		
		assertMemoryValue( 1 , Address.wordAddress(mem) );
		assertMemoryValue( 2 , Address.wordAddress(mem).plus(Size.words(1), false ) );
		assertMemoryValue( 3 , Address.wordAddress(mem).plus(Size.words(2), false ) );
		assertMemoryValue( 4 , Address.wordAddress(mem).plus(Size.words(3), false ) );
		assertMemoryValue( 5 , Address.wordAddress(mem).plus(Size.words(4), false ) );
		assertMemoryValue( 0 , Address.wordAddress(mem).plus(Size.words(5), false ) );		
	}
	
	
	/* ==========================
	 * ============= STI ========
	 * ========================== */    

	public void testSTD() throws InterruptedException, TimeoutException {

		final String source = 
				"      SET a,5\n" +
		        "      SET i , value1\n"+
			    "      SET j , value2\n"+
		        "loop: \n"+
			    "      STD [j],[i]\n"+
		        "      SUB a,1\n"+
			    "      IFN a,0\n"+
		        "      SET PC,loop\n"+
				"      HCF 0\n"+
		        "check1:\n"+
				"      .dat 1,2,3,4\n"+
				"value1:\n"+
				"      .dat 5 \n"+
				"check2:\n"+
				"      .dat 0,0,0,0\n"+
				"value2: \n"+
		        "      .dat 0";
		
		execute(source);
		assertRegA( 0 );
		int mem = getLabelAddress("check2").getWordAddressValue();
		assertRegI(  getLabelAddress("check1").minus(Size.words(1)) );
		assertRegJ(  getLabelAddress("value1") );
		
		assertMemoryValue( 1 , Address.wordAddress(mem) );
		assertMemoryValue( 2 , Address.wordAddress(mem).plus(Size.words(1), false ) );
		assertMemoryValue( 3 , Address.wordAddress(mem).plus(Size.words(2), false ) );
		assertMemoryValue( 4 , Address.wordAddress(mem).plus(Size.words(3), false ) );
		assertMemoryValue( 5 , Address.wordAddress(mem).plus(Size.words(4), false ) );
		assertMemoryValue( 0 , Address.wordAddress(mem).plus(Size.words(5), false ) );		
	}	

	/* ==========================
	 * ============= IAS ========
	 * ========================== */    

	public void testIAS() throws InterruptedException, TimeoutException {

		final String source = "       IAS subroutine\n" +
				"      HCF 0\n"+
				"subroutine:";
		execute(source);
		assertRegIA( 0x02 );
	}    

	/* ==========================
	 * ============= IAG ========
	 * ========================== */    

	public void testIAG() throws InterruptedException, TimeoutException {

		final String source = "       IAS subroutine\n" +
				"       IAG a\n"+
				"      HCF 0\n"+
				"subroutine:";
		execute(source);
		assertRegA( 0x3 );
	}    

	/* ==========================
	 * ============= INT ========
	 * ========================== */    

	public void testINT1() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0x42\n"+
				"       IAS subroutine\n" +
				"       INT 0xbeef\n"+ // trigger software interrupt with message 0xbeef
				"contlabel:\n"+                              
				"       SET b, 0xdead\n"+ // never reached
				"       HCF 0\n"+ // never reached
				"subroutine:"+
				"       SET b,a\n"+ // a will be set to the interrupt message
				"       HCF 0";
		execute(source);

		final Address sp = emulator.getCPU().getSP();

		assertRegB( 0xbeef );

		/* Stack layout at start of interrupt handler
		 * 
		 * +---------+
		 * |   PC    |
		 * +---------+
		 * |    A    | <-- SP
		 * +---------+
		 */
		assertEquals( Address.wordAddress( 0xfffe ) , sp );

		assertMemoryValue( 0x42 , sp ); // saved reg A value 
		assertMemoryValue( getLabelAddress("contlabel") , sp.plus(Size.words(1), false ) ); // PC of instruction after INT
	}    

	/* ==========================
	 * ============= RFI ========
	 * ========================== */    

	public void testRFI() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0x42\n"+
				"       IAS subroutine\n" +
				"       INT 0xbeef\n"+ // trigger software interrupt with message 0xbeef
				"contlabel:\n"+                              
				"       SET c, 0xdead\n"+ 
				"       HCF 0\n"+ 
				"subroutine:"+
				"       SET b,a\n"+ // a will be set to the interrupt message
				"       RFI 0";
		execute(source);

		assertRegB( 0xbeef );
		assertRegC( 0xdead );        

		/* Stack layout at start of interrupt handler
		 * 
		 * +---------+
		 * |   PC    |
		 * +---------+
		 * |    A    | <-- SP
		 * +---------+
		 */
		assertRegSP( 0 );
	}     

	/* ==========================
	 * ============= IAQ ========
	 * ========================== */    

	public void testIAQEnableQueueing() throws InterruptedException, TimeoutException {

		final String source = "       IAS subroutine\n" +
				"       IAQ 1\n"+ // switch queuing on
				"       INT 0x42\n"+ // software interrupt will be queued
				"contlabel:\n"+                              
				"       SET c, 0xbeef\n"+ 
				"       HCF 0\n"+ 
				"subroutine:"+
				"       SET x, 0xdead\n"+
				"       HCF 0"; // a will be set to the interrupt message
		execute(source);

		/* IAQ a
		 * 
		 * -> if a is nonzero, interrupts will be added to the queue instead of triggered. 
		 * -> if a is zero, interrupts will be triggered as normal again
		 */

		assertRegC( 0xbeef );
		assertRegX( 0 );        

		/* Stack layout at start of interrupt handler
		 * 
		 * +---------+
		 * |   PC    |
		 * +---------+
		 * |    A    | <-- SP
		 * +---------+
		 */
		assertRegSP( 0 );
	}    

	public void testIAQDisableQueueing() throws InterruptedException, TimeoutException {

		final String source = "       SET a,0x42\n"+
				"       IAS subroutine\n" +
				"       IAQ 1\n"+ // switch queuing on
				"       INT 0x42\n"+ // software interrupt will be queued
				"       IAQ 0\n"+ // switch queuing off
				"contlabel:\n"+                              
				"       SET c, 0xbeef\n"+ 
				"       HCF 0\n"+ 
				"subroutine:"+
				"       SET x, 0xdead\n"+
				"       HCF 0"; // a will be set to the interrupt message
		execute(source);

		/* IAQ a
		 * 
		 * -> if a is nonzero, interrupts will be added to the queue instead of triggered. 
		 * -> if a is zero, interrupts will be triggered as normal again
		 */

		assertRegC( 0 );
		assertRegX( 0xdead );        

		/* Stack layout at start of interrupt handler
		 * 
		 * +---------+
		 * |   PC    |
		 * +---------+
		 * |    A    | <-- SP
		 * +---------+
		 */
		Address sp = emulator.getCPU().getSP();
		assertEquals( Address.wordAddress( 0xfffe ) , sp );

		assertMemoryValue( 0x42 , sp ); // saved reg A value 
		assertMemoryValue( getLabelAddress("contlabel") , sp.plus(Size.words(1), false ) ); // PC of instruction after INT        
	}

	/* ==========================
	 * ============= HWN ========
	 * ========================== */       

	public void testHWN() throws InterruptedException, TimeoutException {

		final String source = 
				"       HWN a\n"+ // 
						"       HCF 0\n"; // illegal opcode

		execute(source);

		// sets a to number of connected devices
		assertRegA( emulator.getDevices().size() );
	}    

	/* ==========================
	 * ============= HWQ ========
	 * ========================== */       

	public void testHWQ() throws InterruptedException, TimeoutException {

		final MockDevice device = new MockDevice();
		final int slotNo = emulator.addDevice( device );
		
		final String source = 
				"       HWQ "+slotNo+"\n"+ // 
						"       HCF 0\n"; // illegal opcode
		
		try 
		{
			execute(source);

			/* sets A, B, C, X, Y registers to information about hardware a
			 * 
			 * A+(B<<16) is a 32 bit word identifying the hardware id
			 * C is the hardware version
			 * X+(Y<<16) is a 32 bit word identifying the manufacturer
			 */
			// sets a to number of connected devices
			final IReadOnlyCPU cpu = emulator.getCPU();

			final long hardwareId = cpu.getRegisterValue( Register.A) + ( cpu.getRegisterValue( Register.B) << 16 );
			final int hardwareVersion = cpu.getRegisterValue( Register.C );
			final long manufacturer = cpu.getRegisterValue( Register.X) + ( cpu.getRegisterValue( Register.Y) << 16 );

			assertEquals( "Hardware ID mismatch" , device.getDeviceDescriptor().getID() , hardwareId);
			assertEquals( "Hardware version mismatch" , device.getDeviceDescriptor().getVersion() , hardwareVersion );
			assertEquals( "Manufacturer ID mismatch" , device.getDeviceDescriptor().getManufacturer() , manufacturer );
		} finally {
			emulator.removeDevice( device );
		}
	}     
	
	/* ==========================
	 * ============= HWI ========
	 * ========================== */       

	public void testHWI() throws InterruptedException, TimeoutException {

		final MockDevice device = new MockDevice();
		final int slotNo = emulator.addDevice( device );
		
		final String source = 
				"       HWI "+slotNo+"\n"+ // 
						"       HCF 0\n"; // illegal opcode
		
		try 
		{
			execute(source);
			assertEquals( 1 , device.getInterruptCount() );
		} finally {
			emulator.removeDevice( device );
		}
	}  	
	
	protected static final class MockDevice implements IDevice {

		private final DeviceDescriptor desc = new DeviceDescriptor("mock_device", "A mock device", 0x123456, 0x42, 0x654321);
	
		private final AtomicInteger interruptCount = new AtomicInteger(0);
		
		public MockDevice() {
			
		}
		@Override
		public void afterAddDevice(IEmulator emulator) {
		}

		@Override
		public void reset() {
		}

		@Override
		public void beforeRemoveDevice(IEmulator emulator) {
		}

		@Override
		public DeviceDescriptor getDeviceDescriptor() {
			return desc;
		}
		
		public int getInterruptCount() {
			return interruptCount.get();
		}

		@Override
		public int handleInterrupt(IEmulator emulator) {
			interruptCount.incrementAndGet();
			return 0;
		}
		@Override
		public boolean supportsMultipleInstances() {
			return false;
		}
		
	}
}