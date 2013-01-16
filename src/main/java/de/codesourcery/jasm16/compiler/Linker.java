package de.codesourcery.jasm16.compiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Size;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.ByteArrayObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
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

        final ICompilationUnit unit = CompilationUnit.createInstance("string" , source );
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
    public IResource link(List<CompiledCode> objectFiles,File outputFile,boolean createSelfRelocatingCode,boolean rewriteLabelAddresses) throws IOException 
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
                    combined.merge( r.getCompilationUnit().getRelocationTable() , r.getCompilationUnit().getObjectCodeStartOffset() );
                }
                writeRelocationHeader( combined , out );
            }
            
            for ( CompiledCode r : objectFiles ) 
            {
                final InputStream in = r.getObjectCode().createInputStream();
                try {
                    IOUtils.copy( in , out );
                } finally {
                    IOUtils.closeQuietly( in );
                }
            }
            
            if ( createSelfRelocatingCode && rewriteLabelAddresses ) {
                
                final Size size = combined.getBinarySize().plus( Size.bytes( SELFRELOCATION_CODE.length ) );
                
                
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
                }                
            }
            
        } finally {
            IOUtils.closeQuietly( out );
        }
        return executable;
    }

    private void writeRelocationHeader(RelocationTable table, OutputStream out) throws IOException
    {
        out.write( SELFRELOCATION_CODE );
        out.write( table.toByteArray() );
    }
}