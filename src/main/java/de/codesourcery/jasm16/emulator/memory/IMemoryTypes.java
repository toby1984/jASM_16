/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.jasm16.emulator.memory;

/**
 * Interface with constants of built-in memory types.
 * 
 * <p>I intentionally do not use an enum here since enums are 
 * not extensible and thus new device implementations could
 * not define their own memory types.</p>
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface IMemoryTypes {

	public static final long TYPE_RAM = 0x12345678;
	
	// video emulation
	public static final long TYPE_VRAM = 0x12345679;
	public static final long TYPE_FONT_RAM = 0x1234567a;
	public static final long TYPE_PALETTE_RAM = 0x1234567b;
	
	// keyboard buffer
	public static final long TYPE_KEYBOARD_BUFFER = 0x1234567c;	
	
}
