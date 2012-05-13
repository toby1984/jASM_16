package de.codesourcery.jasm16.emulator;

public final class HardwareInterrupt implements IInterrupt {

	private final IDevice device;
	private final int message;
	
	public HardwareInterrupt(IDevice device,int message) {
		if (device == null) {
			throw new IllegalArgumentException("device must not be null");
		}
		this.device = device;
		this.message = message & 0xffff;
	}
	
	public IDevice getDevice() {
		return device;
	}
	
	@Override
	public int getMessage() {
		return message;
	}

	@Override
	public boolean isSoftwareInterrupt() {
		return false;
	}

	@Override
	public boolean isHardwareInterrupt() {
		return true;
	}

}
