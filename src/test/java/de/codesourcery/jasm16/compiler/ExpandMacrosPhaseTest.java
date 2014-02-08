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

        System.out.println("=== After expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );        
    } 
    
    public void testExpandMacroWithTwoArguments() throws IOException 
    {
        final String source =".macro inc(value1,value2)\n"+
                             "  ADD value1,value2\n"+
                              ".endmacro\n"+
                             "inc(1,2)";
        
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

        System.out.println("=== After expansion");
        ASTUtils.visitInOrder( ast , new FormattingVisitor( compContext , true ) );        
    }      
}