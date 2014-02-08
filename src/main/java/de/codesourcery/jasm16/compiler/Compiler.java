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

import de.codesourcery.jasm16.compiler.io.DefaultResourceMatcher;
import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.compiler.phases.ASTValidationPhase1;
import de.codesourcery.jasm16.compiler.phases.ASTValidationPhase2;
import de.codesourcery.jasm16.compiler.phases.CalculateAddressesPhase;
import de.codesourcery.jasm16.compiler.phases.CodeGenerationPhase;
import de.codesourcery.jasm16.compiler.phases.ExpandMacrosPhase;
import de.codesourcery.jasm16.compiler.phases.ParseSourcePhase;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.exceptions.UnknownCompilationOrderException;

/**
 * Default compiler (assembler) implementation.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Compiler implements ICompiler {

	/**
	 * Returned by {@link #getVersionNumber()} if reading
	 * the version number failed.
	 */
	public static final String NO_VERSION_NUMBER = "<no version number>";

	public static final String VERSION ="jASM_16 V"+getVersionNumber();

	private static final Logger LOG = Logger.getLogger(Compiler.class);

	private final List<ICompilerPhase> compilerPhases = new ArrayList<ICompilerPhase>();
	private IObjectCodeWriterFactory writerFactory;
	private IResourceResolver resourceResolver = new FileResourceResolver() 
	{
		protected de.codesourcery.jasm16.compiler.io.IResource.ResourceType determineResourceType(java.io.File file) {
			return ResourceType.SOURCE_CODE;
		}
	};
	
	private final Set<CompilerOption> options = new HashSet<CompilerOption>(); 
	private ICompilationOrderProvider linkOrderProvider;

	/**
	 * Reads the compiler's version number from the Maven2 pom.properties 
	 * file in the classpath.
	 * 
	 * @return version number or {@link #NO_VERSION_NUMBER}.
	 */
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
						return version;
					}
				}
			} finally {
				IOUtils.closeQuietly( in );
			}
		} catch(Exception e) {
		}
		return NO_VERSION_NUMBER;    	
	}

	public Compiler() 
	{
		compilerPhases.addAll( setupCompilerPhases() );
	}

	@Override
	public void compile(final List<ICompilationUnit> units) 
	{
		compile( units , new CompilationListener() );
	}

	@Override
	public DebugInfo compile(final List<ICompilationUnit> unitsToCompile, ICompilationListener listener) 
	{
		return compile( unitsToCompile , new ArrayList<ICompilationUnit>() , new ParentSymbolTable("generated in compile()") , new CompilationListener() , DefaultResourceMatcher.INSTANCE );
	}

	@Override
	public DebugInfo compile(final List<ICompilationUnit> unitsToCompile,
			IParentSymbolTable parentSymbolTable , 
			ICompilationListener listener,
			IResourceMatcher resourceMatcher) 
	{
		return compile(unitsToCompile,new ArrayList<ICompilationUnit>() , parentSymbolTable , listener , resourceMatcher );
	}

	@Override
	public DebugInfo compile(final List<ICompilationUnit> unitsToCompile,
			final List<ICompilationUnit> dependencies,
			IParentSymbolTable parentSymbolTable , 
			ICompilationListener listener,
			IResourceMatcher resourceMatcher) 
	{
	    if ( hasCompilerOption(CompilerOption.DEBUG_MODE ) ) 
	    {
    		// new Exception("------------- compiling ---------------").printStackTrace();
    		
    		System.out.println("----------------------------------");
    		System.out.println("----------- COMPILING ------------");
    		System.out.println("----------------------------------");
    		
    		System.out.println( StringUtils.join( unitsToCompile , "\n" ) );
    		
    		System.out.println("-------------------------------------");
    		System.out.println("----------- DEPENDENCIES ------------");
    		System.out.println("-------------------------------------");
    		
    		System.out.println( StringUtils.join( dependencies, "\n" ) );
	    }

		// sanity check for duplicate compilation units
		// or compilation units that are in both unitsToCompile
		// and otherUnits
		for ( ICompilationUnit u1 : unitsToCompile ) 
		{
			for ( ICompilationUnit u2 : dependencies) 
			{
				if ( u1 == u2 || resourceMatcher.isSame( u1.getResource(), u2.getResource() ) ) {
					throw new IllegalArgumentException("ICompilationUnit for "+u1.getResource()+" must not be present in both lists, unitsToCompile and otherUnits");
				}
			}
		}

		for ( int i = 0 ; i < unitsToCompile.size() ; i++ ) 
		{
			final ICompilationUnit unit1 = unitsToCompile.get(i);
			for ( int j = 0 ; j < unitsToCompile.size() ; j++ )
			{
				final ICompilationUnit unit2 = unitsToCompile.get(j);
				if ( j != i && ( unit1 == unit2 || resourceMatcher.isSame( unit1.getResource() , unit2.getResource() ) ) ) {
					throw new IllegalArgumentException("Duplicate compilation unit "+unit1+" in unitsToCompile");
				}
			}
		}

		for ( int i = 0 ; i < dependencies.size() ; i++ ) 
		{
			final ICompilationUnit unit1 = dependencies.get(i);
			for ( int j = 0 ; j < dependencies.size() ; j++ )
			{
				final ICompilationUnit unit2 = dependencies.get(j);
				if ( j != i && ( unit1 == unit2 || resourceMatcher.isSame( unit1.getResource() , unit2.getResource() ) ) ) {
					throw new IllegalArgumentException("Duplicate compilation unit "+unit1+" in otherUnits");
				}
			}
		}        

		// determine compilation order
		final ICompilationOrderProvider orderProvider;
		if ( linkOrderProvider == null ) {
			orderProvider = new ICompilationOrderProvider() {

				@Override
				public List<ICompilationUnit> determineCompilationOrder(List<ICompilationUnit> units,IResourceResolver resolver,IResourceMatcher resourceMatcher)
				{
					return units;
				}
			};
		} else {
			orderProvider = linkOrderProvider;
		}

		final List<ICompilationUnit> unitsInCompilationOrder;
		try {
			unitsInCompilationOrder = orderProvider.determineCompilationOrder( unitsToCompile , resourceResolver , resourceMatcher );
		} 
		catch (ResourceNotFoundException e1) {
			throw new UnknownCompilationOrderException( e1.getMessage(), e1);
		}
		
		final DebugInfo debugInfo = new DebugInfo();

		// notify listeners of compilation start
		final ICompilerPhase firstPhase = compilerPhases.isEmpty() ? null : compilerPhases.get(0);
		listener.onCompileStart( firstPhase );
		ICompilerPhase lastPhase = firstPhase;
		try 
		{
			// discard any symbols that may have been defined in a previous compilation run
			final IParentSymbolTable globalSymbolTable;
			if ( parentSymbolTable == null ) {
				globalSymbolTable = new ParentSymbolTable("compiler-generated");        
			} else {
				globalSymbolTable = parentSymbolTable;
			}
			globalSymbolTable.clear();

			for ( ICompilationUnit unit : unitsToCompile ) 
			{
				unit.beforeCompilationStart(); // clears symbol table as well
				// globalSymbolTable.clear( unit );
				unit.getSymbolTable().setParent( globalSymbolTable );        	
			}

			// make sure to pass-in a COPY of the input list into
			// ICompilerPhase#execute() ... this method argument is actually MODIFIED by 
			// the compilation phases.
			// Whenever a - previously unseen - include is being processed
			// , a new ICompilationUnit may be added to this copy
			final List<ICompilationUnit> unitsToProcess = new ArrayList<ICompilationUnit>(unitsInCompilationOrder);
			
			final ICompilationUnitResolver compUnitResolver = createCompilationUnitResolver( unitsToProcess , dependencies , parentSymbolTable );

			for ( ICompilerPhase phase : compilerPhases ) 
			{
				lastPhase = phase;
				listener.start( phase );

				boolean success = false;
				try {
					success = phase.execute( 
					        unitsInCompilationOrder ,
					        debugInfo,
							globalSymbolTable ,  
							writerFactory , 
							listener, 
							resourceResolver, 
							options, 
							compUnitResolver );
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
					return debugInfo;
				}
			}
		} 
		finally {
			listener.afterCompile( lastPhase );
		}
		return debugInfo;
	}

	protected ICompilationUnitResolver createCompilationUnitResolver(
	        final List<ICompilationUnit> unitsToCompile,
	        final List<ICompilationUnit> otherUnits,
	        final IParentSymbolTable parentSymbolTable) 
	{
		final ICompilationUnitResolver unitResolver = new ICompilationUnitResolver() 
		{
			@Override
			public ICompilationUnit getOrCreateCompilationUnit(IResource resource) throws IOException 
			{
				ICompilationUnit result = getCompilationUnit( resource );
				if ( result != null ) {
					return result;
				}

				result = CompilationUnit.createInstance( resource.getIdentifier() , resource );

				result.getSymbolTable().setParent( parentSymbolTable );
				
				System.out.println("Creating new ICompilationUnit - did not find "+resource+" in "+unitsToCompile+" NOR "+otherUnits);

				// !!!! the next call actually modifies the method's input argument....
				unitsToCompile.add( result );
				return result;
			}

			@Override
			public ICompilationUnit getCompilationUnit(IResource resource) throws IOException 
			{
				ICompilationUnit result = findCompilationUnit( resource , unitsToCompile );
				if ( result == null ) {
					result = findCompilationUnit( resource , otherUnits );
				}
				return result;
			}

			private ICompilationUnit findCompilationUnit(IResource resource,List<ICompilationUnit> units) 
			{
				for ( ICompilationUnit unit : units ) 
				{
					if ( unit.getResource().getIdentifier().equals( resource.getIdentifier() ) ) {
						return unit;
					}
				}
				return null;
			}
		};
		return unitResolver;
	}

	protected List<ICompilerPhase> setupCompilerPhases()
	{
		final List<ICompilerPhase> phases = new ArrayList<ICompilerPhase>();

		// parse sources
		phases.add( new ParseSourcePhase() );

		// expand macros
		phases.add( new ExpandMacrosPhase() );
		
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
	public ICompiler setCompilerOption(CompilerOption option, boolean onOff) {
		if ( option == null ) {
			throw new IllegalArgumentException("option must not be NULL");
		}
		if ( onOff ) {
			options.add( option );
		} else {
			options.remove( option );
		}
		return this;
	}

	@Override
	public void setCompilationOrderProvider(ICompilationOrderProvider provider)
	{
		if (provider == null) {
			throw new IllegalArgumentException("provider must not be NULL.");
		}
		this.linkOrderProvider = provider;
	}

}
