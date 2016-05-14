(C) 2012-2013 Tobias Gierke / licensed under Apache License 2.0, http://www.apache.org/licenses/LICENSE-2.0


Embeddable DCPU-16 assembler written in Java
--------------------------------------------

Everyone and his dog is writing assemblers for the upcoming game 0x10c game by Mojang, so I thought I'd give it a shot too...

![screenshot](https://github.com/toby1984/jASM_16/blob/master/screenshot.png?raw=true)
Features
--------

- Fully supports DCPU-spec 1.7

- this assembler is intended to be embedded in an editor/IDE so it contains all the necessary hooks along with support for navigating from source code to AST nodes and vice versa
  
- Expression support

  Currently supported operators are () + - * / % << >> 

```
                      SET A , (((0xff+0b1011)*2+label1+label2)) << 4
                      SET B , [ 1+2+label1+A+label2] 
label1:
label2:
```

- Generates short-form opcodes for both literal values and labels (multi-pass assembler)

- Support for local labels (currently disabled by default, pass '--local-labels' to compiler Main) 

- Constants supported ( .equ <identifier> <expression> or #define <identifier> <expression>)

- Supports hexadecimal (0xdeadbeef) , binary (b101111) and decimal number literals

- Supports setting up uninitialized memory of a specific size using '.bss <size in bytes>' or "reserve <size in bytes>" keywords

- Supports setting up initialized memory with byte / word size (using '.byte' or 'dat')

- Supports 16-bit character literals

- Supports source includes via '.include "somesource.dasm16" ' or ' .incsource "somesource.dasm16" '

- Supports including binary files via '.incbin "pic.jpg"

Building 
--------

Requirements:

- Java >= 1.7
- Maven 2.2.1

Simply running

```
mvn install
```

will create a self-executable JAR (jasm16.jar) inside the /target folder.

Do

```
java -jar jasm16.jar <compiler options>
```

to actually run the compiler.

Running the compiler from the command-line
------------------------------------------

```
Usage: [options] [-o <output file>] source1 source2 ...

-o                          => output file to write generated assembly code to, otherwise code will be written to source.dcpu16
-d or --debug               => print debug output
--print                     => print formatted source code along with hex dump of generated assembly
--print-symbols             => print symbol table
--local-labels              => treat identifiers starting with a dot ('.') as local labels
--disable-literal-inlining  => disable inlining of literals -1 ... 30
--dump                      => instead of writing generated object code to a file, write a hexdump to std out
--relaxed-parsing           => relaxed parsing (instructions are parsed case-insensitive)
--relaxed-validation        => out-of-range values only cause a warning)
-v or --verbose             => print more verbose output during compilation
```

Assembler syntax reference
--------------------------

- Labels are CASE-SENSITIVE

- Instructions need to be written in upper-case (unless you compile with '--relaxed-parsing') 

- supported label styles are:

.label
:label
label:
.localLabel
:.localLabel

Note that support for local labels needs to be enabled explicitly.

- Supported number literals:

```
0xdeadbeef
1234566
0b10110111
```

- Indirect addressing uses angle brackets 

```
  SET [0x2000] , 1
  SET [0x2000+A],1
```
 
- Offsets, addresses and immediate values may be calculated using more or less arbitrary expressions

- Supported expression operators are + - * / % << >> (with operator precedence according to C standard)
    
- character literals need to be enclosed in either single or double quotes (")

```
  .dat "Hello world"
```

Characters inside string literals may be escaped using a backslash.

- to define custom constants, use

```
.equ IDENTIFIER 1+2*3+37 << 3 ; arbitary expressions that may involve labels and other constants

- to create memory initialized with byte-sized values, use

```
  .byte 0x01,0x02, 1+2 , 3*label+0x27 , IDENTIFIER 
```

- to create memory initialized with word-sized values, use either

```
  .word 0xdead,0xbeef
  .dat 0xdead,0xbeef
  dat 0xdead,0xbeef
```

- to create uninitialized memory of a specific size, use

```
  .bss 1024 ; 1k of memory initialized to 0
  reserve 1024 ; 1k of memory initialized to 0
```

- to include binary data from a file, use '.incbin "filename"'

- to include another source file, use '.include "filename"' or 'include "filename"'
