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
import java.util.List;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.InvokeMacroNode;
import de.codesourcery.jasm16.compiler.phases.ExpandMacrosPhase;
import de.codesourcery.jasm16.parser.Parser;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.FormattingVisitor;

public class ExpandMacrosPhaseTest extends TestHelper 
{
    public void testExpandMacroWithNoArguments() throws IOException 
    {
    	final String macroBody = "ADD a,1\n"+
                              "ADD b,2";
    	
        final String source =".macro brk\n"+
        					   macroBody+"\n"+
                              ".endmacro\n"+
                              "brk";
        
        ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        ICompilationContext compContext = createCompilationContext( unit ); 
        
        AST ast = new Parser(this).parse( compContext );
        unit.setAST( ast );
        if ( ast.hasErrors() ) {
        	for ( IMarker m : unit.getErrors() ) {
        		System.out.println("ERROR: "+m);
        	}
        }
        assertFalse( "AST has errors?" , unit.getAST().hasErrors() );
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        System.out.println("=== Before expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );
        
        new ExpandMacrosPhase().execute( Collections.singletonList( unit ) , debugInfo,symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS, new MyResolver(unit)  );

        assertFalse( ast.hasErrors() );     
        assertFalse( unit.hasErrors() );
        
        System.out.println("=== After expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );        
        
        List<InvokeMacroNode> startNode = ASTUtils.getNodesByType( ast , InvokeMacroNode.class , true );
        
        String expandedBody = ExpandMacrosPhase.expandInvocation( startNode.get(0) , unit );
        // System.out.println("\n\nExpanded body:\n>"+expandedBody+"<");
        assertEquals( macroBody , expandedBody );
        System.out.println("\n\n============\nAST\n=========\n");
        ASTUtils.debugPrintTextRegions(ast, source , unit );
    }	  
    
    public void testExpandMacroWithOneArgument() throws IOException 
    {
        final String source =".macro inc(value)\n"+
                             "  ADD a,value\n"+
                              ".endmacro\n"+
                             "inc(10)";
        
        ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        ICompilationContext compContext = createCompilationContext( unit ); 
        
        AST ast = new Parser(this).parse( compContext );
        unit.setAST( ast );
        if ( ast.hasErrors() ) {
        	for ( IMarker m : unit.getErrors() ) {
        		System.out.println("ERROR: "+m);
        	}
        }
        assertFalse( "AST has errors?" , unit.getAST().hasErrors() );
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        System.out.println("=== Before expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );
        
        new ExpandMacrosPhase().execute( Collections.singletonList( unit ) , debugInfo,symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS, new MyResolver(unit)  );

        assertFalse( ast.hasErrors() );     
        assertFalse( unit.hasErrors() );
        System.out.println("=== After expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );        
    } 
    
    public void testExpandMacroWithTwoArguments() throws IOException 
    {
        final String source =".macro inc(value1,value2)\n"+
                             "  ADD value1,value2\n"+
                              ".endmacro\n"+
                             "inc(A,2)";
        
        ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        ICompilationContext compContext = createCompilationContext( unit ); 
        
        AST ast = new Parser(this).parse( compContext );
        unit.setAST( ast );
        if ( ast.hasErrors() ) {
        	for ( IMarker m : unit.getErrors() ) {
        		System.out.println("ERROR: "+m);
        	}
        }
        assertFalse( "AST has errors?" , unit.getAST().hasErrors() );
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        System.out.println("=== Before expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );
        
        new ExpandMacrosPhase().execute( Collections.singletonList( unit ) , debugInfo,symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS, new MyResolver(unit)  );

        assertFalse( ast.hasErrors() );        
        assertFalse( unit.hasErrors() );
        System.out.println("=== After expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );        
    }
    
    public void testExpandMultipleMacros() throws IOException 
    {
        final String source="\n" + 
        		"\n" + 
        		".equ vram 0x8000\n" + 
        		".equ graphics_enable 0x9040\n" + 
        		"\n" + 
        		".macro rts \n" + 
        		"  SET PC, POP\n" + 
        		".endmacro\n" + 
        		"\n" + 
        		".macro neg(what)\n" + 
        		"  xor what , 0xFFFF\n" + 
        		"  add what , 1\n" + 
        		".endmacro\n" + 
        		"\n" + 
        		".macro negd(lo,hi)\n" + 
        		"  xor lo , 0xFFFF\n" + 
        		"  xor hi , 0xFFFF\n" + 
        		"  add lo , 1\n" + 
        		"  add hi , ex\n" + 
        		".endmacro\n" + 
        		"\n" + 
        		".macro shld(lo,hi,amount)\n" + 
        		"  shl hi , amount\n" + 
        		"  shl lo , amount\n" + 
        		"  bor hi , ex\n" + 
        		".endmacro\n" + 
        		"\n" + 
        		".macro shrd(lo,hi,amount)\n" + 
        		"  shr lo , amount\n" + 
        		"  and lo , 0x7FFF\n" + 
        		"  shr hi , amount\n" + 
        		"  and hi , 0x7FFF\n" + 
        		"  bor lo , ex\n" + 
        		".endmacro\n" + 
        		"\n" + 
        		".macro addd(lo,hi,rhslo,rhshi)\n" + 
        		"  add hi , rhshi\n" + 
        		"  add lo , rhslo\n" + 
        		"  add hi , ex\n" + 
        		".endmacro\n" + 
        		"\n" + 
        		".macro subd(lo,hi,rhslo,rhshi)\n" + 
        		"  sub hi , rhshi\n" + 
        		"  sub lo , rhslo\n" + 
        		"  add hi , ex\n" + 
        		".endmacro\n" + 
        		"\n" + 
        		".macro rol (var,n)\n" + 
        		"  shr var , n\n" + 
        		"  bor var , ex\n" + 
        		".endmacro\n" + 
        		"\n\n"+
        		"  rol(A,5)";         
        
        ICompilationUnit unit = CompilationUnit.createInstance("dummy",source);
        ICompilationContext compContext = createCompilationContext( unit ); 
        
        AST ast = new Parser(this).parse( compContext );
        unit.setAST( ast );
        if ( ast.hasErrors() ) {
        	for ( IMarker m : unit.getErrors() ) {
        		System.out.println("ERROR: "+m);
        	}
        }
        assertFalse( "AST has errors?" , unit.getAST().hasErrors() );
        assertFalse( unit.hasErrors() );
        assertNotNull( unit.getAST() );
        
        System.out.println("=== Before expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );
        
        new ExpandMacrosPhase().execute( Collections.singletonList( unit ) , debugInfo,symbolTable , NOP_WRITER , new CompilationListener(), RESOURCE_RESOLVER, OPTIONS, new MyResolver(unit)  );

        assertFalse( ast.hasErrors() );     
        assertFalse( unit.hasErrors() );
        System.out.println("=== After expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );        
    }    
    

}