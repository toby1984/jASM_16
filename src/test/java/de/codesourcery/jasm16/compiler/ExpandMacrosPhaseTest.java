package de.codesourcery.jasm16.compiler;

import java.io.IOException;
import java.util.Collections;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.compiler.phases.ExpandMacrosPhase;
import de.codesourcery.jasm16.parser.Parser;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.FormattingVisitor;

public class ExpandMacrosPhaseTest extends TestHelper 
{
    public void testExpandMacro() throws IOException 
    {
        final String source =".macro brk\n"+
                             "  ADD a,0\n"+
                              ".endmacro\n"+
                             "brk";
        
        // 4 + 15 + a + 19 = 4+15+19+A = 19+19+a = 38
        
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