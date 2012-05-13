package de.codesourcery.jasm16.emulator;

public class SoftwareInterrupt implements IInterrupt {

	private final int message;
	
	public SoftwareInterrupt(int message) {
		this.message = message & 0xffff;
	}
	
	@Override
	public int getMessage() {
		return message;
	}

	@Override
	public boolean isSoftwareInterrupt() {
		return true;
	}

	@Override
	public boolean isHardwareInterrupt() {
		return false;
	}
}
