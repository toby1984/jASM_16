package de.codesourcery.jasm16.emulator;

public final class WorkItem 
{
    public WorkItem next;
    public WorkItem prev;
    
    public final MicroOp action;
    public final int executionTime;
    
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