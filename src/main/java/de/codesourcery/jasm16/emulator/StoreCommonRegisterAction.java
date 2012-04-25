package de.codesourcery.jasm16.emulator;

public final class StoreCommonRegisterAction extends MicroOp {

    private final int registerIndex;
    
    public StoreCommonRegisterAction(int registerIndex) {
        super("register #"+registerIndex+" := acc");            
        this.registerIndex= registerIndex;
    }
    
    @Override
    public void execute(Simulation sim)
    {
        sim.registers[registerIndex] = sim.accumulator;
    }

    @Override
    public void schedule(Simulation sim)
    {
        sim.scheduleZeroDuration( this );
    }
}   