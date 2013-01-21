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
package de.codesourcery.jasm16.compiler.dependencyanalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.utils.Misc;

/**
 * A node in the dependency graph.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class DependencyNode 
{
    public final ICompilationUnit unit;
    
    // compilation units included BY this compilation unit 
    public final List<DependencyNode> dependencies = new ArrayList<DependencyNode>();
    
    // compilation units that include this compilation unit
    public final List<DependencyNode> dependentNodes = new ArrayList<DependencyNode>();      
    
    private Address objectCodeStartingAddress = Address.wordAddress( 0 );
    
    public interface NodeVisitor {
        public boolean visit(DependencyNode node);
    }
    
    public DependencyNode(ICompilationUnit unit)
    {
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be NULL.");
        }
        this.unit = unit;
    }

    public ICompilationUnit getCompilationUnit()
    {
        return unit;
    }
    
    public void setObjectCodeStartingAddress(Address objectCodeStartingAddress) {
		this.objectCodeStartingAddress = objectCodeStartingAddress;
	}
    
    public Address getObjectCodeStartingAddress()
    {
        return objectCodeStartingAddress;
    }
    
    public List<DependencyNode> getDependencies()
    {
        return dependencies;
    }
    
    public List<DependencyNode> getDependentNodes()
    {
        return dependentNodes;
    }

    public void visitNodeAndDirectDependenciesOnly(NodeVisitor visitor) {
    
        visitor.visit( this );
        for ( DependencyNode child : dependencies ) {
            visitor.visit( child );
        }
    }
    
    public boolean visitRecursively(NodeVisitor visitor) {
        return visitRecursively(visitor,new HashSet<DependencyNode>());
    }
    
    private boolean visitRecursively(NodeVisitor visitor,Set<DependencyNode> visited) {
        
        if ( visited.contains( this ) ) {
            return true;
        }
        
        if ( ! visitor.visit( this ) ) {
            return false;
        }
        for ( DependencyNode child : dependencies ) {
            if ( ! child.visitRecursively( visitor , visited ) ) {
                return false;
            }
        }
        return true;
    }        
    
    @Override
    public String toString()
    {
        return "(0x"+Misc.toHexString( objectCodeStartingAddress )+")"+unit.toString();
    }
    
    public void addDependency(DependencyNode other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be NULL.");
        }
        dependencies.add( other );
    }
}