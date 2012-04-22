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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.compiler.phases.ASTValidationPhase1;
import de.codesourcery.jasm16.compiler.phases.ASTValidationPhase2;
import de.codesourcery.jasm16.compiler.phases.CalculateAddressesPhase;
import de.codesourcery.jasm16.compiler.phases.CodeGenerationPhase;
import de.codesourcery.jasm16.compiler.phases.ParseSourcePhase;

/**
 * Default compiler (assembler) implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Compiler implements ICompiler {

	public static final String VERSION ="jASM_16 "+getVersionNumber();
	
	private static final Logger LOG = Logger.getLogger(Compiler.class);

    private final List<ICompilerPhase> compilerPhases = new ArrayList<ICompilerPhase>();
    private IObjectCodeWriterFactory writerFactory;
    private IResourceResolver resourceResolver = new FileResourceResolver();
    private final Set<CompilerOption> options = new HashSet<CompilerOption>(); 
    
    public static final String getVersionNumber() {
    	
    	final String path = "META-INF/maven/de.codesourcery.dcpu16/jasm16/pom.properties";
    	try {
    		final InputStream in = Compiler.class.getClassLoader().getResourceAsStream( path );
    		try {
    			if ( in != null ) {
    				final Properties props = new Properties();
    				props.load( in);
    				final String version = props.getProperty("version");
    				if ( ! StringUtils.isBlank( version ) ) {
    					return "V"+version;
    				}
    			}
    		} finally {
    			IOUtils.closeQuietly( in );
    		}
    	} catch(Exception e) {
    	}
    	return "<unknown version>";    	
    }
    public Compiler() 
    {
    	compilerPhases.addAll( setupCompilerPhases() );
    }
    
    @Override
    public void compile(List<ICompilationUnit> units, ICompilationListener listener) 
    {
        final ICompilerPhase firstPhase = compilerPhases.isEmpty() ? null : compilerPhases.get(0);
        listener.onCompileStart( firstPhase );
        
        // make sure to pass-in a COPY of the input list into
        // ICompilerPhase#execute() ... this method argument is actually MODIFIED by 
        // the compilation phases.
        // Whenever an - previously unseen - include is being processed
        // , a new ICompilationUnit will be added to this copy
        
        final List<ICompilationUnit> copy = new ArrayList<ICompilationUnit>(units);
        
        final ISymbolTable symbolTable = createSymbolTable();
        
        ICompilerPhase lastPhase = firstPhase;
        try 
        {
        	for (ICompilationUnit unit : copy) 
        	{
    			unit.beforeCompilationStart();
    		}
        	
            for ( ICompilerPhase phase : compilerPhases ) 
            {
                lastPhase = phase;
                listener.start( phase );
                
                boolean success = false;
                try {
                    success = phase.execute( 
                    		copy ,
                    		symbolTable ,  
                    		writerFactory , 
                    		listener, 
                    		resourceResolver, 
                    		options );
                } 
                catch(Exception e) {
                	LOG.error("compile(): Internal compiler error during phase "+phase.getName(),e);
                }
                finally 
                {
                    if ( ! success ) 
                    {
                        listener.failure( phase );
                    } else {
                        listener.success( phase );
                    }
                }
                if ( ! success || phase.isStopAfterExecution() ) {
                    return;
                }
            }
        } 
        finally {
            listener.afterCompile( lastPhase );
        }
    }

    // subclassing hook
	protected ISymbolTable createSymbolTable() {
		return new SymbolTable();
	}

    protected List<ICompilerPhase> setupCompilerPhases()
    {
    	final List<ICompilerPhase> phases = new ArrayList<ICompilerPhase>();

        // parse sources
        phases.add( new ParseSourcePhase() );

        // validate existence of referenced labels
        phases.add( new ASTValidationPhase1() );

        // calculate size information and set addresses
        phases.add( new CalculateAddressesPhase() );

        // validate before object code generation
        phases.add( new ASTValidationPhase2() );
        
        // generate object code
        phases.add( new CodeGenerationPhase() );
        return phases;
    }

	@Override
	public List<ICompilerPhase> getCompilerPhases() {
		return Collections.unmodifiableList( this.compilerPhases );
	}

	@Override
	public void insertCompilerPhaseAfter(ICompilerPhase phase, String name) {
		
		if ( phase == null ) {
			throw new IllegalArgumentException("phase must not be NULL");
		}
		assertHasUniqueName( phase );		
		final int index = getCompilerPhaseIndex( name );
		if ( (index+1) >= compilerPhases.size() ) {
			compilerPhases.add( phase );
		} else {
			compilerPhases.add( index+1 , phase );
		}
	}
	
	private void assertHasUniqueName(ICompilerPhase phase) {
	    for ( ICompilerPhase p : compilerPhases) {
	        if ( p.getName().equals( phase.getName() ) ) {
	            throw new IllegalArgumentException("Duplicate compiler phase with name '"+phase.getName()+"'");
	        }
	    }
	}
	
	private int getCompilerPhaseIndex(String name) {
		
		if (StringUtils.isBlank(name)) {
			throw new IllegalArgumentException("name must not be NULL/blank");
		}
		for ( int i = 0 ; i < this.compilerPhases.size() ; i++ ) 
		{
			if ( this.compilerPhases.get(i).getName().equals( name ) ) 
			{
				return i;
			}
		}
		throw new IllegalArgumentException("Found no compiler phase '"+name+"'");
	}
	

	@Override
	public ICompilerPhase getCompilerPhaseByName(String name) {
		
		if (StringUtils.isBlank(name)) {
			throw new IllegalArgumentException("name must not be NULL/blank");
		}
		for ( ICompilerPhase p : compilerPhases ) {
			if ( p.getName().equals( name ) ) 
			{
				return p;
			}
		}
		throw new IllegalArgumentException("Found no compiler phase '"+name+"'");
	}
	
	@Override
	public void replaceCompilerPhase(ICompilerPhase phase, String name) 
	{
		if ( phase == null ) {
			throw new IllegalArgumentException("phase must not be NULL");
		}
		
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be NULL/blank.");
        }
        
		if ( ! name.equals( phase.getName() ) ) {
		    assertHasUniqueName( phase );
		}
		compilerPhases.set( getCompilerPhaseIndex( name ) , phase );
	}
	
	@Override
	public void removeCompilerPhase(String name) {
	    
	    for (Iterator<ICompilerPhase> it = compilerPhases.iterator(); it.hasNext();) {
            final ICompilerPhase type = it.next();
            if ( type.getName().equals( name ) ) {
                it.remove();
                return;
            }
        }
	    throw new NoSuchElementException("Failed to remove phase '"+name+"'");
	}
	
	@Override
	public void insertCompilerPhaseBefore(ICompilerPhase phase, String name) 
	{
		if ( phase == null ) {
			throw new IllegalArgumentException("phase must not be NULL");
		}
		if ( name == null ) {
			throw new IllegalArgumentException("name must not be NULL");
		}
		assertHasUniqueName( phase );
		compilerPhases.add( getCompilerPhaseIndex( name ) , phase );
	}

    @Override
    public void setObjectCodeWriterFactory(IObjectCodeWriterFactory factory)
    {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be NULL.");
        }
        this.writerFactory = factory;
    }

    @Override
    public void setResourceResolver(IResourceResolver resolver)
    {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be NULL.");
        }
        this.resourceResolver = resolver;
    }

	@Override
	public boolean hasCompilerOption(CompilerOption option) {
		if (option == null) {
			throw new IllegalArgumentException("option must not be NULL");
		}
		return this.options.contains( option );
	}

	@Override
	public void setCompilerOption(CompilerOption option, boolean onOff) {
		if ( option == null ) {
			throw new IllegalArgumentException("option must not be NULL");
		}
		if ( onOff ) {
			options.add( option );
		} else {
			options.remove( option );
		}
	}

}
