package de.codesourcery.jasm16.emulator;

public interface IInterrupt {

	public int getMessage();
	
	public boolean isSoftwareInterrupt();
	
	public boolean isHardwareInterrupt();
}
