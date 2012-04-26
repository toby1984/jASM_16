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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

import org.apache.commons.lang.ArrayUtils;

import de.codesourcery.jasm16.compiler.io.AbstractObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.AbstractObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.ClassPathResource;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.parser.TestHelper;
import de.codesourcery.jasm16.utils.DebugCompilationListener;
import de.codesourcery.jasm16.utils.Misc;

public class CompilerTest extends TestHelper 
{

	public void testCompileOneUnit() throws IOException {

		final Compiler compiler = new Compiler();
		compiler.setObjectCodeWriterFactory( NOP_WRITER );
		
		final IResource resource = new ClassPathResource("specsample.dasm16");
		final ICompilationUnit unit = CompilationUnit.createInstance("classpath input" ,  resource );
		
		final ByteArrayOutputStream[] out = new ByteArrayOutputStream[]{null};
		
		compiler.setObjectCodeWriterFactory( new AbstractObjectCodeWriterFactory() {

			@Override
			protected IObjectCodeWriter createObjectCodeWriter( ICompilationContext context) 
			{
				return new AbstractObjectCodeWriter() {

					@Override
					protected void closeHook() throws IOException {
						out[0].close();
					}

					@Override
					protected OutputStream createOutputStream() throws IOException 
					{
						out[0] = new ByteArrayOutputStream();
						return out[0];
					}

					@Override
					protected void deleteOutputHook() throws IOException {
						out[0] = null;
					}
				};
			}

			@Override
			protected void deleteOutputHook() throws IOException {
			}
		});
		
		compiler.compile( Collections.singletonList( unit ) , new DebugCompilationListener(true) );
		
		final String source = Misc.readSource( resource.createInputStream() );
		Misc.printCompilationErrors( unit , source , false );
		
		assertFalse( unit.hasErrors() );
		assertNotNull( unit.getAST() );
		assertFalse( unit.getAST().hasErrors() );
		assertNotNull( out[0] );
		
		final byte[] actual = out[0].toByteArray();
		final byte[] expected = Misc.readBytes( new ClassPathResource("specsample.dcpu16" ) );
//		assertTrue("Generated object code does not match expectation" , ArrayUtils.isEquals( expected , actual ) );
	}
	
}
