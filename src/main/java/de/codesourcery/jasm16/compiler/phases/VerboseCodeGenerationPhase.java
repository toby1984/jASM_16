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

import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.Main;
import de.codesourcery.jasm16.compiler.io.FileObjectCodeWriter;
import de.codesourcery.jasm16.compiler.io.IObjectCodeWriter;

/**
 * Phase used by {@link Main} to generate verbose compilation output.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class VerboseCodeGenerationPhase extends CodeGenerationPhase {

    @Override
    protected void outputObjectCode(ICompilationUnit unit, final ICompilationContext compContext,final IObjectCodeWriter writer) throws IOException
    {   
        final String outputFile;
        if ( writer instanceof FileObjectCodeWriter) {
            final FileObjectCodeWriter fileWriter=(FileObjectCodeWriter) writer;
            outputFile = fileWriter.getOutputFile().getAbsolutePath();
        } else {
            outputFile = "<null>";
        }
        
        super.outputObjectCode( unit , compContext , writer );
        System.out.println("Wrote output to file "+outputFile+" ( size is now "+writer.getCurrentWriteOffset().getValue()+" bytes )");
    }
}
