package de.codesourcery.jasm16.emulator;

import java.util.concurrent.CyclicBarrier;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.utils.Misc;

public class SimpleSimulation {

	private final SimulationThread simulationThread;
	
	private static final boolean DEBUG = true;

	private static final int INSTRUCTION_DELAY_MILLIS = 1000;
	
	// ============ Memory ============

	public int[] memory = new int[65535];

	// ============ CPU =============== 

	// a,b,c,x,y,z,i,j
	public final int[] registers=new int[8];

	public int pc;
	public int sp;
	public int ex;

	private int interruptAddress;

	private int currentCycle = 0;

	public void reset() 
	{
		stop();
		currentCycle = 0;
		interruptAddress = 0;
		pc = 0;
		sp = 0xffff;
		ex = 0;
		for ( int i = 0 ; i < registers.length ; i++ ) {
			registers[i] = 0;
		}
		for ( int i = 0 ; i < memory.length ; i++ ) {
			memory[i] = 0;
		}		
	}

	public void stop() 
	{
		simulationThread.stopSimulation();
	}

	public void start() 
	{
		simulationThread.startSimulation();		
	}

	public SimpleSimulation() {
		simulationThread = new SimulationThread();
		simulationThread.start();
	}

	public class SimulationThread extends Thread {

		private volatile boolean isRunnable = false;

		private final Object SLEEP_LOCK = new Object();

		private final CyclicBarrier STOP_LATCH = new CyclicBarrier(2);
		private final CyclicBarrier START_LATCH = new CyclicBarrier(2);

		private long lastStart;
		private long lastStop;
		
		public SimulationThread() 
		{
			setName("simulation-thread");
			setDaemon(true);
		}

		public synchronized void startSimulation() 
		{
			if ( isRunnable == false ) 
			{
				try {
					START_LATCH.reset();
					isRunnable = true;
					synchronized( SLEEP_LOCK ) {
						SLEEP_LOCK.notifyAll();
					}
					START_LATCH.await();
				} 
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}		

		public synchronized void stopSimulation() 
		{
			if ( isRunnable == true ) 
			{
				try {
					STOP_LATCH.reset();
					isRunnable = false;
					STOP_LATCH.await();
				} catch (Exception e) {
					e.printStackTrace();
				}           
			}
		}	 
		
	    public double getRuntimeInSeconds() {
	        if ( lastStart != 0 && lastStop != 0 ) 
	        {
	            if ( lastStop >= lastStart ) {
	                return (lastStop - lastStart)/1000.0f;
	            }
	        } else if ( lastStart != 0 ) {
	            return ( System.currentTimeMillis() - lastStart) / 1000.0f;
	        }
	        return -1; 
	    }     		
	    
	    public String getEstimatedClockSpeed() 
	    {
	        final double clockRate = getCyclesPerSecond();
	        
	        final double delta = clockRate-100000.0;
	        final double deviationPercentage = 100.0d*( delta / 100000.0 );
	        final String deviation = " ( "+deviationPercentage+" % )";
	        
	        if ( clockRate == -1.0d ) {
	            return "<cannot calculate clock rate>";
	        }
	        if ( clockRate < 1000 ) {
	            return clockRate+" Hz"+deviation;
	        } else if ( clockRate < 100000) {
	            return (clockRate/1000)+" kHz"+deviation;
	        } else if ( clockRate < 1000000000 ) {
	            return (clockRate/1000000)+" MHz"+deviation;
	        }
	        return (clockRate/1000000000)+" GHz"+deviation;         
	    } 	    
		
	    public double getCyclesPerSecond() 
	    {
	        if ( lastStart != 0 )
	        {
	            return currentCycle / getRuntimeInSeconds();
	        }
	        return -1.0d;
	    }		

		@Override
		public void run() {

			System.out.println("*** Emulation ready ***");

			while( isRunnable == false ) 
			{
				try {
					synchronized( SLEEP_LOCK ) {
						SLEEP_LOCK.wait();
					}
				} 
				catch (Exception e) 
				{
					e.printStackTrace();
				}
			}

			try {
				START_LATCH.await();
			} catch (Exception e2) {
				e2.printStackTrace();
			}
			
			System.out.println("*** Emulation started ***");
			
	        lastStart = System.currentTimeMillis();
	        
			while ( true ) {

				if ( isRunnable == false ) 
				{
	                lastStop = System.currentTimeMillis();
	                
					try {
						STOP_LATCH.await();
					}
					catch (Exception e1) { e1.printStackTrace(); }

					System.out.println("*** Emulation stopped ***");					
					while ( isRunnable == false ) 
					{
						try 
						{
							synchronized( SLEEP_LOCK ) {
								SLEEP_LOCK.wait();
							}
						} 
						catch (Exception e) { /* can't help it */ } 
					}
					System.out.println("*** Emulation started ***");
					try {
						START_LATCH.await();
					} catch (Exception e) { e.printStackTrace(); }
					
	                lastStart = System.currentTimeMillis();   					
				}

				try {
					final int execDurationInCycles = executeInstruction();
					if ( DEBUG ) {
						System.out.println("Execution takes "+execDurationInCycles+" cycles.");
					}
					currentCycle+=execDurationInCycles;
					if ( isRunnable && DEBUG ) {
						Thread.sleep( INSTRUCTION_DELAY_MILLIS );						
					}
				} 
				catch(Exception e) {
					e.printStackTrace();
					System.err.println("\n\nERROR: Simulation stopped due to error.");
					return;
				} 
				finally {
					if ( DEBUG ) {
						printCPU();
					}
				}
			}
		}
	}

	private void printCPU() {

		System.out.println("\n");
		final String[] names = { "A" , "B" , "C" , "X","Y","Z","I","J" };
		int itemsInLine = 0;
		for ( int i = 0 ; i < names.length ; i++ ) {
			System.out.print( names[i]+": "+Misc.toHexString( registers[i] )+"    ");
			itemsInLine++;
			if ( itemsInLine == 4 ) {
				itemsInLine = 0;
				System.out.println();
			}
		}
		System.out.println("\nPC: "+Misc.toHexString( pc ));
		System.out.println("EX: "+Misc.toHexString( ex ));
		System.out.println("IA: "+Misc.toHexString( interruptAddress ));		
		System.out.println("SP: "+Misc.toHexString( sp ));
	}
	
	private boolean isConditionalInstruction(int instructionWord) {
		
		final int opCode = (instructionWord & 0x1f);
		return ( opCode >= 0x10 && opCode <= 0x17);
	}

	private int executeInstruction() 
	{
		final int instructionWord = memory[ pc++ ];
		
		if ( DEBUG ) {
			System.out.println( "\nExecuting "+Misc.toHexString( pc-1 )+": "+Misc.toHexString( instructionWord )+
					" ( "+Misc.toBinaryString( instructionWord , 16 )+" )" );
		}

		final int opCode = (instructionWord & 0x1f);

		/*
		 *   |--- Basic opcodes (5 bits) ----------------------------------------------------
		 *   |C | VAL  | NAME     | DESCRIPTION
		 *   +---+------+----------+---------------------------------------------------------
		 *   |- | 0x00 | n/a      | special instruction - see below
		 *   |1 | 0x01 | SET b, a | sets b to a
		 *   |2 | 0x02 | ADD b, a | sets b to b+a, sets EX to 0x0001 if there's an overflow, 0x0 otherwise
		 *   |2 | 0x03 | SUB b, a | sets b to b-a, sets EX to 0xffff if there's an underflow, 0x0 otherwise
		 *   |2 | 0x04 | MUL b, a | sets b to b*a, sets EX to ((b*a)>>16)&0xffff (treats b, a as unsigned)
		 *   |2 | 0x05 | MLI b, a | like MUL, but treat b, a as signed
		 *   |3 | 0x06 | DIV b, a | sets b to b/a, sets EX to ((b<<16)/a)&0xffff. if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 *   |3 | 0x07 | DVI b, a | like DIV, but treat b, a as signed. Rounds towards 0
		 *   |3 | 0x08 | MOD b, a | sets b to b%a. if a==0, sets b to 0 instead.
		 *   |3 | 0x09 | MDI b, a | like MOD, but treat b, a as signed. Rounds towards 0
		 *   |1 | 0x0a | AND b, a | sets b to b&a
		 *   |1 | 0x0b | BOR b, a | sets b to b|a
		 *   |1 | 0x0c | XOR b, a | sets b to b^a
		 *   |2 | 0x0d | SHR b, a | sets b to b>>>a, sets EX to ((b<<16)>>a)&0xffff  (logical shift)
		 *   |2 | 0x0e | ASR b, a | sets b to b>>a, sets EX to ((b<<16)>>>a)&0xffff (arithmetic shift) (treats b as signed)
		 *   |2 | 0x0f | SHL b, a | sets b to b<<a, sets EX to ((b<<a)>>16)&0xffff
		 *   |
		 *   |2+| 0x10 | IFB b, a | performs next instruction only if (b&a)!=0
		 *   |2+| 0x11 | IFC b, a | performs next instruction only if (b&a)==0
		 *   |2+| 0x12 | IFE b, a | performs next instruction only if b==a 
		 *   |2+| 0x13 | IFN b, a | performs next instruction only if b!=a 
		 *   |2+| 0x14 | IFG b, a | performs next instruction only if b>a 
		 *   |2+| 0x15 | IFA b, a | performs next instruction only if b>a (signed)
		 *   |2+| 0x16 | IFL b, a | performs next instruction only if b<a 
		 *   |2+| 0x17 | IFU b, a | performs next instruction only if b<a (signed)
		 *   |- | 0x18 | -        |
		 *   |- | 0x19 | -        |
		 *   |3 | 0x1a | ADX b, a | sets b to b+a+EX, sets EX to 0x0001 if there is an over-flow, 0x0 otherwise
		 *   |3 | 0x1b | SBX b, a | sets b to b-a+EX, sets EX to 0xFFFF if there is an under-flow, 0x0 otherwise
		 *   |- | 0x1c | -        | 
		 *   |- | 0x1d | -        |
		 *   |2 | 0x1e | STI b, a | sets b to a, then increases I and J by 1
		 *   |2 | 0x1f | STD b, a | sets b to a, then decreases I and J by 1
		 *   +---+------+----------+----------------------------------------------------------			 
		 */

		switch( opCode ) {
		case 0x00:
			return handleSpecialOpCode( instructionWord );
		case 0x01:
			return handleSET( instructionWord );
		case 0x02:
			return handleADD( instructionWord );
		case 0x03:
			return handleSUB( instructionWord );
		case 0x04:
			return handleMUL( instructionWord );
		case 0x05:
			return handleMLI( instructionWord );
		case 0x06:
			return handleDIV( instructionWord );
		case 0x07:
			return handleDVI( instructionWord );
		case 0x08:
			return handleMOD( instructionWord );
		case 0x09:
			return handleMDI( instructionWord );
		case 0x0a:
			return handleAND( instructionWord );
		case 0x0b:
			return handleBOR( instructionWord );
		case 0x0c:
			return handleXOR( instructionWord );
		case 0x0d:
			return handleSHR( instructionWord );
		case 0x0e:
			return handleASR( instructionWord );
		case 0x0f:
			return handleSHL( instructionWord );
		case 0x10:
			return handleIFB( instructionWord );
		case 0x11:
			return handleIFC( instructionWord );
		case 0x12:
			return handleIFE( instructionWord );
		case 0x13:
			return handleIFN( instructionWord );
		case 0x14:
			return handleIFG( instructionWord );
		case 0x15:
			return handleIFA( instructionWord );
		case 0x16:
			return handleIFL( instructionWord );
		case 0x17:
			return handleIFU( instructionWord );
		case 0x18:
		case 0x19:
			return handleUnknownOpCode( instructionWord );
		case 0x1a:
			return handleADX( instructionWord );
		case 0x1b:
			return handleSBX( instructionWord );
		case 0x1c:
		case 0x1d:
			return handleUnknownOpCode( instructionWord );
		case 0x1e:
			return handleSTI( instructionWord );
		case 0x1f:
			return handleSTD( instructionWord );
		default:
			return handleUnknownOpCode( instructionWord );
		}
	}

	private int handleSTD(int instructionWord) {
		// sets b to a, then decreases I and J by 1
		// a,b,c,x,y,z,i,j
		OperandDesc source = loadSourceOperand( instructionWord );

		registers[6]-=1;
		registers[7]-=1;
		return 2+storeTargetOperand( instructionWord , source.value )+source.cycleCount;			
	}

	private int handleSTI(int instructionWord) {
		// sets b to a, then increases I and J by 1
		OperandDesc source = loadSourceOperand( instructionWord );

		// a,b,c,x,y,z,i,j
		registers[6]+=1;
		registers[7]+=1;
		return 2+storeTargetOperand( instructionWord , source.value )+source.cycleCount;			
	}

	private int handleSBX(int instructionWord) 
	{
		// sets b to b-a+EX, sets EX to 0xFFFF if there is an under-flow, 0x0 otherwise
		OperandDesc source = loadSourceOperand( instructionWord );
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value - source.value + ex;
		if ( acc < 0 ) {
			ex = 0xFFFF;
		} else {
			ex = 0;
		}
		return 3+storeTargetOperand( instructionWord , acc)+source.cycleCount;		
	}

	private int handleADX(int instructionWord) {
		// sets b to b+a+EX, sets EX to 0x0001 if there is an over-flow, 0x0 otherwise
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value + source.value + ex;
		if ( acc > 0xffff) {
			ex = 0x0001;
		} else {
			ex = 0;
		}
		return 3+storeTargetOperand( instructionWord , acc)+source.cycleCount;
	}

	private int handleUnknownOpCode(int instructionWord) {
		throw new RuntimeException("Unknown opcode 0x"+Misc.toHexString( instructionWord )+" at address "+
				"0x"+Misc.toHexString( pc-1 ) );
	}

	private int handleIFU(int instructionWord) {
		// performs next instruction only if b<a (signed)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		int penalty = 0;
		if ( signed( target.value ) >= signed( source.value ) ) 
		{
			penalty = handleConditionFailure();
			pc++; // skip next instruction
		}
		return 2+target.cycleCount+source.cycleCount+penalty;			
	}
	
	private int handleConditionFailure() {
		if ( isConditionalInstruction( memory [pc ] ) ) {
			pc++; // skip additional instruction
			return 1;
		}
		return 0;
	}

	private int handleIFL(int instructionWord) {
		// performs next instruction only if b<a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		int penalty = 0;
		if ( target.value >= source.value ) {
			penalty = handleConditionFailure();
			pc++; // skip next instruction
		}
		return 2+target.cycleCount+source.cycleCount+penalty;		
	}

	private int handleIFA(int instructionWord) {
		// performs next instruction only if b>a (signed)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		int penalty = 0;
		if ( signed( target.value ) <= signed( source.value ) ) {
			penalty = handleConditionFailure();
			pc++; // skip next instruction
		}
		return 2+target.cycleCount+source.cycleCount+penalty;		
	}

	private int handleIFG(int instructionWord) {
		// performs next instruction only if b>a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		int penalty=0;
		if ( target.value <= source.value ) {
			penalty = handleConditionFailure();
			pc++; // skip next instruction
		}
		return 2+target.cycleCount+source.cycleCount+penalty;			
	}

	private int handleIFN(int instructionWord) {
		// performs next instruction only if b!=a
		OperandDesc source = loadSourceOperand( instructionWord );
		OperandDesc target = loadTargetOperand( instructionWord , false );

		int penalty = 0;
		if ( target.value == source.value ) {
			penalty = handleConditionFailure();
			pc++; // skip next instruction
		}
		return 2+target.cycleCount+source.cycleCount+penalty;			
	}

	private int handleIFE(int instructionWord) {
		// performs next instruction only if b==a
		OperandDesc source = loadSourceOperand( instructionWord );
		OperandDesc target = loadTargetOperand( instructionWord , false );

		int penalty=0;
		if ( target.value != source.value ) {
			penalty = handleConditionFailure();
			pc++; // skip next instruction
		}
		return 2+target.cycleCount+source.cycleCount+penalty;		
	}

	private int handleIFC(int instructionWord) {
		// performs next instruction only if (b&a)==0
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		int penalty = 0;
		if ( (target.value & source.value) != 0 ) {
			penalty = handleConditionFailure();
			pc++; // skip next instruction
		}
		return 2+target.cycleCount+source.cycleCount+penalty;
	}

	private int handleIFB(int instructionWord) {
		// performs next instruction only if (b&a)!=0
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		int penalty=0;
		if ( (target.value & source.value) == 0 ) {
			penalty = handleConditionFailure();
			pc++; // skip next instruction
		}
		return 2+target.cycleCount+source.cycleCount+penalty;
	}

	private int handleSHL(int instructionWord) {
		// sets b to b<<a, sets EX to ((b<<a)>>16)&0xffff
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value << source.value;
		ex = (( target.value << source.value)>>16 ) & 0xffff;
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleASR(int instructionWord) {
		// sets b to b>>a, sets EX to ((b<<16)>>>a)&0xffff (arithmetic shift) (treats b as signed)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value >> source.value;
		ex = (( target.value << 16)>>>source.value ) & 0xffff;
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleSHR(int instructionWord) {
		//  sets b to b>>>a, sets EX to ((b<<16)>>a)&0xffff  (logical shift)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );


		final int acc = target.value >>> source.value;
		ex = (( target.value << 16)>>source.value ) & 0xffff;
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleXOR(int instructionWord) 
	{
		//  sets b to b^a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value ^ source.value;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleBOR(int instructionWord) 
	{
		//  sets b to b|a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value | source.value;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;		
	}

	private int handleAND(int instructionWord) {
		// sets b to b&a
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value & source.value;
		return 1+storeTargetOperand( instructionWord , acc )+source.cycleCount;			
	}

	private int handleMDI(int instructionWord) {
		// like MOD, but treat b, a as signed. Rounds towards 0
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc;
		if ( source.value == 0 ) {
			acc=0;
		} else {
			acc = signed( target.value ) % signed( source.value );
		}
		return 3+storeTargetOperand( instructionWord , acc )+source.cycleCount;		
	}

	private int handleMOD(int instructionWord) {
		// sets b to b%a. if a==0, sets b to 0 instead.
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc;
		if ( source.value == 0 ) {
			acc=0;
		} else {
			acc = target.value % source.value;
		}
		return 3+storeTargetOperand( instructionWord , acc )+source.cycleCount;		
	}

	private int handleDVI(int instructionWord) {
		// e DIV, but treat b, a as signed. Rounds towards 0
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc;
		if ( source.value == 0 ) {
			ex = 0;
			acc=0;
		} else {
			acc = signed( target.value ) / signed( source.value );
			ex = (( signed( target.value ) << 16) / signed( source.value) )& 0xffff;
		}
		return 3+storeTargetOperand( instructionWord , acc )+source.cycleCount; 	
	}

	private int handleDIV(int instructionWord) {
		/*set b (TARGET) ,a (SOURCE) 
		 * sets b to b/a, sets EX to ((b<<16)/a)&0xffff. if a==0, sets b and EX to 0 instead. (treats b, a as unsigned)
		 */
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc;
		if ( source.value == 0 ) {
			ex = 0;
			acc=0;
		} else {
			acc = target.value / source.value;
			ex = (( target.value << 16) / source.value)& 0xffff;
		}
		return 3+storeTargetOperand( instructionWord , acc )+source.cycleCount; 		
	}

	private int handleMLI(int instructionWord) {
		//  like MUL, but treat b, a as signed
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = signed( target.value ) * signed( source.value );
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount; 		
	}

	private int signed( int value) 
	{
		if ( ( value & ( 1 << 16 ) ) != 0 ) { // MSB set => negative value
			return value | 0xffff0000;
		}
		return value;
	}

	private int handleMUL(int instructionWord) 
	{
		// sets b to b*a, sets EX to ((b*a)>>16)&0xffff (treats b, a as unsigned)
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value * source.value;
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount; 
	}

	private int handleSUB(int instructionWord) {
		// sets b to b-a, sets EX to 0xffff if there's an underflow, 0x0 otherwise
		OperandDesc source = loadSourceOperand( instructionWord );		
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value - source.value;
		if ( acc < 0 ) {
			ex = 0xffff;
		} else {
			ex = 0;
		}
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount; 		
	}

	private int handleADD(int instructionWord) 
	{
		// sets b to b+a, sets EX to 0x0001 if there's an overflow, 0x0 otherwise
		OperandDesc source = loadSourceOperand( instructionWord );
		OperandDesc target = loadTargetOperand( instructionWord , false );

		final int acc = target.value + source.value;
		if ( acc > 0xffff) {
			ex = 0x0001;
		} else {
			ex = 0;
		}
		return 2+storeTargetOperand( instructionWord , acc )+source.cycleCount; 
	}

	private int handleSET(int instructionWord) 
	{
		final OperandDesc desc = loadSourceOperand( instructionWord );
		return 1+storeTargetOperand( instructionWord , desc.value ) + desc.cycleCount;
	}

	private int handleSpecialOpCode(int instructionWord) {

		final int opCode = ( instructionWord >> 5 ) &0x1f;

		/*
		 *  |--- Special opcodes: (5 bits) --------------------------------------------------
		 *  | C | VAL  | NAME  | DESCRIPTION
		 *  |---+------+-------+-------------------------------------------------------------
		 *  | - | 0x00 | n/a   | reserved for future expansion
		 *  | 3 | 0x01 | JSR a | pushes the address of the next instruction to the stack, then sets PC to a
		 *  | - | 0x02 | -     |
		 *  | - | 0x03 | -     |
		 *  | - | 0x04 | -     |
		 *  | - | 0x05 | -     |
		 *  | - | 0x06 | -     |
		 *  | 9 | 0x07 | HCF a | use sparingly
		 *  | 4 | 0x08 | INT a | triggers a software interrupt with message a
		 *  | 1 | 0x09 | IAG a | sets a to IA 
		 *  | 1 | 0x0a | IAS a | sets IA to a
		 *  | 3 | 0x0b | IAP a | if IA is 0, does nothing, otherwise pushes IA to the stack,
		 *  |   |      |       | then sets IA to a
		 *  | 2 | 0x0c | IAQ a | if a is nonzero, interrupts will be added to the queue
		 *  |   |      |       | instead of triggered. if a is zero, interrupts will be
		 *  |   |      |       | triggered as normal again
		 *  | - | 0x0d | -     |
		 *  | - | 0x0e | -     |
		 *  | - | 0x0f | -     |
		 *  | 2 | 0x10 | HWN a | sets a to number of connected hardware devices
		 *  | 4 | 0x11 | HWQ a | sets A, B, C, X, Y registers to information about hardware a
		 *  |   |      |       | A+(B<<16) is a 32 bit word identifying the hardware id
		 *  |   |      |       | C is the hardware version
		 *  |   |      |       | X+(Y<<16) is a 32 bit word identifying the manufacturer
		 *  | 4+| 0x12 | HWI a | sends an interrupt to hardware a
		 *  | - | 0x13 | -     |
		 *  | - | 0x14 | -     |
		 *  | - | 0x15 | -     |
		 *  | - | 0x16 | -     |
		 *  | - | 0x17 | -     |
		 *  | - | 0x18 | -     |
		 *  | - | 0x19 | -     |
		 *  | - | 0x1a | -     |
		 *  | - | 0x1b | -     |
		 *  | - | 0x1c | -     |
		 *  | - | 0x1d | -     |
		 *  | - | 0x1e | -     |
		 *  | - | 0x1f | -     |
		 *  |---+------+-------+-------------------------------------------------------------		 
		 */

		switch( opCode ) {
		case 0x00:
			return handleUnknownOpCode( instructionWord );
		case 0x01:
			return handleJSR( instructionWord );
		case 0x02:
		case 0x03:
		case 0x04:
		case 0x05:
		case 0x06:
			return handleUnknownOpCode( instructionWord );
		case 0x07:
			return handleHCF( instructionWord );
		case 0x08:
			return handleINT( instructionWord );
		case 0x09:
			return handleIAG( instructionWord );
		case 0x0a:
			return handleIAS( instructionWord );
		case 0x0b:
			return handleIAP( instructionWord );
		case 0x0c:
			return handleIAQ( instructionWord );
		case 0x0d:
		case 0x0e:
		case 0x0f:
			return handleUnknownOpCode( instructionWord );
		case 0x10:
			return handleHWN( instructionWord );
		case 0x11:
			return handleHWQ( instructionWord );
		case 0x12:
			return handleHWI( instructionWord );
		case 0x14:
		case 0x15:
		case 0x16:
		case 0x17:
		case 0x18:
		case 0x19:
		case 0x1a:
		case 0x1b:
		case 0x1c:
		case 0x1d:
		case 0x1e:
		case 0x1f:
		default:
			return handleUnknownOpCode( instructionWord );
		}
	}

	private int handleHWI(int instructionWord) {
		// TODO Auto-generated method stub
		return 4;
	}

	private int handleHWQ(int instructionWord) {
		// TODO Auto-generated method stub
		return 4;
	}

	private int handleHWN(int instructionWord) {
		// TODO Auto-generated method stub
		return 2;
	}

	private int handleIAQ(int instructionWord) {
		// TODO Auto-generated method stub
		return 2;
	}

	private int handleIAP(int instructionWord) {
		// TODO Auto-generated method stub
		return 3;
	}

	private int handleIAS(int instructionWord) {
		// TODO Auto-generated method stub
		return 1;
	}

	private int handleIAG(int instructionWord) {
		// TODO Auto-generated method stub
		return 1;
	}

	private int handleINT(int instructionWord) {
		// TODO Auto-generated method stub
		return 4;
	}

	private int handleHCF(int instructionWord) 
	{
		// return 9+X;
		throw new RuntimeException("HCF not implemented");
	}

	private int handleJSR(int instructionWord) 
	{
		// pushes the address of the next instruction to the stack, then sets PC to a
		OperandDesc source= loadSourceOperand( instructionWord );
		memory[ --sp ] = pc;
		pc = source.value;
		return 3+source.cycleCount;
	}

	/*
	 * 
	 * b is always handled by the processor after a, and is the lower five bits.
	 * In bits (in LSB-0 format), a basic instruction has the format: 
	 * 
	 *    aaaaaabbbbbooooo
	 *    
	 * SET b,a
	 * 
	 * b = TARGET operand
	 * a = SOURCE operand
	 * 
	 * --- Values: (5/6 bits) ---------------------------------------------------------
	 * 
	 * | C | VALUE     | DESCRIPTION
	 * +---+-----------+----------------------------------------------------------------
	 * | 0 | 0x00-0x07 | register (A, B, C, X, Y, Z, I or J, in that order)
	 * | 0 | 0x08-0x0f | [register]
	 * | 1 | 0x10-0x17 | [register + next word]
	 * | 0 |      0x18 | (PUSH / [--SP]) if in TARGET, or (POP / [SP++]) if in SOURCE
	 * | 0 |      0x19 | [SP] / PEEK
	 * | 1 |      0x1a | [SP + next word] / PICK n
	 * | 0 |      0x1b | SP
	 * | 0 |      0x1c | PC
	 * | 0 |      0x1d | EX
	 * | 1 |      0x1e | [next word]
	 * | 1 |      0x1f | next word (literal)
	 * | 0 | 0x20-0x3f | literal value 0xffff-0x1e (-1..30) (literal) (only for SOURCE)
	 * +---+-----------+----------------------------------------------------------------	 
	 */
	private int storeTargetOperand(int instructionWord,int value) 
	{
		final int operandBits = (instructionWord >> 5) & ( 1+2+4+8+16);
		if ( operandBits <= 07 ) {
			registers[ operandBits ]=value;
			return 0;
		}
		if ( operandBits <= 0x0f ) {
			memory[ registers[ operandBits - 0x08 ] ] = value;
			return 1;
		}
		if ( operandBits <= 0x17 ) {
			final int nextWord = memory[ pc++ ];
			memory[ registers[ operandBits - 0x17 ]+nextWord ] = value;
			return 1;
		}
		switch( operandBits ) {
		case 0x18:
			// PUSH / [--SP]
			sp-=1;
			memory[ sp ] = value;
			return 1;
		case 0x19:
			return handleIllegalTargetOperand(instructionWord);
		case 0x1a:
			int nextWord = memory[ pc++ ];
			memory[ sp + nextWord ] = value;
			return 1;
		case 0x1b:
			sp = value;
			return 0;
		case 0x1c:
			pc = value;
			return 0;
		case 0x1d:
			ex = value;
			return 0;
		case 0x1e:
			nextWord = memory[ pc++ ];
			memory[ nextWord ] = value;
			return 1;
		default:
			return handleIllegalTargetOperand(instructionWord); // assignment to literal value
		}
	}

	private int handleIllegalTargetOperand(int instructionWord) {
		throw new RuntimeException("Illegal target operand in instruction word 0x"+
				Misc.toHexString( instructionWord )+" at address 0x"+Misc.toHexString( pc-1 ) );
	}

	private OperandDesc loadSourceOperand(int instructionWord) {

		/* SET b,a
		 * 
		 * b = TARGET operand
		 * a = SOURCE operand
		 * 
		 * Special opcodes always have their lower five bits unset, have one value and a
		 * five bit opcode. In binary, they have the format: aaaaaaooooo00000
		 * The value (a) is in the same six bit format as defined earlier.
		 */

		final int operandBits= (instructionWord >> 10) & ( 1+2+4+8+16+32);
		if ( operandBits <= 0x07 ) {
			return operandDesc( registers[ operandBits ] );
		}
		if ( operandBits <= 0x0f ) {
			return operandDesc( memory[ registers[ operandBits - 0x08 ] ] , 1 );
		}
		if ( operandBits <= 0x17 ) {
			final int nextWord = memory[ pc++ ];
			return operandDesc( memory[ registers[ operandBits - 0x17 ]+nextWord ] ,1 );
		}

		switch( operandBits ) {
		case 0x18:
			// POP / [SP++]
			sp+=1;
			return operandDesc( memory[ sp ] , 1 );
		case 0x19:
			return operandDesc( memory[ sp] , 1 );
		case 0x1a:
			int nextWord = memory[ pc++ ];
			return operandDesc( memory[ sp + nextWord ] , 1 );
		case 0x1b:
			return operandDesc( sp );
		case 0x1c:
			return operandDesc( pc );
		case 0x1d:
			return operandDesc( ex );
		case 0x1e:
			nextWord = memory[ pc++ ];
			return operandDesc( memory[ nextWord ] ,1 );
		case 0x1f:
			return operandDesc( memory[ pc++ ] , 1 );
		}

		// literal value: -1...30 ( 0x20 - 0x3f )
		return operandDesc( operandBits - 0x21 , 0 ); 
	}	

	public OperandDesc loadTargetOperand(int instructionWord,boolean specialInstruction) {


		/* 
		 * SET b,a
		 * 
		 * b = TARGET operand
		 * a = SOURCE operand
		 * 
		 * SOURCE is always handled by the processor BEFORE TARGET, and is the lower five bits.
		 * In bits (in LSB-0 format), a basic instruction has the format: 
		 * 
		 * aaaaaabbbbbooooo		 
		 * 
		 * SPECIAL opcodes always have their lower five bits unset, have one value and a
		 * five bit opcode. In binary, they have the format: 
		 * 
		 * aaaaaaooooo00000
		 * 
		 * The value (a) is in the same six bit format as defined earlier.
		 */

		final int operandBits;

		if ( specialInstruction ) {
			operandBits= (instructionWord >> 10) & ( 1+2+4+8+16+32);
		} else {
			operandBits= (instructionWord >> 5) & ( 1+2+4+8+16+32);			
		}

		if ( operandBits <= 0x07 ) {
			return operandDesc( registers[ operandBits ] );
		}
		if ( operandBits <= 0x0f ) {
			return operandDesc( memory[ registers[ operandBits - 0x08 ] ] , 1 );
		}
		if ( operandBits <= 0x17 ) {
			final int nextWord = memory[ pc++ ];
			return operandDesc( memory[ registers[ operandBits - 0x17 ]+nextWord ] ,1 );
		}

		switch( operandBits ) {
		case 0x18:
			// POP / [SP++]
			sp+=1;
			return operandDesc( memory[ sp ] , 1 );
		case 0x19:
			return operandDesc( memory[ sp] , 1 );
		case 0x1a:
			int nextWord = memory[ pc++ ];
			return operandDesc( memory[ sp + nextWord ] , 1 );
		case 0x1b:
			return operandDesc( sp );
		case 0x1c:
			return operandDesc( pc );
		case 0x1d:
			return operandDesc( ex );
		case 0x1e:
			nextWord = memory[ pc++ ];
			return operandDesc( memory[ nextWord ] ,1 );
		case 0x1f:
			return operandDesc( memory[ pc++ ] , 1 );
		}

		// literal value: -1...30 ( 0x20 - 0x3f )
		return operandDesc( operandBits - 0x21 , 0 ); 

	}

	protected static final class OperandDesc {
		public final int value;
		public final int cycleCount; // how long it takes to perform the operation

		public OperandDesc(int value) {
			this.value = value;
			this.cycleCount = 0;
		}

		public OperandDesc(int value,int cycleCount) {
			this.value = value;
			this.cycleCount = cycleCount;
		}
	}

	private OperandDesc operandDesc(int value) {
		return new OperandDesc( value );
	}

	private OperandDesc operandDesc(int value,int cycleCount) {
		return new OperandDesc( value , cycleCount );
	}

	public void memoryBulkLoad(Address startingOffset, byte[] data) {

		int current = startingOffset.getValue();
		int pointer=0;
		int value=0;
		while ( pointer < data.length ) 
		{
			value= data[pointer++];
			if ( pointer < data.length ) {
				value = (value << 8) | (0xff & data[pointer++]);
			}
			memory[ current++ ] = value;
		}
		System.out.println( "\nMEMORY: \n\n");
		System.out.println( Misc.toHexDumpWithAddresses( 0 , data , 8 ) );
	}	
}
