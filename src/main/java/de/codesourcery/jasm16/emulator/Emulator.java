package de.codesourcery.jasm16.emulator;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.utils.Misc;

public final class Emulator {

    private static final boolean DEBUG = false;
    
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
	
	protected final class Simulation implements Runnable 
	{
		
		private final Object LOCK = new Object();
		private boolean isRunnable = false;
		
		private final Memory memory = new Memory();
		private final CPU cpu = new CPU( memory );
		
		private int currentCycle;
		private int nextAvailableCycle;
		
		private final List<WorkItem> items = new ArrayList<WorkItem>();
		
		private long lastStart;
		private long lastStop;
		
		public Simulation() {
		    reset();
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
		
		public double getCyclesPerSecond() 
		{
		    if ( lastStart != 0 )
		    {
		        return currentCycle / getRuntimeInSeconds();
		    }
		    return -1.0d;
		}
		
		public String getEstimatedClockSpeed() 
		{
		    final double clockRate = getCyclesPerSecond();
            if ( clockRate == -1.0d ) {
		        return "<cannot calculate clock rate>";
		    }
		    if ( clockRate < 1000 ) {
		        return clockRate+" Hz";
		    } else if ( clockRate < 100000) {
		        return (clockRate/1000)+" kHz";
		    } else if ( clockRate < 1000000000 ) {
                return (clockRate/1000000)+" MHz";
		    }
            return (clockRate/1000000000)+" GHz";		    
		}
		
		@Override
		public void run() {
		
			while( true ) 
			{
				synchronized( LOCK ) 
				{
					while ( isRunnable == false ) 
					{
						try {
						    lastStop = System.currentTimeMillis();
						    System.out.println("*** Emulation stopped after "+getRuntimeInSeconds()+" seconds ( " +
						    		"cycle: "+currentCycle+" , "+getCyclesPerSecond()+" cycles/sec = ~ "+getEstimatedClockSpeed());
							LOCK.wait();
							lastStart = System.currentTimeMillis();
						} 
						catch (InterruptedException e) { /* can't help it */ }
					}
				}
				advanceOneStep();
			}
		}
		
		protected void advanceOneStep() 
		{
            // TICK
            currentCycle++; 
            
            if ( DEBUG ) {
                System.out.println("=== current cycle: "+currentCycle+" ===");
            }
            
            if ( items.isEmpty() ) {
                scheduleFetchInstruction();
            }
            
            while ( ! items.isEmpty() ) 
            {
                WorkItem item = items.get(0);
                if ( item.executionTime == currentCycle ) 
                {
                    if ( DEBUG ) {
                        System.out.println("Executing "+item);
                    }
                    items.remove( 0 );
                    item.action.execute( this  , cpu , memory, nextAvailableCycle );
                } else 
                {
                    if ( DEBUG ) {
                        System.out.println("Waiting for cycle "+item.executionTime);
                    }
                    break;
                }
            }
		}
		
		public void schedule(Action action,int duration) 
		{
		    if ( nextAvailableCycle < currentCycle ) {
		        nextAvailableCycle = currentCycle;
		    }
		    if ( DEBUG ) {
		        System.out.println("Scheduling action: "+action+" [ duration: "+duration+"] on cycle "+nextAvailableCycle);
		    }
			addWorkItem( new WorkItem( nextAvailableCycle, action ) );
			nextAvailableCycle += duration;
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
			lastStart = 0;
			lastStop = 0;
			items.clear();
			currentCycle = 0;
			nextAvailableCycle = 1;			
			cpu.reset();
			memory.reset();
		}		
		
		private void scheduleFetchInstruction()
        {
		    schedule( FETCH_AND_DECODE , 0);
        }

        public void stop() 
		{
			synchronized(LOCK) {
				isRunnable = false;
				LOCK.notifyAll();
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
	
	public static class LoadCommonRegisterAction extends Action {

	    private final int registerIndex;
	    
	    public LoadCommonRegisterAction(int registerIndex) 
	    {
	        super("acc:= register #"+registerIndex);
	        this.registerIndex= registerIndex;
	    }
	    
        @Override
        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
        {
            cpu.accumulator = cpu.registers[registerIndex];
        }

        @Override
        public void schedule(Simulation sim)
        {
            sim.schedule( this , 0 );
        }
	}
	
    public static class StoreCommonRegisterAction extends Action {

        private final int registerIndex;
        
        public StoreCommonRegisterAction(int registerIndex) {
            super("register #"+registerIndex+" := acc");            
            this.registerIndex= registerIndex;
        }
        
        @Override
        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
        {
            cpu.registers[registerIndex] = cpu.accumulator;
        }

        @Override
        public void schedule(Simulation sim)
        {
            sim.schedule( this , 0 );
        }
    }	
    
    public static class LoadCommonRegisterIndirectAction extends Action {

        private final int registerIndex;
        
        public LoadCommonRegisterIndirectAction(int registerIndex) 
        {
            super("acc := [ register #"+registerIndex+" ]");              
            this.registerIndex= registerIndex;
        }
        
        @Override
        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
        {
            cpu.accumulator = memory.memory[ cpu.registers[registerIndex] ];
        }

        @Override
        public void schedule(Simulation sim)
        {
            sim.schedule( this , 1 );
        }
    }	
    
    public static class StoreCommonRegisterIndirectAction extends Action {

        private final int registerIndex;
        
        public StoreCommonRegisterIndirectAction(int registerIndex) 
        {
            super("[ register #"+registerIndex+" ] := acc");              
            this.registerIndex= registerIndex;
        }
        
        @Override
        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
        {
            memory.memory[ cpu.registers[registerIndex] ] = cpu.accumulator;
        }

        @Override
        public void schedule(Simulation sim)
        {
            sim.schedule( this , 1 );
        }
    }       
    
    public static class LoadOperandBRegisterIndirectWithOffsetAction extends Action {

        private final int registerIndex;
        
        public LoadOperandBRegisterIndirectWithOffsetAction(int registerIndex) 
        {
            super("acc = [ register #"+registerIndex+" + offset ]");             
            this.registerIndex= registerIndex;
        }
        
        @Override
        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
        {
            cpu.accumulator = memory.memory[ cpu.registers[registerIndex] ];
        }

        @Override
        public void schedule(Simulation sim)
        {
            new Action("offsetB = [ pc++ ]") {

                @Override
                public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                {
                    cpu.offsetB = memory.memory[ cpu.pc++ ];
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.schedule( this , 1 );
                }
            }.schedule( sim );
            
            new Action("acc := memory[ memory[ PC++ ] ]") {

                @Override
                public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                {
                    cpu.accumulator = memory.memory[ cpu.registers[ registerIndex ] + cpu.offsetB ];
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.schedule( this , 0 );
                }
            }.schedule( sim );            
        }
    }  
    
    public static class StoreOperandARegisterIndirectWithOffsetAction extends Action {

        private final int registerIndex;
        
        public StoreOperandARegisterIndirectWithOffsetAction(int registerIndex) {
            super("memory[ register #"+registerIndex+" +offsetA ] := acc");
            this.registerIndex= registerIndex;
        }
        
        @Override
        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
        {
            memory.memory[ cpu.registers[registerIndex] ] = cpu.accumulator;
        }

        @Override
        public void schedule(Simulation sim)
        {
            new Action("cpu.offsetA := [ PC++ ]") {

                @Override
                public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                {
                    cpu.offsetA = memory.memory[ cpu.pc++ ];
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.schedule( this , 1 );
                }
            }.schedule( sim );
            
            new Action("memory[ register #"+registerIndex+" + offset ] := acc") {

                @Override
                public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                {
                    memory.memory[ cpu.registers[ registerIndex ] + cpu.offsetA ] = cpu.accumulator;
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.schedule( this , 0 );
                }
            }.schedule( sim );            
        }
    }    
    
    public static class StoreRegisterIndirectWithOffsetAction extends Action {

        private final int registerIndex;
        
        public StoreRegisterIndirectWithOffsetAction(int registerIndex) 
        {
            super("[ register #"+registerIndex+" + offsetB ] := acc");
            this.registerIndex= registerIndex;
        }
        
        @Override
        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
        {
            cpu.accumulator = memory.memory[ cpu.registers[registerIndex] ];
        }

        @Override
        public void schedule(Simulation sim)
        {
            new Action("memory.memory[ cpu.registers[ registerIndex ] + cpu.offsetB ] = cpu.accumulator") {

                @Override
                public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                {
                    memory.memory[ cpu.registers[ registerIndex ] + cpu.offsetB ] = cpu.accumulator;
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.schedule( this , 1 );
                }
            }.schedule( sim );
        }
    }     
	
	public static abstract class Action 
	{
	    private final String name;
	    
	    public Action(String name) {
	        this.name = name;
	    }
	    
	    @Override
	    public String toString() {
	        return name;
	    }
	    
		public abstract void execute(Simulation sim,CPU cpu,Memory memory, int nextAvailableCycle);
		
		public abstract void schedule(Simulation sim);
		
		protected  void loadAccumulator(Simulation sim,int operandBits) 
		{
		    switch( operandBits ) 
		    {
		        case 0x18: // POP / [SP++]
		            new Action("acc:=POP") 
		            {

                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.accumulator = memory.memory[ cpu.sp++ ];
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );
		            return;
		        case 0x19: // PEEK / [SP]
                    new Action("acc:=PEEK") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.accumulator = memory.memory[ cpu.sp ];
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );		            
		            return;
		        case 0x1a: // PUSH / [--SP]
                    new Action("acc:=PUSH") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.accumulator = memory.memory[ --cpu.sp ];
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );                 
                    return;
		        case 0x1b: // SP
                    new Action("acc:=SP") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.accumulator = cpu.sp;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 0 );
                        }
                     }.schedule( sim );                 
                    return;		
                case 0x1c: // PC
                    new Action("acc:=PC") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.accumulator = cpu.pc;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 0 );
                        }
                     }.schedule( sim );                 
                    return;     
                case 0x1d: // O
                    new Action("acc:=O") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.accumulator = cpu.o;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 0 );
                        }
                     }.schedule( sim );                 
                    return;    
                case 0x1e: // [next word]
                    new Action("acc:=[next word]") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            final int adr = memory.memory[ cpu.pc++ ];
                            cpu.accumulator = memory.memory[ adr ];
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );                 
                    return;          
                case 0x1f: // next word (literal)
                    new Action("acc:=next word (literal)") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.accumulator = memory.memory[ cpu.pc++ ];
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );                 
                    return;                     
		    }
		    
		    //  0x20-0x3f: literal value 0x00-0x1f (literal)     
		    if ( operandBits >= 0x20 ) {
		        sim.cpu.accumulator = operandBits - 0x20;
		        return;
		    }
		    
            // register immediate ?
            int value = operandBits & 0x07; // bits 0-2
            if ( value <= 0x07) 
            {
                new LoadCommonRegisterAction( value ).schedule( sim );
                return;
            }
            
            // register indirect?
            value = (operandBits >> 3) & 0x07; // bits 3-5
            if ( value <= 0x07) 
            {
                new LoadCommonRegisterIndirectAction( value ).schedule( sim );
                return;
            }
            
            // register indirect with offset?
            value = (operandBits >> 6) & 0x07; // bits 6-8
            if ( value <= 0x07) 
            {
                new LoadOperandBRegisterIndirectWithOffsetAction( value ).schedule( sim );
                return;                
            }               
		}
		
		protected  void storeAccumulator(Simulation sim,int operandBits) 
        {
            switch( operandBits ) 
            {
                case 0x18: // POP / [SP++]
                    new Action("POP := acc") 
                    {

                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            memory.memory[ cpu.sp++ ] = cpu.accumulator;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );
                    return;
                case 0x19: // PEEK / [SP]
                    new Action("PEEK := acc") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            memory.memory[ cpu.sp ] = cpu.accumulator;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );                 
                    return;
                case 0x1a: // PUSH / [--SP]
                    new Action("PUSH := acc") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            memory.memory[ --cpu.sp ] = cpu.accumulator;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );                 
                    return;
                case 0x1b: // SP
                    new Action("SP := acc") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.sp = cpu.accumulator;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 0 );
                        }
                     }.schedule( sim );                 
                    return;     
                case 0x1c: // PC
                    new Action("pc := acc") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            cpu.pc = cpu.accumulator;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 0 );
                        }
                     }.schedule( sim );                 
                    return;     
                case 0x1d: // O
                    new Action("O := acc") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            // cpu.accumulator = cpu.o;
                            throw new RuntimeException("Assigning register O is not possible");
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 0 );
                        }
                     }.schedule( sim );                 
                    return;    
                case 0x1e: // [next word]
                    new Action("[ next word ] := acc") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            final int adr = memory.memory[ cpu.pc++ ];
                            memory.memory[ adr ] = cpu.accumulator;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );                 
                    return;          
                case 0x1f: // next word (literal)
                    new Action("next word (literal)") 
                    {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            memory.memory[ cpu.pc++ ] = cpu.accumulator;
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            sim.schedule( this , 1 );
                        }
                     }.schedule( sim );                 
                    return;                     
            }
            
            //  0x20-0x3f: literal value 0x00-0x1f (literal)     
            if ( operandBits >= 0x20 ) {
                throw new RuntimeException("Trying to assign a literal value?");
            }
            
            // register immediate ?
            int value = operandBits & 0x07; // bits 0-2
            if ( value <= 0x07) 
            {
                new StoreCommonRegisterAction( value ).schedule( sim );
                return;
            }
            
            // register indirect?
            value = (operandBits >> 3) & 0x07; // bits 3-5
            if ( value <= 0x07) 
            {
                new LoadCommonRegisterIndirectAction( value ).schedule( sim );
                return;
            }
            
            // register indirect with offset?
            value = (operandBits >> 6) & 0x07; // bits 6-8
            if ( value <= 0x07) 
            {
                new StoreOperandARegisterIndirectWithOffsetAction( value ).schedule( sim );
                return;                
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
		
		@Override
		public String toString()
		{
		    return "executionOnCycle="+executionTime+", action = "+action;
		}
	}
	
	public static final class CPU 
	{
		public final int[] registers=new int[8];
		
		public int pc;
		public int sp;
		public int o;
		
		public int offsetA; // SET [ register + offset ] , ...
		public int offsetB; // SET x , [ register + offset ]
		public int accumulator;
		
		private final Memory mem;
		
		public CPU(Memory memory) {
			mem = memory;
		}
		
		public void reset() 
		{
		    System.out.println("*** CPU reset ***");
		    for ( int i = 0 ; i < registers.length ;i++) {
		        registers[i]=0;
		    }
			pc=sp=o=0;
		}
		
		public int fetchInstruction() 
		{
			final int instruction = mem.memory[ pc++ ];
			if ( DEBUG ) {
			    System.out.println( "next instruction => "+Misc.toHexString( pc-1 )+": "+Misc.toHexString( instruction ) );
			}
			return instruction;
		}
	}
	
	public static final class Memory {
		
		private int[] memory = new int[65535];
		
		public int read(int address) 
		{
			final int result = memory[address];
	        System.out.println("MEM: Read value 0x"+Misc.toHexString( result)+" from 0x"+Misc.toHexString( address ));
	        return result;
		}
		
		public void write(int address,int value) 
		{
	        System.out.println("MEM: Writing value 0x"+Misc.toHexString( value )+" to 0x"+Misc.toHexString( address ));
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
					value = (value << 8) | (0xff & data[pointer++]);
				}
				memory[ current++ ] = value;
			}
			System.out.println("BULK-LOAD: \n\n"+Misc.toHexDumpWithAddresses( startingOffset , data , 8 ) );
		}
		
		public void reset() 
		{
		    System.out.println("*** Memory reset ***");		    
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
	
	
    public static final Action FETCH_AND_DECODE = new Action("fetch_and_decode") {

        @Override
        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
        {
            // fetch instruction word
            final int instruction = cpu.fetchInstruction();
            
            // decode operand bits
            final int opCode = instruction & 0x0f; // lowest 4 bits
            switch( opCode ) 
            {
                case 0: // => extended instruction ?
                    switch( (instruction >> 4) & 0x3f ) 
                    {
                        case 1: // JSR a - pushes the address of the next instruction to the stack, then sets PC to a
                            new Action("JSR") {
                                @Override
                                public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                                {
                                    loadAccumulator( sim , (instruction >> 4) & (1+2+4+8+16+32) );
                                    storeAccumulator( sim , (instruction >> 10) & (1+2+4+8+16+32) );
                                }

                                @Override
                                public void schedule(Simulation sim)
                                {
                                    int operandBits = (instruction >> 4) & (1+2+4+8+16+32);
                                    loadAccumulator(sim , operandBits );
                                    sim.schedule( this  , 0 );
                                }
                             }.schedule( sim );                            
                            return;
                        default:
                            throw new RuntimeException("Cannot handle reserved opcode "+Misc.toHexString( opCode ));
                                // RESERVED OPCODE
                    }
                case 1: // 0x1: SET a, b - sets a to b
                    new Action("SET") {
                        @Override
                        public void execute(Simulation sim, CPU cpu, Memory memory, int nextAvailableCycle)
                        {
                            storeAccumulator( sim , (instruction >> 4) & (1+2+4+8+16+32) );
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            int operandBits = (instruction >> 10) & (1+2+4+8+16+32);
                            loadAccumulator(sim , operandBits );
                            sim.schedule( this  , 1 );
                        }
                     }.schedule( sim );
                    break;
//                case 2: // 0x2: ADD a, b - sets a to a+b, sets O to 0x0001 if there's an overflow, 0x0 otherwise
//                    op= OpCode.ADD;
//                    break;
//                case 3: // 0x3: SUB a, b - sets a to a-b, sets O to 0xffff if there's an underflow, 0x0 otherwise
//                    op= OpCode.SUB;
//                    break;
//                case 4: // 0x4: MUL a, b - sets a to a*b, sets O to ((a*b)>>16)&0xffff
//                    op= OpCode.MUL;
//                    break;
//                case 5: // 0x5: DIV a, b - sets a to a/b, sets O to ((a<<16)/b)&0xffff. if b==0, sets a and O to 0 instead.
//                    op= OpCode.DIV;
//                    break;
//                case 6: // 0x6: MOD a, b - sets a to a%b. if b==0, sets a to 0 instead.
//                    op= OpCode.MOD;
//                    break;
//                case 7: // 0x7: SHL a, b - sets a to a<<b, sets O to ((a<<b)>>16)&0xffff
//                    op= OpCode.SHL;
//                    break;
//                case 8: // 0x8: SHR a, b - sets a to a>>b, sets O to ((a<<16)>>b)&0xffff
//                    op= OpCode.SHR;
//                    break;
//                case 9: // 0x9: AND a, b - sets a to a&b
//                    op= OpCode.AND;
//                    break;
//                case 10: // 0xa: BOR a, b - sets a to a|b
//                    op= OpCode.OR;
//                    break;
//                case 11: // 0xb: XOR a, b - sets a to a^b
//                    op= OpCode.XOR;
//                    break;
//                case 12: // 0xc: IFE a, b - performs next instruction only if a==b
//                    op= OpCode.IFE;
//                    break;
//                case 13: // 0xd: IFN a, b - performs next instruction only if a!=b
//                    op= OpCode.IFN;
//                    break;
//                case 14: // 0xe: IFG a, b - performs next instruction only if a>b
//                    op= OpCode.IFG;
//                    break;
//                case 15: // 0xf: IFB a, b - performs next instruction only if (a&b)!=0
//                    op= OpCode.IFB;
//                    break;
            }
        }

        @Override
        public void schedule(Simulation sim)
        {
            sim.schedule(this,0);
        }
        
    };	
	
}
