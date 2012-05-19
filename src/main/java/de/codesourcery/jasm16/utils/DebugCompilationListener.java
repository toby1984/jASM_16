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
package de.codesourcery.jasm16.utils;

import java.io.PrintStream;

import de.codesourcery.jasm16.compiler.CompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationListener;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilerPhase;

/**
 * An {@link ICompilationListener} implementation that outputs
 * debugging information (compile speed in lines/s) to
 * standard out.
 *  
 * @author tobias.gierke@code-sourcery.de
 */
public class DebugCompilationListener extends CompilationListener {

    private int parsedLineCount;
    private long overallTime=0;
    private long startTime;
    
    private PrintStream out = System.out;

    private final boolean printDetails;
    public DebugCompilationListener(boolean printDetails) {
        this.printDetails=printDetails;
    }

    
    public void onCompileStart(ICompilerPhase firstPhase)
    {
        overallTime = -System.currentTimeMillis();
    }

    
    public void afterCompile(ICompilerPhase lastPhase)
    {
        overallTime +=System.currentTimeMillis();
        final float speed = parsedLineCount / (overallTime / 1000.0f );
        out.println("Compiled "+parsedLineCount+" lines in "+overallTime+" ms ( "+speed+" lines/s )");
    }

    
    public void start(ICompilerPhase phase)
    {
        startTime = -System.currentTimeMillis();
        if ( printDetails ) {
            out.println("start  : "+phase.getName());
        }
    }

    
    public void success(ICompilerPhase phase)
    {
        startTime += System.currentTimeMillis();
        if ( printDetails ) {
            out.println("success: "+phase.getName()+" [ "+startTime+" ms ]");
        }
    }

    
    public void failure(ICompilerPhase phase)
    {
        startTime += System.currentTimeMillis();
        if ( printDetails ) {
            out.println("FAILURE: "+phase.getName()+" [ "+startTime+" ms ]");
        }
    }

    
    public void failure(ICompilerPhase phase, ICompilationUnit unit) {
        if ( phase.getName().equals( ICompilerPhase.PHASE_PARSE ) ) {
            parsedLineCount = unit.getParsedLineCount();
        }   	    
    }

    
    public void start(ICompilerPhase phase, ICompilationUnit unit) {
    }

    
    public void success(ICompilerPhase phase, ICompilationUnit unit) {
        if ( phase.getName().equals( ICompilerPhase.PHASE_PARSE ) ) {
            parsedLineCount = unit.getParsedLineCount();
        }	    
    }

    public PrintStream getOutput()
    {
        return out;
    }

    public void setOutput(PrintStream out)
    {
        this.out = out;
    }
}
