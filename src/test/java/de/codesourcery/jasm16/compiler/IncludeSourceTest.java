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

import java.util.Collections;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.compiler.io.NullObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.StringResource;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.Misc;

public class IncludeSourceTest extends TestHelper {

	public void testIncludeUsingPathWithSlashes() throws ParseException {
		
		final String source1 = ".include \"../source2\"";
		final String source2 = ":label";

		final Compiler c = new Compiler() 
		{
			@Override
			protected ISymbolTable createSymbolTable() {
				return symbolTable;
			}
		};
		
		final ICompilationUnit unit1 = CompilationUnit.createInstance( "source1" , source1 );
		
		c.setResourceResolver( new IResourceResolver() {

			@Override
			public IResource resolve(String identifier,ResourceType type) throws ResourceNotFoundException 
			{
				throw new UnsupportedOperationException("Unexpected call");
			}

			@Override
			public IResource resolveRelative(String identifier, IResource parent,ResourceType resourceType) throws ResourceNotFoundException 
			{
				if ( "../source2".equals( identifier ) ) 
				{
					assertSame( unit1.getResource() , parent );
					return new StringResource( identifier , source2 , resourceType );
				}
				throw new IllegalArgumentException("Unexpected call for '"+identifier+"'");
			}

		});

		c.setObjectCodeWriterFactory( new NullObjectCodeWriterFactory() );
		
		c.compile( Collections.singletonList( unit1) , new CompilationListener() );
		
		Misc.printCompilationErrors( unit1  , source1 , true );
		assertFalse( unit1.hasErrors() );
		assertEquals( 1 , unit1.getDependencies().size() );
		
		final ICompilationUnit unit2 = unit1.getDependencies().get(0);
		assertFalse( unit2.hasErrors() );		
		
		assertNotNull( symbolTable.containsSymbol( new Identifier("label" ) ) );
		final Label symbol = (Label) symbolTable.getSymbol( new Identifier("label" ) ) ;
		assertEquals( Address.wordAddress( 0 ) , symbol.getAddress() );		
	}
	
	public void testInclude() throws ParseException {
		
		final String source1 = ".include \"source2\"";
		final String source2 = ":label";

		final Compiler c = new Compiler() 
		{
			@Override
			protected ISymbolTable createSymbolTable() {
				return symbolTable;
			}
		};
		
		c.setResourceResolver( new IResourceResolver() {

			@Override
			public IResource resolve(String identifier, ResourceType resourceType) throws ResourceNotFoundException 
			{
				throw new UnsupportedOperationException("Unexpected call");
			}

			@Override
			public IResource resolveRelative(String identifier, IResource parent, ResourceType resourceType) throws ResourceNotFoundException 
			{
				if ( "source2".equals( identifier ) ) {
					return new StringResource( identifier , source2 , ResourceType.SOURCE_CODE );
				}
				throw new IllegalArgumentException("Unexpected call for '"+identifier+"'");
			}
		});

		c.setObjectCodeWriterFactory( new NullObjectCodeWriterFactory() );
		
		final ICompilationUnit unit1 = CompilationUnit.createInstance( "source1" , source1 );
		c.compile( Collections.singletonList( unit1) , new CompilationListener() );
		
		Misc.printCompilationErrors( unit1  , source1 , true );
		assertFalse( unit1.hasErrors() );
		assertEquals( 1 , unit1.getDependencies().size() );
		
		final ICompilationUnit unit2 = unit1.getDependencies().get(0);
		assertFalse( unit2.hasErrors() );		
		
		assertNotNull( symbolTable.containsSymbol( new Identifier("label" ) ) );
		final Label symbol = (Label) symbolTable.getSymbol( new Identifier("label" ) ) ;
		assertEquals( Address.wordAddress( 0 ) , symbol.getAddress() );		
	}
	
	public void testCircularInclude() throws ParseException {
		
		final String source1 = ".include \"source2\"";
		final String source2 = ".include \"source1\"";

		final Compiler c = new Compiler() 
		{
			@Override
			protected ISymbolTable createSymbolTable() {
				return symbolTable;
			}
		};
		
		c.setCompilerOption( CompilerOption.DEBUG_MODE , true );
		
		c.setResourceResolver( new IResourceResolver() {

			@Override
			public IResource resolve(String identifier, ResourceType resourceType) throws ResourceNotFoundException 
			{
				throw new UnsupportedOperationException("Unexpected call");
			}

			@Override
			public IResource resolveRelative(String identifier, IResource parent, ResourceType resourceType) throws ResourceNotFoundException 
			{
				if ( "source2".equals( identifier ) ) {
					return new StringResource( identifier , source2 , ResourceType.SOURCE_CODE);
				} else if ( "source1".equals( identifier ) ) {
					return new StringResource( identifier , source2 , ResourceType.SOURCE_CODE);
				}
				throw new IllegalArgumentException("Unexpected call for '"+identifier+"'");				
			}
		});

		c.setObjectCodeWriterFactory( new NullObjectCodeWriterFactory() );
		
		final ICompilationUnit unit1 = CompilationUnit.createInstance( "source1" , source1 );
		c.compile( Collections.singletonList( unit1) , new CompilationListener() );
		
		Misc.printCompilationErrors( unit1  , source1 , true );
		
		assertTrue( unit1.hasErrors() );
		assertEquals( 1 , unit1.getDependencies().size() );
		
		final ICompilationUnit unit2 = unit1.getDependencies().get(0);
		Misc.printCompilationErrors( unit2  , source1 , true );
		assertFalse( unit2.hasErrors() );		
	}	
}
