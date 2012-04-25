package de.codesourcery.jasm16.emulator;

public final class LoadCommonRegisterIndirectAction extends MicroOp {

    private final int registerIndex;
    
    public LoadCommonRegisterIndirectAction(int registerIndex) 
    {
        super("acc := [ register #"+registerIndex+" ]");              
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
        sim.scheduleOneDuration( this );
    }
}