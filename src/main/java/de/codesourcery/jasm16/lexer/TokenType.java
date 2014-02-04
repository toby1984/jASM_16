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
package de.codesourcery.jasm16.lexer;

/**
 * Enumeration of all token types recognized by the lexer.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see ILexer
 */
public enum TokenType 
{
	CHARACTERS, // unrecognized token / random character sequence 
	NUMBER_LITERAL, // decimal, hex (0x...) or binary (%...) number literal
	ANGLE_BRACKET_OPEN, // [
	ANGLE_BRACKET_CLOSE, // ]
    PARENS_OPEN, // (
    PARENS_CLOSE, // (	
    STRING_ESCAPE, // \
    STRING_DELIMITER, // " or '
	COMMA, // , 
	EOL, // DOS or UNIX-style newline
	COLON, // :
    SINGLE_LINE_COMMENT, // ;
	WHITESPACE, // 0x20 or 0x08
	INSTRUCTION, // assembler instruction
	DOT, // .
	PUSH, // 'push' ( shorthand for [--SP] )
	POP, // 'pop' ( shorthand for [SP++] )
	PEEK, // 'peek' (shorthand for [SP] )
	PICK, // 'pick' (shorthand for [SP+offset] )
	OPERATOR,
	// ============ preprocessor-style stuff ======
    INITIALIZED_MEMORY_PACK, // pack
    INITIALIZED_MEMORY_WORD, // .dat
    INITIALIZED_MEMORY_BYTE, // .byte
    UNINITIALIZED_MEMORY_WORDS, // reserve
    UNINITIALIZED_MEMORY_BYTES, // .bss
    INCLUDE_BINARY, // .incbin
    ORIGIN, // .org
    EQUATION, // .equ or #define
    START_MACRO, // .macro
    END_MACRO, // .endmacro
	INCLUDE_SOURCE; // .include
}
