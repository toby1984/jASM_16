package de.codesourcery.jasm16.emulator;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressingMode;
import de.codesourcery.jasm16.OpCode;

public final class Emulator {

	public static final int REG_A = 0;
	public static final int REG_B = 1;
	public static final int REG_C = 2;
	public static final int REG_X = 3;
	public static final int REG_Y = 4;
	public static final int REG_Z = 5;
	public static final int REG_I = 6;
	public static final int REG_J = 7;
	
	private final Simulation simulation = new Simulation();
	private final Thread simulationThread;
	
	/*
Basic opcodes: (4 bits)
    0x0: non-basic instruction - see below
    0x1: SET a, b - sets a to b
    0x2: ADD a, b - sets a to a+b, sets O to 0x0001 if there's an overflow, 0x0 otherwise
    0x3: SUB a, b - sets a to a-b, sets O to 0xffff if there's an underflow, 0x0 otherwise
    0x4: MUL a, b - sets a to a*b, sets O to ((a*b)>>16)&0xffff
    0x5: DIV a, b - sets a to a/b, sets O to ((a<<16)/b)&0xffff. if b==0, sets a and O to 0 instead.
    0x6: MOD a, b - sets a to a%b. if b==0, sets a to 0 instead.
    0x7: SHL a, b - sets a to a<<b, sets O to ((a<<b)>>16)&0xffff
    0x8: SHR a, b - sets a to a>>b, sets O to ((a<<16)>>b)&0xffff
    0x9: AND a, b - sets a to a&b
    0xa: BOR a, b - sets a to a|b
    0xb: XOR a, b - sets a to a^b
    0xc: IFE a, b - performs next instruction only if a==b
    0xd: IFN a, b - performs next instruction only if a!=b
    0xe: IFG a, b - performs next instruction only if a>b
    0xf: IFB a, b - performs next instruction only if (a&b)!=0
    
* SET, AND, BOR and XOR take 1 cycle, plus the cost of a and b
* ADD, SUB, MUL, SHR, and SHL take 2 cycles, plus the cost of a and b
* DIV and MOD take 3 cycles, plus the cost of a and b
* IFE, IFN, IFG, IFB take 2 cycles, plus the cost of a and b, plus 1 if the test fails
    

    
Non-basic opcodes always have their lower four bits unset, have one value and a six bit opcode.
In binary, they have the format: aaaaaaoooooo0000
The value (a) is in the same six bit format as defined earlier.

Non-basic opcodes: (6 bits)
         0x00: reserved for future expansion
         0x01: JSR a - pushes the address of the next instruction to the stack, then sets PC to a
    0x02-0x3f: reserved	 
	 */
	
	public Emulator() 
	{
		Thread t = new Thread( simulation );
		t.setDaemon(true);
		t.setName("simulation-thread");
		this.simulationThread = t;
		t.start();
	}
	
	protected final class Simulation implements Runnable {
		
		private final Object LOCK = new Object();
		private boolean isRunnable = false;
		
		private final Memory memory = new Memory();
		private final CPU cpu = new CPU( memory );
		
		private int currentCycle;
		private int nextAvailableCycle;
		
		private final List<WorkItem> items = new ArrayList<WorkItem>();
		
		@Override
		public void run() {
		
			while( true ) 
			{
				synchronized( LOCK ) 
				{
					while ( isRunnable == false ) 
					{
						try {
							LOCK.wait();
						} 
						catch (InterruptedException e) { /* can't help it */ }
					}
				}
				
				// TICK
				currentCycle++; 
				while ( ! items.isEmpty() ) 
				{
					WorkItem item = items.get(0);
					if ( item.executionTime == currentCycle ) 
					{
						items.remove( 0 );
						item.action.execute( cpu  , memory , nextAvailableCycle );
					} else {
						break;
					}
				}
			}
		}
		
		public void schedule(Action action,int onCycle) 
		{
			addWorkItem( new WorkItem( onCycle, action ) );
		}
		
		public void advanceOneStep() 
		{
			// fetch instruction word
			final int instruction = cpu.fetchInstruction();
			
			
			// decode operand bits
			final int opCode = instruction & 0x0f; // lowest 4 bits
			final Action op;
			switch( opCode ) 
			{
				case 0: // => extended instruction ?
					switch( (instruction >> 4) & 0x3f ) 
					{
						case 1: // JSR a - pushes the address of the next instruction to the stack, then sets PC to a
							op= OpCode.JSR;
							break;
							default:
								op = null;
								// RESERVED OPCODE
					}
					break;
				case 1: // 0x1: SET a, b - sets a to b
					op= OpCode.SET;
					break;
				case 2: // 0x2: ADD a, b - sets a to a+b, sets O to 0x0001 if there's an overflow, 0x0 otherwise
					op= OpCode.ADD;
					break;
				case 3: // 0x3: SUB a, b - sets a to a-b, sets O to 0xffff if there's an underflow, 0x0 otherwise
					op= OpCode.SUB;
					break;
				case 4: // 0x4: MUL a, b - sets a to a*b, sets O to ((a*b)>>16)&0xffff
					op= OpCode.MUL;
					break;
				case 5: // 0x5: DIV a, b - sets a to a/b, sets O to ((a<<16)/b)&0xffff. if b==0, sets a and O to 0 instead.
					op= OpCode.DIV;
					break;
				case 6: // 0x6: MOD a, b - sets a to a%b. if b==0, sets a to 0 instead.
					op= OpCode.MOD;
					break;
				case 7: // 0x7: SHL a, b - sets a to a<<b, sets O to ((a<<b)>>16)&0xffff
					op= OpCode.SHL;
					break;
				case 8: // 0x8: SHR a, b - sets a to a>>b, sets O to ((a<<16)>>b)&0xffff
					op= OpCode.SHR;
					break;
				case 9: // 0x9: AND a, b - sets a to a&b
					op= OpCode.AND;
					break;
				case 10: // 0xa: BOR a, b - sets a to a|b
					op= OpCode.OR;
					break;
				case 11: // 0xb: XOR a, b - sets a to a^b
					op= OpCode.XOR;
					break;
				case 12: // 0xc: IFE a, b - performs next instruction only if a==b
					op= OpCode.IFE;
					break;
				case 13: // 0xd: IFN a, b - performs next instruction only if a!=b
					op= OpCode.IFN;
					break;
				case 14: // 0xe: IFG a, b - performs next instruction only if a>b
					op= OpCode.IFG;
					break;
				case 15: // 0xf: IFB a, b - performs next instruction only if (a&b)!=0
					op= OpCode.IFB;
					break;
			}
			
		}
		
		private void crash() 
		{
			System.out.println("Reserved/unknown opcode at "+(cpu.pc-1));
			stop();
		}
		
		public void addWorkItem(WorkItem item) 
		{
			final int len = items.size();
			final int newTime = item.executionTime;
			for ( int i = 0 ; i < len ; i++ ) 
			{
				if ( newTime <= items.get(i).executionTime ) 
				{
					items.add( i , item ); 
					return;
				}
			}
			items.add( item );
		}
		
		public void load(Address startingAddress , byte[] program) 
		{
			reset();
			memory.bulkLoad( startingAddress.getValue() , program );
		}
		
		public void reset() 
		{
			stop();
			currentCycle = 0;
			nextAvailableCycle = 0;			
			cpu.reset();
			memory.reset();
		}		
		
		public void stop() 
		{
			synchronized(LOCK) {
				isRunnable = false;
			}
		}
		
		public void start() 
		{
			synchronized(LOCK) {
				isRunnable = true;
				LOCK.notifyAll();
			}
		}
	}
	
	public enum Mode {
		LOAD,
		STORE;
	}
	
	public static abstract class Action 
	{
		public abstract void execute(CPU cpu,Memory memory,int nextAvailableCycle);
		
		public abstract void schedule(Simulation sim);
		
		public void scheduleOperandA(Simulation sim , int instruction,Mode mode) {
			final int op  = (instruction >> 4) & 0x3f; // 6 bits
			final int operandBits = op & (1+2+4+8+16+32);
			scheduleOperands( sim , instruction , operandBits , mode );
		}
		
		public void scheduleOperandB(Simulation sim , int instruction,Mode mode) 
		{
			final int op= (instruction >> 10) & 0x3f; // 6 bits
			final int operandBits = op & (1+2+4+8+16+32);	
			scheduleOperands( sim , instruction , operandBits , mode );
		}		
		
		public void scheduleOperands(Simulation sim , int instruction, int operandBits , Mode mode) 
		{
			// register immediate ?
			int value = operandBits & 0x07; // bits 0-2
			if ( value <= 0x07) 
			{
				switch( value ) 
				{
					case 0:
						if ( mode == Mode.LOAD ) {
							LOAD_A.schedule( sim );
						} else {
							STORE_A.schedule( sim );
						}
						return;
					case 1:
						if ( mode == Mode.LOAD ) {
							LOAD_B.schedule( sim );
						} else {
							STORE_B.schedule( sim );
						}
						return;					
					case 2:
						if ( mode == Mode.LOAD ) {
							LOAD_C.schedule( sim );
						} else {
							STORE_C.schedule( sim );
						}
						return;					
					case 3:
						if ( mode == Mode.LOAD ) {
							LOAD_X.schedule( sim );
						} else {
							STORE_X.schedule( sim );
						}
						return;					
					case 4:
						if ( mode == Mode.LOAD ) {
							LOAD_Y.schedule( sim );
						} else {
							STORE_Y.schedule( sim );
						}
						return;							
					case 5:
						if ( mode == Mode.LOAD ) {
							LOAD_Z.schedule( sim );
						} else {
							STORE_Z.schedule( sim );
						}
						return;						
					case 6:
						if ( mode == Mode.LOAD ) {
							LOAD_I.schedule( sim );
						} else {
							STORE_I.schedule( sim );
						}
						return;						
					case 7:				
						if ( mode == Mode.LOAD ) {
							LOAD_J.schedule( sim );
						} else {
							STORE_J.schedule( sim );
						}
						return;	
				}
			}
			
			/*

Values: (6 bits)
    0x00-0x07: register (A, B, C, X, Y, Z, I or J, in that order)
    0x08-0x0f: [register]
    0x10-0x17: [next word + register]
         0x18: POP / [SP++]
         0x19: PEEK / [SP]
         0x1a: PUSH / [--SP]
         0x1b: SP
         0x1c: PC
         0x1d: O
         0x1e: [next word]
         0x1f: next word (literal)
    0x20-0x3f: literal value 0x00-0x1f (literal)
    
* "next word" really means "[PC++]". These increase the word length of the instruction by 1. 
* If any instruction tries to assign a literal value, the assignment fails silently. Other than that, the instruction behaves as normal.
* All values that read a word (0x10-0x17, 0x1e, and 0x1f) take 1 cycle to look up. The rest take 0 cycles.
* By using 0x18, 0x19, 0x1a as POP, PEEK and PUSH, there's a reverse stack starting at memory location 0xffff. Example: "SET PUSH, 10", "SET X, POP"
			 */			
			
			value = (operandBits >> 3) & 0x07; // bits 3-5 ( 8+16+32)
			if ( value <= 0x07) 
			{			
				
			}
		}		
	}
	
	public static final class WorkItem 
	{
		public final int executionTime;
		public final Action action;
		
		public WorkItem(int executionTime, Action action) 
		{
			this.executionTime = executionTime;
			this.action = action;
		}
	}
	
	public static final class CPU 
	{
		
		/*
		 *     0x00-0x07: register (A, B, C, X, Y, Z, I or J, in that order)
A, B, C, X, Y, Z, I or J		 
		 */
		public int a;
		public int b;
		public int c;
		public int x;
		public int y;
		public int z;
		public int i;
		public int j;
		
		public int pc;
		public int sp;
		public int o;
		
		public int accumulator;
		
		private final Memory mem;
		
		public CPU(Memory memory) {
			mem = memory;
		}
		
		public void reset() 
		{
			a=b=c=x=y=z=i=j=0;
			pc=sp=o=0;
		}
		
		public int fetchInstruction() {
			return mem.memory[ pc++ ];
		}
	}
	
	public static final class Memory {
		
		private int[] memory = new int[65535];
		
		public int read(int address) 
		{
			return memory[address];
		}
		
		public void write(int address,int value) {
			memory[address]= value;
		}
		
		public void bulkLoad(int startingOffset, byte[] data) {
			
			int current = startingOffset;
			int pointer=0;
			int value=0;
			while ( pointer < data.length ) 
			{
				value= data[pointer++];
				if ( pointer < data.length ) {
					value = (value << 8) | data[pointer++];
				}
				memory[ current++ ] = value;
			}
		}
		
		public void reset() 
		{
			for ( int i = 0 ; i < memory.length ; i++ ) {
				memory[i]=0;
			}
		}
	}
	
	public void load(Address startingAddress , byte[] program) 
	{
		simulation.load( startingAddress , program );
	}
	
	public void reset() 
	{
		simulation.reset();
	}
	
	public void start() {
		simulation.start();
	}
	
	public void stop() {
		simulation.stop();
	}
	
	/* ===============
	 *    Microcode
	 * ===============
	 */
	
	
	/* ============
	 * === LOAD ===
	 * ============
	 */
	
	// LOAD register
	public static final Action LOAD_A = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.a;
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};
	
	public static final Action LOAD_B = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.b;
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};
	
	public static final Action LOAD_C = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.c;
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};	
	
	public static final Action LOAD_I = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.i;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};	
	
	public static final Action LOAD_J = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.j;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};	
	
	public static final Action LOAD_X = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.x;
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};	
	
	public static final Action LOAD_Y = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.y;
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};	
	
	public static final Action LOAD_Z = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.z;
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};
	
	// ============= LOAD INDIRECT =====================
	
	public static final Action LOAD_INDIRECT_A = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.accumulator = cpu.mem.read( cpu.a );
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};	
	
	/* ======================================
	 * =============== STORE ================
	 * ======================================
	 */
	
	// ====================== Store register ====================
	
	public static final Action STORE_A = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.a = cpu.accumulator;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};
	
	public static final Action STORE_B = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.b = cpu.accumulator;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};		
	
	public static final Action STORE_C = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.c = cpu.accumulator;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};
	
	public static final Action STORE_X = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.x = cpu.accumulator;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};		
	
	public static final Action STORE_Y = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.y = cpu.accumulator;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};
	
	public static final Action STORE_Z = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.z = cpu.accumulator;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};
	
	public static final Action STORE_I = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.i = cpu.accumulator;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};	
	
	public static final Action STORE_J = new Action() 
	{
		@Override
		public void execute(CPU cpu, Memory memory, int nextAvailableCycle) 
		{
			cpu.j = cpu.accumulator;			
		}

		@Override
		public void schedule(Simulation sim) {
			sim.schedule( this , sim.currentCycle );
		}
	};		
}
