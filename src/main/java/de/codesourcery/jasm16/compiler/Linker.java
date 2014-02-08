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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.Size.SizeInBytes;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.ByteArrayObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.utils.Misc;

public class Linker
{
    private static final byte[] SELFRELOCATION_CODE;
    
    public Linker() {
    }
    
    static 
    {
        final String source = "$start:\n" + 
        		              "        SET a, PC; A now points to $start\n" + 
        		              "        SET j,[ a + ($relocationtable-$start)]; C = number of relocation table entries\n" + 
        		              "        SET y,a; Y will become the total offset to add                               \n" + 
        		              "        ADD y,j; add the number of relocation table entries\n" + 
        		              "        ADD y, $relocationtable - $start; add size of code in front of relocation table\n" + 
        		              "        SET i,y\n" + 
        		              "        ADD y,1; add +1 for word that holds number of entries\n" + 
        		              "$relocationloop:\n" + 
        		              "        STD x, [i]; load address to relocate into X and decrement I and J\n" + 
        		              "        IFU j,0\n" + 
        		              "        SET PC,y; => jump to $exit\n" + 
        		              "        ADD x,y\n" + 
        		              "        ADD [x] , y ; add offset\n" + 
        		              "$thisInstruction:\n" + 
        		              "        SUB PC, $thisInstruction - $relocationloop\n" + 
        		              "$relocationtable:";
        
        final ICompiler c = new Compiler();
        c.setCompilerOption(CompilerOption.RELAXED_PARSING,true);

        final ByteArrayObjectCodeWriterFactory factory = new ByteArrayObjectCodeWriterFactory();
        c.setObjectCodeWriterFactory( factory );

        final ICompilationUnit unit = CompilationUnit.createInstance( "self-relocation code" , source );
        c.compile( Collections.singletonList( unit ) );

        if ( unit.hasErrors() ) 
        {
            Misc.printCompilationErrors(unit, source, false );
            throw new RuntimeException("Failed to compile relocator code ?");
        }

        SELFRELOCATION_CODE = factory.getBytes();
        if ( SELFRELOCATION_CODE == null || SELFRELOCATION_CODE.length == 0 ) {
            throw new RuntimeException("Failed to compile relocator code ?");            
        }
    }    
    
    /**
     * Link one or more files into an program file.
     * 
     * @param objectFiles
     * @param outputFile
     * @param createSelfRelocatingCode whether to prepend the program with self-relocation code. <b>This requires that all
     * compilation units where compiled with {@link CompilerOption#GENERATE_RELOCATION_INFORMATION}.</b>
     * @param rewriteLabelAddresses whether symbol tables should be updated to account for the
     * size of self-relocation code in front of the executable (only applicable if <code>createSelfRelocatingCode</code> was set to <code>true</code>)
     * @return
     * @throws IOException
     */
    public Executable link(List<CompiledCode> objectFiles,
            DebugInfo debugInfo,
            final File outputFile,
            boolean createSelfRelocatingCode,
            boolean rewriteLabelAddresses) throws IOException 
    {
        final FileResource executable = new FileResource( outputFile , ResourceType.EXECUTABLE );
        
        final OutputStream out = executable.createOutputStream( true );
        try 
        {
            // sanity check to assert that compilation units actually
            // where passed into this method in the same order their object code was generated
            ICompilationUnit previous = null;
            for ( CompiledCode r : objectFiles ) 
            {
                final Address currentOffset = r.getCompilationUnit().getObjectCodeStartOffset();
                if ( previous == null ) {
                    previous = r.getCompilationUnit();
                } 
                else 
                {
                    Address previousOffset = previous.getObjectCodeStartOffset();
                    if ( currentOffset.getByteAddressValue() < previousOffset.getByteAddressValue() ) {
                        throw new IllegalArgumentException("Bad input list, compilation unit "+r.getCompilationUnit()+" has "+
                    "address "+currentOffset+" but comes after "+previous+" with offset "+previousOffset);
                    }
                }
            }
            
            final RelocationTable combined = new RelocationTable();
            
            if ( createSelfRelocatingCode ) 
            {
                for ( CompiledCode r : objectFiles ) 
                {
                    combined.merge( r.getCompilationUnit().getRelocationTable() , 
                    		r.getCompilationUnit().getObjectCodeStartOffset() );
                }
                writeRelocationHeader( combined , out );
            }
            
            int currentOffset = 0;
            
            for ( CompiledCode r : objectFiles ) 
            {
            	final boolean hasAST = r.getCompilationUnit().getAST() != null;
            	
            	final Address start = ASTUtils.getEarliestMemoryLocation( r.getCompilationUnit().getAST() );
            	final Address end = ASTUtils.getLatestMemoryLocation( r.getCompilationUnit().getAST() );
            	
            	System.out.println("LINKING: [ "+Misc.toHexString( Address.byteAddress( currentOffset ) )+"] "+
            	r.getObjectCode()+" [ AST: "+hasAST+" , offset_from_CU: "+start+" - "+end);
            	
            	// write object code
                final InputStream in = r.getObjectCode().createInputStream();
                int bytesWritten = 0;
                try {
                	bytesWritten = IOUtils.copy( in , out );
                } finally {
                    IOUtils.closeQuietly( in );
                }
                currentOffset += bytesWritten;
            }
            
            if ( createSelfRelocatingCode && rewriteLabelAddresses ) {
                
                final SizeInBytes offset = Size.bytes( SELFRELOCATION_CODE.length );
                final Size size = combined.getBinarySize().plus( offset );
                
                for ( CompiledCode r : objectFiles ) 
                {
                    final ISimpleASTNodeVisitor<ASTNode> visitor = new ISimpleASTNodeVisitor<ASTNode>() {
                        
                        @Override
                        public boolean visit(ASTNode node)
                        {
                            if ( node instanceof ObjectCodeOutputNode) {
                                ObjectCodeOutputNode n = (ObjectCodeOutputNode) node;
                                if ( n.getAddress() != null ) {
                                    n.adjustAddress( size.getSizeInWords() );
                                }
                            }
                            return true;
                        }
                    };
                    
                    ASTUtils.visitInOrder( r.getCompilationUnit().getAST() , visitor );
                    
                    for ( ISymbol s : r.getCompilationUnit().getSymbolTable().getSymbols() ) {
                        if ( s instanceof Label) {
                            final Label l = (Label) s;
                            if ( l.getAddress() != null ) {
                                l.setAddress( l.getAddress().plus( size , false ) );
                            }
                        }
                    }
                    
                    debugInfo.relocate( Address.byteAddress( offset.getSizeInBytes() ) );
                }                
            }
            
        } finally {
            IOUtils.closeQuietly( out );
        }
        return new Executable(outputFile.getAbsolutePath(),debugInfo) {
			
			@Override
			public OutputStream createOutputStream(boolean append) throws IOException {
				return new FileOutputStream( outputFile , append );
			}
			
			@Override
			public InputStream createInputStream() throws IOException {
				return new FileInputStream( outputFile );
			}
		};
    }

    private void writeRelocationHeader(RelocationTable table, OutputStream out) throws IOException
    {
        out.write( SELFRELOCATION_CODE );
        out.write( table.toByteArray() );
    }
}