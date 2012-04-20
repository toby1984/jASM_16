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
package de.codesourcery.jasm16.compiler;

import java.io.IOException;
import java.util.Collections;

import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.Compiler;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompiler;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.DebugCompilationListener;
import de.codesourcery.jasm16.utils.FormattingVisitor;
import de.codesourcery.jasm16.utils.Misc;

public class CompilerTest extends TestHelper 
{

	public void testCompileOneUnit() {

	    /*
                                        SET A,0x30          ; 7c01 0030 
                                        SET [0x1000],0x20   ; 7de1 1000 0020 
                                        SUB A,[0x1000]      ; 7803 1000 
                                        IFN A,0x10          ; c00d 
                                        SET PC,crash        ; 7dc1 0032 
                                        SET I,10            ; a861 
                                        SET A,0x2000        ; 7c01 2000 
:loop ($1a )                            SET [0x2000+I],[A]  ; 2161 2000 
                                        SUB I,1             ; 8463 
                                        IFN I,0             ; 806d 
                                        SET PC,loop         ; e9c1 
                                        SET X,0x4           ; 9031 
                                        JSR testsub         ; 7c10 002e 
                                        SET PC,crash        ; 7dc1 0032 
:testsub ($2e )                         SHL X,4             ; 9037 
                                        SET PC,POP          ; 61c1 

:crash ($32 )                           SET PC,crash        ; 7dc1 0032 
	     */
		String source = " :start       ; Try some basic stuff\n" + 
		"                      SET A, 0x30              ; 7c01 0030\n" + // 4 bytes 
		"                      SET [0x1000], 0x20       ; 7de1 1000 0020\n" + // 6 bytes 
		"                      SUB A, [0x1000]          ; 7803 1000\n" +  // 4 bytes
		"                      IFN A, 0x10              ; c00d \n" +  // 2 bytes
		"                         SET PC, crash         ; 7dc1 001a [*]\n" + // 4 bytes
		"                      \n" + 
		"        ; Do a loopy thing\n" + 
		"                      SET I, 10                ; a861\n" + // 2 bytes
		"                      SET A, 0x2000            ; 7c01 2000\n" + // 4 bytes
		"        :loop         SET [0x2000+I], [A]      ; 2161 2000\n" + // 4 bytes
		"                      SUB I, 1                 ; 8463\n" +  // 2 bytes
		"                      IFN I, 0                 ; 806d\n" + // 2 bytes
		"                         SET PC, loop          ; 7dc1 000d [*]\n" + // 4 bytes
		"        \n" + 
		"        ; Call a subroutine\n" + 
		"                      SET X, 0x4               ; 9031\n" + // 2 bytes
		"                      JSR testsub              ; 7c10 0018 [*]\n" + // 4 bytes 
		"                      SET PC, crash            ; 7dc1 001a [*]\n" + // 4 bytes
		"        \n" + 
		"        :testsub      SHL X, 4                 ; 9037\n" + // 2 bytes
		"                      SET PC, POP              ; 61c1\n" + // 2 bytes
		"                        \n" + 
		"        ; Hang forever. X should now be 0x40 if everything went right.\n" + 
		"        :crash        SET PC, crash            ; 7dc1 001a [*]\n" + // 4 bytes
		"        \n" + 
		"        ; [*]: Note that these can be one word shorter and one cycle faster by using the short form (0x00-0x1f) of literals,\n" + 
		"        ;      but my assembler doesn't support short form labels yet.\n  "+
		"        :text              .dat \"Hello world\"";
		
		// compile source
		
		final Compiler compiler = new Compiler();
		attachDebuggingPhase( compiler );
		compiler.setObjectCodeWriterFactory( NOP_WRITER );
		
		final ICompilationUnit unit = CompilationUnit.createInstance("string input" , source );
		
		compiler.compile( Collections.singletonList( unit ) , new DebugCompilationListener(true) );
		
		Misc.printCompilationErrors( unit , source , false );
		
		assertFalse( unit.hasErrors() );
		assertNotNull( unit.getAST() );
		assertFalse( unit.getAST().hasErrors() );
	}
	
    private void attachDebuggingPhase(ICompiler compiler) {
        compiler.insertCompilerPhaseAfter( new CompilerPhase("format-code") 
        {
            @Override
            protected void run(ICompilationUnit unit, ICompilationContext context) throws IOException 
            {
                if ( unit.getAST() != null ) 
                {
                    ASTUtils.visitInOrder( unit.getAST() , new FormattingVisitor(context) );
                }
            };
            
        },ICompilerPhase.PHASE_GENERATE_CODE );        
    }	
}
