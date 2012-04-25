package de.codesourcery.jasm16.emulator;

import java.util.LinkedList;
import java.util.concurrent.CyclicBarrier;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.utils.Misc;

public final class Emulator {

    private static final boolean DEBUG = false;
    
	private final Simulation simulation = new Simulation();
	
	@SuppressWarnings("unused")
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
		final Thread t = new Thread( simulation );
		t.setDaemon(true);
		t.setName("simulation-thread");
		this.simulationThread = t;
		t.start();
	}
	
	protected final class Simulation implements Runnable 
	{
		private final Object SLEEP_LOCK = new Object();
		private final CyclicBarrier START_LATCH = new CyclicBarrier(2);		
		private final CyclicBarrier STOP_LATCH = new CyclicBarrier(2);	
		
		private volatile boolean isRunnable = false;
		
		private boolean stopQuietly = false;
		
		private final Memory memory = new Memory();
		private final CPU cpu = new CPU( memory );
		
		private int currentCycle=0;
		private int nextAvailableCycle=1;
		
		private final LinkedList<WorkItem> items = new LinkedList<WorkItem>();
		
		private long lastStart;
		private long lastStop;
		
		private int delay = -1;
		
		public int dummy=1;
		
		public Simulation() {
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
		
		@Override
		public void run() {
		
			System.out.println("Simulation thread running.");

			while( isRunnable == false ) 
			{
				try {
					synchronized( SLEEP_LOCK ) {
						SLEEP_LOCK.wait();
					}
					START_LATCH.await();
				} 
				catch (Exception e) 
				{
					e.printStackTrace();
				}
			}
			lastStart = System.currentTimeMillis();
			while( true ) 
			{
				if ( isRunnable == false ) 
				{
					lastStop = System.currentTimeMillis();
					
					if ( ! stopQuietly ) {
					    System.out.println("*** Emulation stopped after "+getRuntimeInSeconds()+" seconds ( " +
				    		"cycle: "+currentCycle+" , "+getCyclesPerSecond()+" cycles/sec = ~ "+getEstimatedClockSpeed());
					}

					try {
						STOP_LATCH.await();
					} catch (Exception e1) { e1.printStackTrace(); }
					
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
					
					try {
						START_LATCH.await();
					} catch (Exception e) { e.printStackTrace(); }
					
					lastStart = System.currentTimeMillis();					
				}
				
				/* 1 second = 1000 millis = 1.000.000.000 nanos
				 *  
				 * 100 kHz = 100.000 cycles / second = 1000 nanos / cycle
				 */
				int i = 1000;
				while ( i-- > 0 ) 
				{
					advanceOneStep();
					for ( int j = delay ; j > 0 ; j-- ) {
		                dummy = ((dummy*2+j*2)/3)/(dummy*7+j);					    
					}
				}
			}
		}
		
        protected final double measureDelayLoopInNanos() 
        {
            double averages = 0.0d;
            int count = 0;
            for ( int i = 0 ; i < 10 ; i++ ) {
                averages += measureDelayLoop();
                count++;
            }
            return (averages / count);
        }
		
		protected final double measureDelayLoop() 
		{
			final int oldValue = delay;
			
			final int LOOP_COUNT = 1000000;
			delay = LOOP_COUNT;
			
			final long nanoStart = System.nanoTime();
			
			for ( int j = delay ; j > 0 ; j-- ) {
			    dummy = ((dummy*2+j*2)/3)/(dummy*7+j);
			}
			
			long durationNanos = ( System.nanoTime() - nanoStart );
			if ( durationNanos < 0) {
			    durationNanos = -durationNanos;
			}
			
			delay = oldValue;
			return ( (double) durationNanos / (double) LOOP_COUNT);
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
            
            WorkItem item=null; 
            while ( ! items.isEmpty() ) 
            {
                item = items.getFirst();
                if ( item.executionTime == currentCycle ) 
                {
                    if ( DEBUG ) {
                        System.out.println("Executing "+item);
                    }
                    items.removeFirst();
                    item.action.execute( this  , cpu , memory, nextAvailableCycle );
                } 
                else 
                {
                    if ( DEBUG ) {
                        System.out.println("Waiting for cycle "+item.executionTime);
                    }
                    break;
                }
            }
		}
		
		public void schedule(MicroOp action,int duration) 
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
		
		public final void addWorkItem(WorkItem item) 
		{
			final int len = items.size();
			if ( len == 0 ) {
				items.add( item );
				return;
			}
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

        public synchronized void stop() 
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
		
		public synchronized void start() 
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
	}
	
	public enum Mode {
		LOAD,
		STORE;
	}
	
	public static class LoadCommonRegisterAction extends MicroOp {

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
	
    public static class StoreCommonRegisterAction extends MicroOp {

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
    
    public static class LoadCommonRegisterIndirectAction extends MicroOp {

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
    
    public static class StoreCommonRegisterIndirectAction extends MicroOp {

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
    
    public static class LoadOperandBRegisterIndirectWithOffsetAction extends MicroOp {

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
            new MicroOp("offsetB = [ pc++ ]") {

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
            
            new MicroOp("acc := memory[ memory[ PC++ ] ]") {

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
    
    public static class StoreOperandARegisterIndirectWithOffsetAction extends MicroOp {

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
            new MicroOp("cpu.offsetA := [ PC++ ]") {

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
            
            new MicroOp("memory[ register #"+registerIndex+" + offset ] := acc") {

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
    
    public static class StoreRegisterIndirectWithOffsetAction extends MicroOp {

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
            new MicroOp("memory.memory[ cpu.registers[ registerIndex ] + cpu.offsetB ] = cpu.accumulator") {

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
	
	public static abstract class MicroOp 
	{
	    private final String name;
	    
	    public MicroOp(String name) {
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
		            new MicroOp("acc:=POP") 
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
                    new MicroOp("acc:=PEEK") 
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
                    new MicroOp("acc:=PUSH") 
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
                    new MicroOp("acc:=SP") 
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
                    new MicroOp("acc:=PC") 
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
                    new MicroOp("acc:=O") 
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
                    new MicroOp("acc:=[next word]") 
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
                    new MicroOp("acc:=next word (literal)") 
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
                    new MicroOp("POP := acc") 
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
                    new MicroOp("PEEK := acc") 
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
                    new MicroOp("PUSH := acc") 
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
                    new MicroOp("SP := acc") 
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
                    new MicroOp("pc := acc") 
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
                    new MicroOp("O := acc") 
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
                    new MicroOp("[ next word ] := acc") 
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
                    new MicroOp("next word (literal)") 
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
		public final MicroOp action;
		
		public WorkItem(int executionTime, MicroOp action) 
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
//			System.out.println("BULK-LOAD: \n\n"+Misc.toHexDumpWithAddresses( startingOffset , data , 8 ) );
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
	
	
    public static final MicroOp FETCH_AND_DECODE = new MicroOp("fetch_and_decode") {

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
                            new MicroOp("JSR") {
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
                    new MicroOp("SET") {
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

    public void calibrate() 
    {
        final double EXPECTED_CYCLES_PER_SECOND = 100000; // 100 kHz       
        final double expectedNanosPerCycle = (1000.0d * 1000000.0d) / EXPECTED_CYCLES_PER_SECOND;    	

        System.out.println("Calibration started...");
        
    	/*
    	 * Warm-up JVM / JIT.
    	 */
        System.out.println(" *** warm-up ***");
        double sum =0.0d;
        for ( int i = 0 ; i < 5 ; i++ ) {
            final double tmp = simulation.measureDelayLoopInNanos();
            sum+= tmp;
            System.out.println("Nanoseconds per delay loop execution: "+tmp);               
        }

        /*
         * Repeatedly measure the execution time
         * for a single delay-loop iteration.
         */
    	final int LOOP_COUNT=5;
    	sum =0.0d;
    	
        System.out.println(" *** measurement start ***");    	
    	for ( int i = 0 ; i < LOOP_COUNT ; i++ ) {
    	    final double tmp = simulation.measureDelayLoopInNanos();
    	    sum+= tmp;
            System.out.println("Nanoseconds per delay loop execution: "+tmp);    	    
    	}
    	
    	final double nanosPerDelayLoopExecution = sum / LOOP_COUNT;
    	
        System.out.println("Avg. nanoseconds per delay loop execution: "+nanosPerDelayLoopExecution);
        
        /*
         * Measure max. cycles/sec on this machine... 
         */
        simulation.stopQuietly=true;
        double actualCyclesPerSecond = measureActualCyclesPerSecond();
        
        /*
         * Setup initial delay loop iteration count 
         * that we'll be adjusting later
         */
        final double actualNanosPerCycle =   (1000.0d * 1000000.0d) / actualCyclesPerSecond;
        final double delayNanosAccurate= expectedNanosPerCycle - actualNanosPerCycle;
        
        double adjustmentFactor = 1.0d;
        double adjustedNanosPerDelayLoopExecution = nanosPerDelayLoopExecution * adjustmentFactor;        
        simulation.delay = (int) Math.round( delayNanosAccurate / adjustedNanosPerDelayLoopExecution );
        
        System.out.println("Using initial delay of "+delayNanosAccurate+" nanos per cycle ( delay loop iterations: "+simulation.delay+")");
        
        /*
         * Incrementally adjust the delay loop iteration count until
         * we reach clock rate (deviation).
         */
        double increment = 30;
        do 
        {
            actualCyclesPerSecond = measureActualCyclesPerSecond();
    
            final double deltaPercentage = 100.0d* ( ( actualCyclesPerSecond - EXPECTED_CYCLES_PER_SECOND ) / EXPECTED_CYCLES_PER_SECOND );
            
        	if (deltaPercentage < 0.0d || deltaPercentage > 0.9d )
        	{
        	    System.out.println("Deviation: "+deltaPercentage+" % (delay loop iterations: "+simulation.delay+")");
        	    if ( increment < 1 ) {
        	        increment = 1;
        	    }
        	    if ( deltaPercentage > 0 ) {
        	        simulation.delay += increment;
        	    } else {
        	        simulation.delay -= increment;
        	    }
        	    increment = increment*0.7;
        	} else {
        	    break;
        	}
        } while ( true );
        
        System.out.println("Calibration complete.");
        simulation.stopQuietly=false;        
    }

    private double measureActualCyclesPerSecond() 
    {
        final byte[] program = new byte[] {(byte) 0x84,0x01,(byte) 0x81,(byte) 0xc1};
        
        load(  Address.ZERO  , program );
        
        start();
        
        try {
            Thread.sleep( 1 * 1000 );
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop();
        }
        return simulation.getCyclesPerSecond();
    }
}
