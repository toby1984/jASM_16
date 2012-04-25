package de.codesourcery.jasm16.emulator;

public final class LoadCommonRegisterAction extends MicroOp {

    private final int registerIndex;
    
    public LoadCommonRegisterAction(int registerIndex) 
    {
        super("acc:= register #"+registerIndex);
        this.registerIndex= registerIndex;
    }
    
    @Override
    public void execute(Simulation sim)
    {
        sim.accumulator = sim.registers[registerIndex];
    }

    @Override
    public void schedule(Simulation sim)
    {
        sim.scheduleZeroDuration( this );
    }
}