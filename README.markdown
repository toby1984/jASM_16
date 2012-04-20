(C) 2012 tobias.gierke@code-sourcery.de, 
licensed under Apache License 2.0, http://www.apache.org/licenses/LICENSE-2.0


Embeddable DCPU-16 assembler written in Java
--------------------------------------------

Everyone and his dog is writing assemblers for the upcoming game 0x10c game by Mojang, so I thought I'd give it a shot too...

Features
--------

- this assembler is intended to be embedded in an editor/IDE so it contains all the necessary hooks along with support for navigating from source code to AST nodes and vice versa
  
  Run de.codesourcery.dcpu16.utils.DEditor for a simple proof-of-concept.

- Full-featured expression support

  Supported operators are + - * / % << >> 

```
                      SET A , (((0xff+0b1011)*2+label1+label2)) << 4
                      SET B , [ 1+2+label1+A+label2] 
label1:
label2:
```

- Uses two-pass compilation for operand inlining ( operand <= 0x1f ) 

- Supports setting up uninitialized memory of a specific size using '.bss <size in bytes>'

- Supports setting up initialized memory with byte or word size (using '.byte' , '.word' or 'dat')

- Supports 16-bit character literals

Building 
--------

Requirements:

- Java >= 1.7
- Maven 2.2.1

Simply running

```
mvn install
```

will create a self-executable JAR (dasm.jar) in /target

Running the compiler from the command-line
------------------------------------------

```
Usage: [options] [-o <output file>] source1 source2 ...

-d or --debug   => print debug output
--print         => print formatted source code along with hex dump of generated assembly
--print-symbols => print symbol table
-v or --verbose => print slightly more verbose output during compilation
-h or --help    => prints this help
```

Running the demo editor from the command line
---------------------------------------------

```
java -classpath target/dasm.jar de.codesourcery.dcpu16.utils.DEditor
```

Assembler syntax reference
--------------------------

- everything except label identifiers is treated case-insensitive

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
    
- character literals need to be enclosed in double quotes (")

```
  .dat "Hello world"
```

- to create memory initialized with byte-sized values, use

```
  .byte 0x01,0x02
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
```
