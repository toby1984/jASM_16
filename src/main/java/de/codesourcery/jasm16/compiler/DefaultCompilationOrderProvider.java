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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.codesourcery.jasm16.compiler.dependencyanalysis.DependencyNode;
import de.codesourcery.jasm16.compiler.dependencyanalysis.SourceFileDependencyAnalyzer;
import de.codesourcery.jasm16.compiler.io.IResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.AmbigousCompilationOrderException;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.exceptions.UnknownCompilationOrderException;

public class DefaultCompilationOrderProvider implements ICompilationOrderProvider
{
    private final SourceFileDependencyAnalyzer analyzer = new SourceFileDependencyAnalyzer();
    
    @Override
    public List<ICompilationUnit> determineCompilationOrder(List<ICompilationUnit> units,IResourceResolver resolver,IResourceMatcher resourceMatcher) throws UnknownCompilationOrderException, ResourceNotFoundException
    {
        final List<DependencyNode> rootSet = analyzer.calculateRootSet(units, resolver, resourceMatcher);
        if ( rootSet.size() > 1 ) 
        {
            return resolveAmbigousRootSet( rootSet );
        } 
        return analyzer.linearize( rootSet.get(0) ); 
    }

    protected List<ICompilationUnit> resolveAmbigousRootSet(final List<DependencyNode> rootSet) throws AmbigousCompilationOrderException
    {
        // try to order root set ascending by starting address
        Collections.sort( rootSet , new Comparator<DependencyNode>() {

            @Override
            public int compare(DependencyNode o1, DependencyNode o2)
            {
                if ( o1.getObjectCodeStartingAddress().isLessThan( o2.getObjectCodeStartingAddress() ) ) {
                    return -1;
                } else if ( o1.getObjectCodeStartingAddress().isGreaterThan( o2.getObjectCodeStartingAddress() ) ) {
                    return 1;
                }
                throw new AmbigousCompilationOrderException("Unable to determine compilation order,ambigous root set:"+rootSet, rootSet);
            }
        } );
        
        final List<ICompilationUnit>  result = new ArrayList<ICompilationUnit> ();
        for ( DependencyNode node : rootSet ) {
            result.addAll( analyzer.linearize( node ) );
        }
        return result;
    }    
}