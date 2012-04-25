package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

public final class Emulator {

    private static final Simulation sim = new Simulation();
    
	@SuppressWarnings("unused")
	private final Thread simulationThread;
	
	public Emulator() 
	{
		final Thread t = new Thread( sim );
		t.setDaemon(true);
		t.setName("simulation-thread");
		this.simulationThread = t;
		t.start();
	}
	
	public enum Mode {
		LOAD,
		STORE;
	}
	
    public static class StoreCommonRegisterIndirectAction extends MicroOp {

        private final int registerIndex;
        
        public StoreCommonRegisterIndirectAction(int registerIndex) 
        {
            super("[ register #"+registerIndex+" ] := acc");              
            this.registerIndex= registerIndex;
        }
        
        @Override
        public void execute(Simulation sim)
        {
            sim.memory[ sim.registers[registerIndex] ] = sim.accumulator;
        }

        @Override
        public void schedule(Simulation sim)
        {
            sim.scheduleOneDuration( this );
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
        public void execute(Simulation sim)
        {
            sim.accumulator = sim.memory[ sim.registers[registerIndex] ];
        }

        @Override
        public void schedule(Simulation sim)
        {
            new MicroOp("offsetB = [ pc++ ]") {

                @Override
                public void execute(Simulation sim)
                {
                    sim.offsetB = sim.memory[ sim.pc++ ];
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.scheduleOneDuration( this );
                }
            }.schedule(sim );
            
            new MicroOp("acc := memory[ memory[ PC++ ] ]") {

                @Override
                public void execute(Simulation sim)
                {
                    sim.accumulator = sim.memory[ sim.registers[ registerIndex ] + sim.offsetB ];
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.scheduleZeroDuration( this );
                }
            }.schedule(sim );            
        }
    }  
    
    public static class StoreOperandARegisterIndirectWithOffsetAction extends MicroOp {

        private final int registerIndex;
        
        public StoreOperandARegisterIndirectWithOffsetAction(int registerIndex) {
            super("memory[ register #"+registerIndex+" +offsetA ] := acc");
            this.registerIndex= registerIndex;
        }
        
        @Override
        public void execute(Simulation sim)
        {
            sim.memory[ sim.registers[registerIndex] ] = sim.accumulator;
        }

        @Override
        public void schedule(Simulation sim)
        {
            new MicroOp("sim.offsetA := [ PC++ ]") {

                @Override
                public void execute(Simulation sim)
                {
                    sim.offsetA = sim.memory[ sim.pc++ ];
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.scheduleOneDuration( this );
                }
            }.schedule(sim );
            
            new MicroOp("memory[ register #"+registerIndex+" + offset ] := acc") {

                @Override
                public void execute(Simulation sim)
                {
                    sim.memory[ sim.registers[ registerIndex ] + sim.offsetA ] = sim.accumulator;
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.scheduleZeroDuration( this );
                }
            }.schedule(sim );            
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
        public void execute(Simulation sim)
        {
            sim.accumulator = sim.memory[ sim.registers[registerIndex] ];
        }

        @Override
        public void schedule(Simulation sim)
        {
            new MicroOp("sim.memory[ sim.registers[ registerIndex ] + sim.offsetB ] = sim.accumulator") {

                @Override
                public void execute(Simulation sim)
                {
                    sim.memory[ sim.registers[ registerIndex ] + sim.offsetB ] = sim.accumulator;
                }

                @Override
                public void schedule(Simulation sim)
                {
                    sim.scheduleOneDuration( this );
                }
            }.schedule(sim );
        }
    }     
	
	public void load(Address startingAddress , byte[] program) 
	{
	    sim.load( startingAddress , program );
	}
	
	public void reset() 
	{
	    sim.reset();
	}
	
	public void start() {
	    sim.start();
	}
	
	public void stop() {
	    sim.stop();
	}
	
	/* ===============
	 *    Microcode
	 * ===============
	 */
	
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
            final double tmp = sim.measureDelayLoopInNanos();
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
    	    final double tmp = sim.measureDelayLoopInNanos();
    	    sum+= tmp;
            System.out.println("Nanoseconds per delay loop execution: "+tmp);    	    
    	}
    	
    	final double nanosPerDelayLoopExecution = sum / LOOP_COUNT;
    	
        System.out.println("Avg. nanoseconds per delay loop execution: "+nanosPerDelayLoopExecution);
        
        /*
         * Measure max. cycles/sec on this machine... 
         */
        sim.setStopQuietly( true );
        double actualCyclesPerSecond = measureActualCyclesPerSecond();
        
        /*
         * Setup initial delay loop iteration count 
         * that we'll be adjusting later
         */
        final double actualNanosPerCycle =   (1000.0d * 1000000.0d) / actualCyclesPerSecond;
        final double delayNanosAccurate= expectedNanosPerCycle - actualNanosPerCycle;
        
        double adjustmentFactor = 1.0d;
        double adjustedNanosPerDelayLoopExecution = nanosPerDelayLoopExecution * adjustmentFactor;        
        sim.delay = (int) Math.round( delayNanosAccurate / adjustedNanosPerDelayLoopExecution );
        
        System.out.println("Using initial delay of "+delayNanosAccurate+" nanos per cycle ( delay loop iterations: "+sim.delay+")");
        
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
        	    System.out.println("Deviation: "+deltaPercentage+" % (delay loop iterations: "+sim.delay+")");
        	    if ( increment < 1 ) {
        	        increment = 1;
        	    }
        	    if ( deltaPercentage > 0 ) {
        	        sim.delay += increment;
        	    } else {
        	        sim.delay -= increment;
        	    }
        	    increment = increment*0.7;
        	} else {
        	    break;
        	}
        } while ( true );
        
        System.out.println("Calibration complete.");
        sim.setStopQuietly(false);        
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
        return sim.getCyclesPerSecond();
    }
}
