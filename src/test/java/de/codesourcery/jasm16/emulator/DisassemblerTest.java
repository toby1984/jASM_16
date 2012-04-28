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
package de.codesourcery.jasm16.emulator;

import java.util.List;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.disassembler.DisassembledLine;
import de.codesourcery.jasm16.disassembler.Disassembler;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.Misc;

public class DisassemblerTest extends TestHelper
{

    public void testDisassemble() throws Exception 
    {
        final String source = ":label SET a,1\n"+
                "       ADD b ,1\n"+
                "       ADD [stuff],1\n"+
                "       SET c , [stuff]\n"+
                "       SET PC,label\n"+
                ":stuff .dat 0x1234";
        
        final byte[] data = compileToByteCode( source );
        
//        System.out.println("\n\nCOMPILED:\n\n"+Misc.toHexDumpWithAddresses( 0 , data , 1 ) );
        final Disassembler dis = new Disassembler();
        final List<DisassembledLine> lines = dis.disassemble( Address.ZERO , data , 7 , false );
        final String[] expected = new String[]{
            "0000: SET A , 0x0001", 
            "0001: ADD B , 0x0001", 
            "0002: ADD [ 0x0007 ] , 0x0001", 
            "0004: SET C , [ 0x0007 ]", 
            "0006: SET PC , 0x0000", 
            "0007: IFG [B + 0x0000] , Y", 
            "0009: < unknown opcode: 0000000000000000 >"
        };
        int i = 0;
        for ( DisassembledLine actual : lines ) {
            final String withAddress = Misc.toHexString( actual.getAddress().getValue() )+": "+actual.getContents();
            assertEquals( expected[i++] ,withAddress );
        }
    }
}
