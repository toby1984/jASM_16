package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.Address;

public interface ICPU
{
    public static final int COMMON_REGISTER_COUNT=8;
    public static final String[] COMMON_REGISTER_NAMES = {"A","B","C","X","Y","Z","I","J"};
    
    public static final int REG_A=0;
    public static final int REG_B=1;
    public static final int REG_C=2;
    public static final int REG_X=3;
    public static final int REG_Y=4;
    public static final int REG_Z=5;
    public static final int REG_I=6;
    public static final int REG_J=7;
    
    /**
     * 
     * @return
     */
    public int[] getCommonRegisters();
    
    public Address getPC();
    
    public Address getSP();
    
    public int getEX();
    
    public int getInterruptAddress();
    
    public int getCurrentCycleCount();
    
}
