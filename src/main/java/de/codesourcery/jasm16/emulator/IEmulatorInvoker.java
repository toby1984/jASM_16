package de.codesourcery.jasm16.emulator;

import de.codesourcery.jasm16.emulator.memory.IMemory;

public interface IEmulatorInvoker<T> {

	public T doWithEmulator(IEmulator emulator, ICPU cpu,IMemory memory);
}
