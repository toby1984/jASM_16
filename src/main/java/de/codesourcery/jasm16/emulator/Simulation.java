package de.codesourcery.jasm16.emulator;

import java.util.concurrent.CyclicBarrier;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.utils.Misc;

public final class Simulation implements Runnable 
{
    private static final boolean DEBUG = false;
    
    private static final boolean USE_DELAY_LOOP = false;
    
    private final Object SLEEP_LOCK = new Object();
    
    private final CyclicBarrier START_LATCH = new CyclicBarrier(2);     
    private final CyclicBarrier STOP_LATCH = new CyclicBarrier(2);  
    
    private volatile boolean isRunnable = false;
    
    private boolean stopQuietly = false;
    
    public int currentCycle=0;
    private int nextAvailableCycle=1;
    
    private final WorkItemList items = new WorkItemList();
    
    private long lastStart;
    private long lastStop;
    
    public int delay = -1;
    
    public int dummy=1;
    
    // ============ Memory ============
    
    public int[] memory = new int[65535];
    
    // ============ CPU =============== 
    
    public final int[] registers=new int[8];
    
    public int pc;
    public int sp;
    public int o;
    
    public int offsetA; // SET [ register + offset ] , ...
    public int offsetB; // SET x , [ register + offset ]
    public int accumulator;     
    
    /**
     * Run simulation.
     * 
     */
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
                }
                catch (Exception e1) { e1.printStackTrace(); }
                
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
            
            int i = 10000;
            while ( i-- > 0 ) 
            {
                // ========== start: execute current cycle =========
                
                currentCycle++; 
                
                if ( DEBUG ) {
                    System.out.println("=== current cycle: "+currentCycle+" ===");
                }
                
                if ( items.isEmpty() ) {
                    scheduleZeroDuration( FETCH_AND_DECODE );
                }
                
                items.executeAllItemsForCurrentCycle( this , currentCycle );
                
             // ========== end: execute current cycle =========
                
                if ( USE_DELAY_LOOP ) {
                    for ( int j = delay ; j > 0 ; j-- ) {
                        dummy = ((dummy*2+j*2)/3)/(dummy*7+j);                      
                    }
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
    
    public void scheduleZeroDuration(MicroOp action) 
    {
        if ( nextAvailableCycle < currentCycle ) {
            nextAvailableCycle = currentCycle;
        }
        if ( DEBUG ) {
            System.out.println("Scheduling action: "+action+" [ duration: 0 ] on cycle "+nextAvailableCycle);
        }
        items.insertWorkItem( new WorkItem( nextAvailableCycle, action ) );
    }  
    
    public void scheduleOneDuration(MicroOp action) 
    {
        if ( nextAvailableCycle < currentCycle ) {
            nextAvailableCycle = currentCycle;
        }
        if ( DEBUG ) {
            System.out.println("Scheduling action: "+action+" [ duration: 1] on cycle "+nextAvailableCycle);
        }
        items.insertWorkItem( new WorkItem( nextAvailableCycle, action ) );
        nextAvailableCycle++;
    }    
    
    public void schedule(MicroOp action,int duration) 
    {
        if ( nextAvailableCycle < currentCycle ) {
            nextAvailableCycle = currentCycle;
        }
        if ( DEBUG ) {
            System.out.println("Scheduling action: "+action+" [ duration: "+duration+"] on cycle "+nextAvailableCycle);
        }
        items.insertWorkItem( new WorkItem( nextAvailableCycle, action ) );
        nextAvailableCycle += duration;
    }
    
    public void load(Address startingAddress , byte[] program) 
    {
        reset();
        memoryBulkLoad( startingAddress.getValue() , program );
    }
    
    public void reset() 
    {
        stop();
        items.clear();
        currentCycle = 0;
        nextAvailableCycle = 1;         
        resetCPU();
        resetMemory();
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
    
    public final MicroOp FETCH_AND_DECODE = new MicroOp("fetch_and_decode") {

        @Override
        public void execute(Simulation sim)
        {
            // fetch instruction word
            final int instruction = sim.fetchInstruction();
            
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
                                public void execute(Simulation sim)
                                {
                                    loadAccumulator( sim , (instruction >> 4) & (1+2+4+8+16+32) );
                                    storeAccumulator( sim , (instruction >> 10) & (1+2+4+8+16+32) );
                                }

                                @Override
                                public void schedule(Simulation sim)
                                {
                                    int operandBits = (instruction >> 4) & (1+2+4+8+16+32);
                                    loadAccumulator( sim , operandBits );
                                    sim.scheduleZeroDuration( this );
                                }
                             }.schedule(sim );                            
                            return;
                        default:
                            throw new RuntimeException("Cannot handle reserved opcode "+Misc.toHexString( opCode ));
                                // RESERVED OPCODE
                    }
                case 1: // 0x1: SET a, b - sets a to b
                    new MicroOp("SET") {
                        @Override
                        public void execute(Simulation sim)
                        {
                            storeAccumulator( sim , (instruction >> 4) & (1+2+4+8+16+32) );
                        }

                        @Override
                        public void schedule(Simulation sim)
                        {
                            int operandBits = (instruction >> 10) & (1+2+4+8+16+32);
                            loadAccumulator( sim , operandBits );
                            sim.scheduleOneDuration( this );
                        }
                     }.schedule(sim );
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
            sim.scheduleZeroDuration(this);
        }
        
    };

    public void setStopQuietly(boolean yesNo)
    {
        this.stopQuietly = yesNo;
    }
    
    public void resetCPU() 
    {
        for ( int i = 0 ; i < registers.length ;i++) {
            registers[i]=0;
        }
        pc=sp=o=0;
    }
    
    public int fetchInstruction() 
    {
        final int instruction = memory[ pc++ ];
        if ( DEBUG ) {
            System.out.println( "next instruction => "+Misc.toHexString( pc-1 )+": "+Misc.toHexString( instruction ) );
        }
        return instruction;
    }       
    
    
    public int readMem(int address) 
    {
        final int result = memory[address];
        System.out.println("MEM: Read value 0x"+Misc.toHexString( result)+" from 0x"+Misc.toHexString( address ));
        return result;
    }
    
    public void writeMem(int address,int value) 
    {
        System.out.println("MEM: Writing value 0x"+Misc.toHexString( value )+" to 0x"+Misc.toHexString( address ));
        memory[address]= value;
    }
    
    public void memoryBulkLoad(int startingOffset, byte[] data) {
        
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
//      System.out.println("BULK-LOAD: \n\n"+Misc.toHexDumpWithAddresses( startingOffset , data , 8 ) );
    }
    
    public void resetMemory() 
    {
        for ( int i = 0 ; i < memory.length ; i++ ) {
            memory[i]=0;
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
}