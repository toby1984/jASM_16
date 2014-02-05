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
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.compiler.ICompiler.CompilerOption;
import de.codesourcery.jasm16.compiler.io.AbstractObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.AbstractObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.FileObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.SimpleFileObjectCodeWriterFactory;
import de.codesourcery.jasm16.compiler.phases.VerboseCodeGenerationPhase;
import de.codesourcery.jasm16.utils.DebugCompilationListener;
import de.codesourcery.jasm16.utils.FormattingVisitor;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Class to invoke the compiler from the command-line.
 *
 * <p>Run this class without options (or with '--help') to see the available command-line options.</p>
 * @author tobias.gierke@code-sourcery.de
 */
public class Main {

    private final Compiler compiler = new Compiler();
    
    private ByteArrayOutputStream generatedObjectCode=new ByteArrayOutputStream();
    private File outputFile;    

    /*
     * Options.
     */
    private boolean printSymbolTable = false;
    private boolean printStackTraces = false;
    private boolean printDebugStats=false;
    private boolean verboseOutput = false;
    private boolean dumpObjectCode = false;
    private boolean printSourceCode = false;
    private boolean relaxedParsing = false;
    private boolean relaxedValidation = false;
    private boolean disableLiteralInlining = false;
    private boolean enableLocalLabelSupport = false;
    
    public static void main(String[] args) throws Exception 
    {
        try {
            System.exit( new Main().run( args ) );
        }
        catch(Exception e) 
        {
            System.out.println("\n\nERROR: "+e.getMessage()+"\n" );
            e.printStackTrace();
            System.exit(1);
        }
    }

    private int run(String[] args) throws Exception 
    {
        final List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();

        final Stack<String> arguments = new Stack<String>();
        for ( String arg : args ) {
            arguments.push( arg );
        }
        Collections.reverse( arguments );

        while ( ! arguments.isEmpty() ) 
        {
            final String arg = arguments.peek();
            if ( arg.startsWith("-" ) || arg.startsWith("--" ) ) 
            {
                try {
                    handleCommandlineOption( arg , arguments );
                } catch(NoSuchElementException e) {
                    printError("Invalid command line, option "+arg+" lacks argument.");
                    return 1;
                }
            } 
            else 
            {
                units.add( createCompilationUnit( arguments.pop() ) );
            }			
        }
        
        if ( verboseOutput ) {
        	printVersionInfo();
        }

        if ( units.isEmpty() ) {
            printError("No input files.");
            return 1;
        }

        setupCompiler(units);

        final ICompilationListener listener;
        if ( printDebugStats || verboseOutput) {
            listener = new DebugCompilationListener( printDebugStats );
        } else {
            listener = new CompilationListener();
        }

        if ( printSourceCode ) 
        {
            compiler.insertCompilerPhaseAfter( new CompilerPhase("format-code") 
            {
                @Override
                protected void run(ICompilationUnit unit, ICompilationContext context) throws IOException {
                    if ( unit.getAST() != null ) {
                        ASTUtils.visitInOrder( unit.getAST() , 
                                new FormattingVisitor( context ) );
                    }
                };

            },ICompilerPhase.PHASE_GENERATE_CODE );
        }

        // invoke compiler
        compiler.compile( units , listener );

        boolean hasErrors = false;
        for (ICompilationUnit unit : units) 
        {
            if ( unit.hasErrors() ) 
            {
                Misc.printCompilationErrors(unit, Misc.readSource( unit ) , printStackTraces ); 
                hasErrors = true;
            }
        }
        
        if ( dumpObjectCode ) {
            dumpObjectCode();
        }
        return hasErrors ? 1 : 0;
    }

	private void printVersionInfo() {
		System.out.println( Compiler.VERSION +"\n(c) 2012 by tobias.gierke@code-sourcery.de\n" );
	}
    

    private void dumpObjectCode()
    {
        final byte[] combined = this.generatedObjectCode.toByteArray();
        
        if ( ArrayUtils.isEmpty( combined ) ) 
        {
            System.out.println("No object code generated.");
            return;
        }
        
        System.out.println( "\nHex dump:\n\n"+Misc.toHexDumpWithAddresses( Address.byteAddress( 0 ) , combined , 8 ) );
    }

    private void setupCompiler(List<ICompilationUnit> units) {

        if ( printSymbolTable ) {
            compiler.insertCompilerPhaseAfter( new PrintSymbolTablePhase() , ICompilerPhase.PHASE_GENERATE_CODE );
        }

        setObjectCodeWriterFactory(units);

        if ( verboseOutput ) {
            compiler.replaceCompilerPhase( new VerboseCodeGenerationPhase() , 
            		ICompilerPhase.PHASE_GENERATE_CODE );
        }
        
        if ( enableLocalLabelSupport ) {
        	compiler.setCompilerOption( CompilerOption.LOCAL_LABELS_SUPPORTED, true );
        }
        
        if ( printStackTraces ) {
            compiler.setCompilerOption( CompilerOption.DEBUG_MODE , true );
        }
        
        if ( relaxedParsing ) {
            compiler.setCompilerOption( CompilerOption.RELAXED_PARSING , true );
        }
        
        if ( relaxedValidation ) {
            compiler.setCompilerOption( CompilerOption.RELAXED_VALIDATION , true );            
        }
        
        if ( disableLiteralInlining ) {
            compiler.setCompilerOption( CompilerOption.DISABLE_INLINING , true );    
        }
    }

    private void setObjectCodeWriterFactory(List<ICompilationUnit> units)
    {
        final IObjectCodeWriterFactory factory;
        
        if ( dumpObjectCode ) {
            factory = new AbstractObjectCodeWriterFactory() {
                
                @Override
                protected void deleteOutputHook() throws IOException
                {
                    generatedObjectCode = new ByteArrayOutputStream(); 
                }
                
                @Override
                protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context)
                {
                    return new AbstractObjectCodeWriter() {
                        
                        @Override
                        protected void deleteOutputHook() throws IOException
                        {
                            generatedObjectCode = new ByteArrayOutputStream(); 
                        }
                        
                        @Override
                        protected OutputStream createOutputStream() throws IOException
                        {
                            return generatedObjectCode;
                        }
                        
                        @Override
                        protected void closeHook() throws IOException
                        {
                        }
                    };
                }
            };
        } 
        else if ( outputFile != null )
        {
            outputFile.delete();
            final boolean append = units.size() > 1;
            factory = new SimpleFileObjectCodeWriterFactory( outputFile , append );
        } 
        else 
        {
            // no output file given, just dump source.dasm16 into source.o
            factory = new SimpleFileObjectCodeWriterFactory() 
            {
                @Override
                protected IObjectCodeWriter createObjectCodeWriter(ICompilationContext context) 
                {
                    final IResource resource = context.getCurrentCompilationUnit().getResource();
                    if ( ! (resource instanceof FileResource) ) {
                        throw new RuntimeException("Internal error, not a file resoure: "+resource);
                    }
                    final FileResource fileResource = (FileResource) resource;
                    return new FileObjectCodeWriter( new File( toObjectFileName( fileResource.getFile() ) ) , false );
                }
                
                private String toObjectFileName(File sourceFile) {
                    final List<String> nameComponents = Arrays.asList( sourceFile.getName().split("\\.") );
                    if ( nameComponents.size() == 1 ) {
                        return nameComponents.get(0)+".dcpu16";
                    }
                    final String nameWithoutSuffix = StringUtils.join( nameComponents.subList( 0 , nameComponents.size() - 1  ) , "");
                    return nameWithoutSuffix+".dcpu16";
                }
                
            };
        }
        compiler.setObjectCodeWriterFactory( factory );
    }

    private void handleCommandlineOption(String option,Stack<String> arguments) 
    {
        if ( "-d".equalsIgnoreCase( option ) || "--debug".equalsIgnoreCase( option ) ) {
            this.printStackTraces = true;
            this.printDebugStats = true;
            this.verboseOutput = true ;
            
            arguments.pop();
        } else if ( "--local-labels".equalsIgnoreCase( option ) ) {
            this.enableLocalLabelSupport = true;
            arguments.pop();            
        } else if ( "--relaxed-validation".equalsIgnoreCase( option ) ) {
            this.relaxedValidation = true;
            arguments.pop();
        } else if ( "--disable-literal-inlining".equalsIgnoreCase( option ) ) {
            this.disableLiteralInlining = true;
            arguments.pop();
        } else if ( "--relaxed-parsing".equalsIgnoreCase( option ) ) {
            this.relaxedParsing = true;
            arguments.pop();
        } else if ( "--print".equalsIgnoreCase( option ) ) {
            this.printSourceCode = true;
            arguments.pop();
        } else if ( "--print-symbols".equalsIgnoreCase( option ) ) {
            this.printSymbolTable = true;
            arguments.pop();
        } else if ( "-v".equalsIgnoreCase( option ) || "--verbose".equalsIgnoreCase( option ) ) {
            this.verboseOutput = true;
            arguments.pop();
        } else if ( "--dump".equalsIgnoreCase( option ) ) {
            this.dumpObjectCode = true;
            arguments.pop();
        }
        else if ( "-o".equalsIgnoreCase( option ) ) 
        {
            arguments.pop();
            this.outputFile = new File( arguments.pop() );
        } else if ( "-h".equalsIgnoreCase( option ) || "--help".equalsIgnoreCase( option ) ) {
            printUsage();
            System.exit(1);
        } else {
            printError("ERROR: Unrecognized option '"+option+"'\n\n");
            printUsage();
            System.exit(1);
        }
    }

    private void printUsage() {

    	printVersionInfo();
    	
        final String usage="\nUsage: [options] [-o <output file>] source1 source2 ...\n\n"+
                "-o                          => output file to write generated assembly code to, otherwise code will be written to source.dcpu16\n"+
                "-d or --debug               => print debug output\n"+
                "--print                     => print formatted source code along with hex dump of generated assembly\n"+
                "--print-symbols             => print symbol table\n"+
                "--local-labels              => treat identifiers starting with a dot ('.') as local labels\n"+
                "--disable-literal-inlining  => disable inlining of literals -1 ... 30\n"+
                "--dump                      => instead of writing generated object code to a file, write a hexdump to std out\n"+
                "--relaxed-parsing           => relaxed parsing (instructions are parsed case-insensitive)\n"+
                "--relaxed-validation        => out-of-range values only cause a warning)\n"+                
                "-v or --verbose             => print more verbose output during compilation\n\n";
        System.out.println( usage );		
    }

    // DEBUG
    protected static class PrintSymbolTablePhase extends CompilerPhase {

        public PrintSymbolTablePhase() {
            super( "debug-symbols" ); 
        }

        @Override
        protected void run(ICompilationUnit unit, ICompilationContext context) throws IOException
        {
            final List<ISymbol> symbols = context.getSymbolTable().getSymbols();
            final Comparator<ISymbol> comp = new Comparator<ISymbol>() {

                @Override
                public int compare(ISymbol o1, ISymbol o2) 
                {
                    if ( o1 instanceof Label && o2 instanceof Label ) 
                    {
                        Address addr1 = ((Label) o1).getAddress();
                        Address addr2 = ((Label) o2).getAddress();
                        if ( addr1 != null && addr2 != null ) {
                            return (int) Math.signum( addr1.getValue() - addr2.getValue() );
                        }
                    }
                    return 1;
                }
            };
            Collections.sort( symbols , comp );

            System.out.println("\nSymbol table:\n\n");
            for ( ISymbol s : symbols ) 
            {
                String name = s.getFullyQualifiedName();
                final String sAddress;
                if ( s instanceof Label) {
                    final Address addr = ((Label) s).getAddress();
                    if ( addr != null ) {
                    	sAddress = "0x"+Misc.toHexString( addr.getValue() );
                    } else {
                        sAddress = "< not calculated yet >";
                    }
                } else {
                    sAddress = "< not a label? >";
                }
                System.out.println( Misc.padRight( name, 20 )+"     "+sAddress);				
            }
        }
    }  	

    private void printError(String message) {
        System.out.println("ERROR: "+message);
    }

    private ICompilationUnit createCompilationUnit(String file) throws IOException {

        final File infile = new File( file );
        if ( ! infile.exists() ) {
            throw new IOException("ERROR: File '"+file+"' does not exist.");
        }
        if ( ! infile.isFile() ) {
            throw new IOException("ERROR: '"+file+"' is no file.");
        }		
        return CompilationUnit.createInstance( file , infile );
    }
}
