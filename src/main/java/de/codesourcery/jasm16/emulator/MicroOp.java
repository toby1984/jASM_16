package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.emulator.Emulator.LoadOperandBRegisterIndirectWithOffsetAction;
import de.codesourcery.jasm16.emulator.Emulator.StoreOperandARegisterIndirectWithOffsetAction;

public abstract class MicroOp 
{
    public static final LoadCommonRegisterAction LOAD_A = new LoadCommonRegisterAction(0);
    public static final LoadCommonRegisterAction LOAD_B = new LoadCommonRegisterAction(1);
    public static final LoadCommonRegisterAction LOAD_C = new LoadCommonRegisterAction(2);
    public static final LoadCommonRegisterAction LOAD_X = new LoadCommonRegisterAction(3);
    public static final LoadCommonRegisterAction LOAD_Y = new LoadCommonRegisterAction(4);
    public static final LoadCommonRegisterAction LOAD_Z = new LoadCommonRegisterAction(5);    
    public static final LoadCommonRegisterAction LOAD_I = new LoadCommonRegisterAction(6);
    public static final LoadCommonRegisterAction LOAD_J = new LoadCommonRegisterAction(7);    
    
    public static final StoreCommonRegisterAction STORE_A = new StoreCommonRegisterAction(0);
    public static final StoreCommonRegisterAction STORE_B = new StoreCommonRegisterAction(1);
    public static final StoreCommonRegisterAction STORE_C = new StoreCommonRegisterAction(2);
    public static final StoreCommonRegisterAction STORE_X = new StoreCommonRegisterAction(3);
    public static final StoreCommonRegisterAction STORE_Y = new StoreCommonRegisterAction(4);
    public static final StoreCommonRegisterAction STORE_Z = new StoreCommonRegisterAction(5);
    public static final StoreCommonRegisterAction STORE_I = new StoreCommonRegisterAction(6);
    public static final StoreCommonRegisterAction STORE_J = new StoreCommonRegisterAction(7);     
    
    public static final LoadCommonRegisterAction[] LOAD_REGISTER = new LoadCommonRegisterAction[] {LOAD_A,LOAD_B,LOAD_C,LOAD_X,LOAD_Y,LOAD_Z,LOAD_I,LOAD_J};    
    public static final StoreCommonRegisterAction[] STORE_REGISTER = new StoreCommonRegisterAction[] {STORE_A,STORE_B,STORE_C,STORE_X,STORE_Y,STORE_Z,STORE_I,STORE_J};
    private final String name;
    
    public MicroOp(String name) {
        this.name = name;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    public abstract void execute(Simulation sim);
    
    public abstract void schedule(Simulation sim);
    
    protected  void loadAccumulator(final Simulation sim , int operandBits) 
    {
        switch( operandBits ) 
        {
            case 0x18: // POP / [SP++]
                new MicroOp("acc:=POP") 
                {

                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.accumulator = sim.memory[ sim.sp++ ];
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );
                return;
            case 0x19: // PEEK / [SP]
                new MicroOp("acc:=PEEK") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.accumulator = sim.memory[ sim.sp ];
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );                 
                return;
            case 0x1a: // PUSH / [--SP]
                new MicroOp("acc:=PUSH") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.accumulator = sim.memory[ --sim.sp ];
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );                 
                return;
            case 0x1b: // SP
                new MicroOp("acc:=SP") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.accumulator = sim.sp;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleZeroDuration( this );
                    }
                 }.schedule(sim );                 
                return;     
            case 0x1c: // PC
                new MicroOp("acc:=PC") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.accumulator = sim.pc;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleZeroDuration( this );
                    }
                 }.schedule(sim );                 
                return;     
            case 0x1d: // O
                new MicroOp("acc:=O") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.accumulator = sim.o;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleZeroDuration( this );
                    }
                 }.schedule(sim );                 
                return;    
            case 0x1e: // [next word]
                new MicroOp("acc:=[next word]") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        final int adr = sim.memory[ sim.pc++ ];
                        sim.accumulator = sim.memory[ adr ];
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );                 
                return;          
            case 0x1f: // next word (literal)
                new MicroOp("acc:=next word (literal)") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.accumulator = sim.memory[ sim.pc++ ];
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );                 
                return;                     
        }
        
        //  0x20-0x3f: literal value 0x00-0x1f (literal)     
        if ( operandBits >= 0x20 ) {
            sim.accumulator = operandBits - 0x20;
            return;
        }
        
        // register immediate ?
        int value = operandBits & 0x07; // bits 0-2
        if ( value <= 0x07) 
        {
            LOAD_REGISTER[ value ].schedule(sim);
            return;
        }
        
        // register indirect?
        value = (operandBits >> 3) & 0x07; // bits 3-5
        if ( value <= 0x07) 
        {
            new LoadCommonRegisterIndirectAction( value ).schedule(sim );
            return;
        }
        
        // register indirect with offset?
        value = (operandBits >> 6) & 0x07; // bits 6-8
        if ( value <= 0x07) 
        {
            new LoadOperandBRegisterIndirectWithOffsetAction( value ).schedule(sim );
            return;                
        }               
    }
    
    protected  void storeAccumulator(final Simulation sim , int operandBits) 
    {
        switch( operandBits ) 
        {
            case 0x18: // POP / [SP++]
                new MicroOp("POP := acc") 
                {

                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.memory[ sim.sp++ ] = sim.accumulator;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );
                return;
            case 0x19: // PEEK / [SP]
                new MicroOp("PEEK := acc") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.memory[ sim.sp ] = sim.accumulator;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );                 
                return;
            case 0x1a: // PUSH / [--SP]
                new MicroOp("PUSH := acc") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.memory[ --sim.sp ] = sim.accumulator;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );                 
                return;
            case 0x1b: // SP
                new MicroOp("SP := acc") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.sp = sim.accumulator;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleZeroDuration( this );
                    }
                 }.schedule(sim );                 
                return;     
            case 0x1c: // PC
                new MicroOp("pc := acc") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.pc = sim.accumulator;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleZeroDuration( this );
                    }
                 }.schedule(sim );                 
                return;     
            case 0x1d: // O
                new MicroOp("O := acc") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        // cpu.accumulator = cpu.o;
                        throw new RuntimeException("Assigning register O is not possible");
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleZeroDuration( this );
                    }
                 }.schedule(sim );                 
                return;    
            case 0x1e: // [next word]
                new MicroOp("[ next word ] := acc") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        final int adr = sim.memory[ sim.pc++ ];
                        sim.memory[ adr ] = sim.accumulator;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );                 
                return;          
            case 0x1f: // next word (literal)
                new MicroOp("next word (literal)") 
                {
                    @Override
                    public void execute(Simulation sim)
                    {
                        sim.memory[ sim.pc++ ] = sim.accumulator;
                    }

                    @Override
                    public void schedule(Simulation sim)
                    {
                        sim.scheduleOneDuration( this );
                    }
                 }.schedule(sim );                 
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
            STORE_REGISTER[ value ].schedule(sim);
            return;                
        }
        
        // register indirect?
        value = (operandBits >> 3) & 0x07; // bits 3-5
        if ( value <= 0x07) 
        {
            new LoadCommonRegisterIndirectAction( value ).schedule(sim );
            return;
        }
        
        // register indirect with offset?
        value = (operandBits >> 6) & 0x07; // bits 6-8
        if ( value <= 0x07) 
        {
            new StoreOperandARegisterIndirectWithOffsetAction( value ).schedule(sim );
            return;                
        }               
        
    }       
} // End: MicroOp