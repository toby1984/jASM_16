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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;

/**
 * Abstract base-class for compiler phase implementations.
 * 
 * @author tobias.gierke@code-sourcery.de
 * @see ICompilerPhase
 */
public abstract class CompilerPhase implements ICompilerPhase {

	private static final Logger LOG = Logger.getLogger( CompilerPhase.class );
	
    private final String name;
	private boolean stopAfterExecution;

    public CompilerPhase(String name) 
    {
        if ( StringUtils.isBlank( name ) ) {
            throw new IllegalArgumentException("name must not be NULL/blank.");
        }
        this.name = name;
    }
    
    @Override
    public String getName()
    {
        return name;
    }

    private boolean hasErrors(List<ICompilationUnit> units)
    {
        for ( ICompilationUnit unit : units ) {
            if ( unit.hasErrors() ) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isStopAfterExecution() {
        return stopAfterExecution;
    }

    protected boolean isAbortOnErrors() {
        return false;
    }

    @Override
    public String toString()
    {
        return name.toString();
    }
    
    protected boolean isProcessCompilationUnit(ICompilationUnit unit) {
        return true;
    }

    @Override
    public boolean execute(List<ICompilationUnit> units,
            DebugInfo debugInfo,
    		IParentSymbolTable globalSymbolTable, 
    		IObjectCodeWriterFactory writerFactory , 
    		ICompilationListener listener, 
    		IResourceResolver resourceResolver, 
    		Set<CompilerOption> options, 
    		ICompilationUnitResolver compUnitResolver)        
    {
    	/*
    	 * NEED to create a copy for the for() loop here since
    	 * createCompilationContext() instantiates an
    	 * ICompilationUnitResolver that MODIFIES the
    	 * input list (and thus the for() loop would otherwise
    	 * fail with a ConcurrentModificationExcption) ...
    	 */
    	final List<ICompilationUnit> internalCopy = new ArrayList<ICompilationUnit>( units );
    	
        for ( ICompilationUnit unit : internalCopy ) 
        {
            if ( ! isProcessCompilationUnit( unit ) ) {
                listener.skipped( this , unit );
                continue;
            }
            
        	listener.start( this , unit );
        	
            try 
            {
                final ICompilationContext context = createCompilationContext( units ,  globalSymbolTable, writerFactory , resourceResolver , options , compUnitResolver , unit );
                		
                run( unit , context );
                if ( hasErrors( units ) ) 
                { 
                	listener.failure( this , unit );
                	if ( isAbortOnErrors() ) {
                		return false;
                	}
                } else {
                	listener.success( this , unit );
                }
            }
            catch (Exception e) 
            {
            	listener.failure( this , unit );
                unit.addMarker( new GenericCompilationError("Unexpected error while compiling "+unit, unit,e) );
                LOG.error("execute(): [ phase "+this+"] "+e.getMessage() , e );
                return ! isAbortOnErrors();
            }                
        }
        return true;
    }

	protected final ICompilationContext createCompilationContext(
			final List<ICompilationUnit> units,
			IParentSymbolTable symbolTable, 
			IObjectCodeWriterFactory writerFactory,
			IResourceResolver resourceResolver, 
			final Set<CompilerOption> options,
			ICompilationUnitResolver compUnitResolver,
			ICompilationUnit unit) 
	{
		return new CompilationContext( unit , units ,  symbolTable, writerFactory , resourceResolver ,compUnitResolver ,options );
	}
    
    @Override
    public void setStopAfterExecution(boolean yesNo) {
    	this.stopAfterExecution = yesNo;
    }

    protected abstract void run(ICompilationUnit unit , ICompilationContext context) throws IOException;
}