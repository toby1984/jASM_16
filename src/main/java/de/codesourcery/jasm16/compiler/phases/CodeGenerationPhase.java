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
package de.codesourcery.jasm16.compiler.phases;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.IASTNodeVisitor;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.compiler.CompilerPhase;
import de.codesourcery.jasm16.compiler.DebugInfo;
import de.codesourcery.jasm16.compiler.GenericCompilationError;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnitResolver;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.ICompilerPhase;
import de.codesourcery.jasm16.compiler.IParentSymbolTable;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * Compiler phase that generates the actual object code.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class CodeGenerationPhase extends CompilerPhase {

    private static final Logger LOG = Logger.getLogger( CodeGenerationPhase.class );

    public CodeGenerationPhase() {
        super(ICompilerPhase.PHASE_GENERATE_CODE);
    }

    @Override
    public boolean execute(List<ICompilationUnit> units,
            DebugInfo debugInfo,
            IParentSymbolTable symbolTable,
            IObjectCodeWriterFactory writerFactory, ICompilationListener listener, 
            IResourceResolver resourceResolver, 
            Set<CompilerOption> options, ICompilationUnitResolver compUnitResolver)
    {
        try {
            return super.execute(units, debugInfo,symbolTable, writerFactory, listener, resourceResolver, options, compUnitResolver);
        } 
        finally 
        {
            try 
            {
                for (  ICompilationUnit unit : units ) 
                {
                    if ( ! unit.hasErrors() ) {
                        continue;
                    } 
                    try 
                    {
                        LOG.debug("execute(): Deleting generated output files because of compilation errors.");                            
                        writerFactory.deleteOutput();
                        break;
                    } catch (IOException e) {
                        LOG.error("execute(): Error while deleting output files",e);
                    }
                }
            } finally {
                try {
                    writerFactory.closeObjectWriters();
                } catch (IOException e) {
                    LOG.error("execute(): Failed to close writers",e);
                }
            }
        }
    }

    @Override
    protected void run(ICompilationUnit unit, final ICompilationContext compContext) throws IOException
    {
        final IObjectCodeWriterFactory factory = compContext.getObjectCodeWriterFactory();
        final IObjectCodeWriter writer = factory.getWriter( compContext );
        if ( writer == null ) {
            final String msg = "IObjectCodeWriterFactory "+factory.getClass()+" returned NULL writer ??";
            LOG.error("run(): "+msg);
            throw new RuntimeException( msg );
        }
        outputObjectCode(unit,compContext, writer );
    }

    protected void outputObjectCode(ICompilationUnit unit, final ICompilationContext compContext,final IObjectCodeWriter writer) throws IOException
    {
        final AST ast = compContext.getCurrentCompilationUnit().getAST();
        if ( ast != null ) 
        {
            final IASTNodeVisitor<ASTNode> visitor = new IASTNodeVisitor<ASTNode>() {

                @Override
                public void visit(ASTNode n, IIterationContext context)
                {
                    if ( n instanceof ObjectCodeOutputNode ) 
                    {
                        try 
                        {
                            ((ObjectCodeOutputNode) n).writeObjectCode( writer, compContext );
                        }
                        catch (Exception e) 
                        {
                            LOG.error("outputObjectCode(): While handling "+compContext.getCurrentCompilationUnit()+" using writer "+writer,e);
                            final ICompilationUnit unit = compContext.getCurrentCompilationUnit();
                            unit.addMarker( new GenericCompilationError("I/O error while writing object-code for "+unit,unit,e) );
                            context.stop();
                            return;
                        }
                    }
                }
            };
            ASTUtils.visitInOrder( ast , visitor );
        }
    }            

}
