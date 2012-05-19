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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.IncludeSourceFileNode;
import de.codesourcery.jasm16.ast.OriginNode;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.exceptions.AmbigousCompilationOrderException;
import de.codesourcery.jasm16.exceptions.ResourceNotFoundException;
import de.codesourcery.jasm16.exceptions.UnknownCompilationOrderException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.Lexer;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.parser.ParseContext;
import de.codesourcery.jasm16.scanner.Scanner;
import de.codesourcery.jasm16.utils.Misc;

public class DefaultCompilationOrderProvider implements ICompilationOrderProvider
{
    @Override
    public List<ICompilationUnit> determineCompilationOrder(List<ICompilationUnit> units,IResourceResolver resolver) throws UnknownCompilationOrderException, ResourceNotFoundException
    {
        if ( units.isEmpty() ) {
            return new ArrayList<ICompilationUnit>();
        }
        
        // create dependency graph from units
        final List<DependencyNode> graph = new ArrayList<DependencyNode>();
        for ( ICompilationUnit unit : units ) 
        {
            ParsingResult parseResult;
            try {
                parseResult = getIncludedSources( unit , resolver );
            } catch (IOException | ParseException e) {
                throw new UnknownCompilationOrderException("Failed to parse "+unit,e);
            }
            final List<IResource> sourceFile = parseResult.includedSources;
            final DependencyNode node = getOrCreateGraphNode( unit , graph);
            node.objectCodeStartingAddress=parseResult.objectCodeStartingAddress;
            
            for ( IResource r : sourceFile ) {
                final DependencyNode dependency = getOrCreateGraphNode( getUnitForResource( r , units ) , graph );
                node.dependencies.add( dependency ); // A -> B
                dependency.dependentNodes.add( node ); // B <- A
            }
        }
        
        // linearize graph root set
        final List<DependencyNode> rootSet = determineRootSet( graph );
        if ( rootSet.size() > 1 ) {
            return resolveAmbigousRootSet( rootSet );
        } 
        return linearize( rootSet.get(0) ); 
    }
    
    private static class ParsingResult 
    {
        public final List<IResource> includedSources=new ArrayList<IResource>();
        public Address objectCodeStartingAddress=Address.wordAddress( 0 );
    }
    
    protected List<ICompilationUnit> resolveAmbigousRootSet(final List<DependencyNode> rootSet) throws UnknownCompilationOrderException
    {
        // try to order root set ascending by starting address
        Collections.sort( rootSet , new Comparator<DependencyNode>() {

            @Override
            public int compare(DependencyNode o1, DependencyNode o2)
            {
                if ( o1.objectCodeStartingAddress.isLessThan( o2.objectCodeStartingAddress ) ) {
                    return -1;
                } else if ( o1.objectCodeStartingAddress.isGreaterThan( o2.objectCodeStartingAddress ) ) {
                    return 1;
                }
                throw new AmbigousCompilationOrderException("Unable to determine compilation order,ambigous root set:"+rootSet, rootSet);
            }
        } );
        
        final List<ICompilationUnit>  result = new ArrayList<ICompilationUnit> ();
        for ( DependencyNode node : rootSet ) {
            result.addAll( linearize( node ) );
        }
        return result;
    }    
    
    protected List<ICompilationUnit> linearize(DependencyNode dependencyNode)
    {
        // resolve so that dependencies are compiled before the
        // unit that depends on them
        final List<ICompilationUnit> result = new ArrayList<ICompilationUnit>();
        recursiveAdd( dependencyNode , result );
        return result;
    }

    protected void recursiveAdd(DependencyNode node,List<ICompilationUnit> result) 
    {
        for ( DependencyNode dep : node.dependencies ) {
            recursiveAdd( dep , result);
        }
        result.add( node.unit );
    }

    /**
     * The root set contains the graph nodes that no other nodes depends on.
     * 
     * @param graph
     * @return
     */
    protected List<DependencyNode> determineRootSet(List<DependencyNode> graph)
    {
        List<DependencyNode> result = new ArrayList<DependencyNode> ();
        for ( DependencyNode n : graph ) {
            result.addAll( determineRootSet( n ) );
        }
        return result;
    }

    protected List<DependencyNode> determineRootSet(DependencyNode n)
    {
        final List<DependencyNode> result = new ArrayList<DependencyNode> ();
        
        final NodeVisitor visitor= new NodeVisitor() {
            
            @Override
            public void visit(DependencyNode node)
            {
                if ( node.dependentNodes.isEmpty() ) {
                    result.add( node );
                }
            }
        };
        n.visit( visitor );
        return result;
    }
    
    protected interface NodeVisitor {
        public void visit(DependencyNode node);
    }

    protected DependencyNode getOrCreateGraphNode(ICompilationUnit unit,List<DependencyNode> graph) 
    {
        for ( DependencyNode n : graph ) 
        {
            DependencyNode result = findNodeForUnit( n , unit );            
            if ( result != null ) {
                return result;
            }            
        }
        final DependencyNode node = new DependencyNode( unit );
        graph.add( node );
        return node;
    }
    
    protected DependencyNode findNodeForUnit(DependencyNode n,ICompilationUnit unit) 
    {
        if ( n.unit == unit ) {
            return n;
        }
        for ( DependencyNode child : n.dependencies ) {
            DependencyNode result = findNodeForUnit( child , unit );
            if ( result != null ) {
                return result;
            }
        }
        return null;
    }
    
    protected ICompilationUnit getUnitForResource(IResource resource,List<ICompilationUnit> units) throws ResourceNotFoundException {
        for ( ICompilationUnit unit : units ) {
            if ( unit.getResource().isSame( resource ) ) {
                return unit;
            }
        }
        throw new ResourceNotFoundException("Unable to find compilation unit for resource "+resource+" in "+units, resource.getIdentifier() );        
    }
    
    public static final class DependencyNode 
    {
        private final ICompilationUnit unit;
        
        // compilation units included BY this compilation unit 
        private List<DependencyNode> dependencies = new ArrayList<DependencyNode>();
        
        // compilation units that include this compilation unit
        private List<DependencyNode> dependentNodes = new ArrayList<DependencyNode>();      
        
        private Address objectCodeStartingAddress = Address.wordAddress( 0 );
        
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

        public void visit(NodeVisitor visitor) {
        
            visitor.visit( this );
            for ( DependencyNode child : dependencies ) {
                visitor.visit( child );
            }
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
    
    protected ParsingResult getIncludedSources(ICompilationUnit original,IResourceResolver resolver) throws IOException, ParseException 
    {
        // create a copy since the parsing process may add compilation errors , symbols etc.
        final ICompilationUnit copy = CompilationUnit.createInstance( original.getIdentifier() , original.getResource() );
        
        final ParsingResult parseResult = new ParsingResult();
        
        final Set<ParserOption> parserOptions = Collections.singleton( ParserOption.NO_SOURCE_INCLUDE_PROCESSING );   
        final ICompilationUnitResolver compUnitResolver = new ICompilationUnitResolver() {
            
            @Override
            public ICompilationUnit getOrCreateCompilationUnit(IResource resource) throws IOException
            {
                throw new UnsupportedOperationException("Should not be called");
            }
        };
        
        final String input = Misc.readSource(copy);
        final ILexer lexer = new Lexer( new Scanner( input ) );
        
        while( ! lexer.eof() ) 
        {
            if ( lexer.peek().hasType( TokenType.SINGLE_LINE_COMMENT ) ) {
                while ( ! lexer.eof() ) 
                {
                    if ( lexer.peek().isEOL() ) {
                        lexer.read();
                        break;
                    } 
                    lexer.read();
                }
                continue;
            }
            else if ( lexer.peek().hasType( TokenType.STRING_DELIMITER) )
            {
                while ( ! lexer.eof() ) 
                {
                    if ( lexer.peek().isEOL() || lexer.peek().hasType( TokenType.STRING_DELIMITER ) ) 
                    {
                        lexer.read();
                        break;
                    } 
                    lexer.read();
                }
                continue;                
            }
            
            if ( lexer.peek().hasType( TokenType.INCLUDE_SOURCE ) || lexer.peek().hasType( TokenType.ORIGIN ) ) {
                parseNode(copy, resolver, parseResult, parserOptions, compUnitResolver, lexer);
            } else {
                lexer.read(); // skip token
            }
        }
        return parseResult;
    }

    protected void parseNode(ICompilationUnit unit, 
            IResourceResolver resolver, final ParsingResult parseResult,
            final Set<ParserOption> parserOptions, 
            final ICompilationUnitResolver compUnitResolver, 
            final ILexer lexer)
            throws ParseException
    {
        final IParseContext context = new ParseContext(unit, new SymbolTable() , lexer , resolver , compUnitResolver, parserOptions );
        
        if ( lexer.peek().hasType( TokenType.INCLUDE_SOURCE ) ) 
        {
            final ASTNode node = new IncludeSourceFileNode().parse( context );
            if ( node instanceof IncludeSourceFileNode && ! unit.hasErrors()  ) {
                final IResource resource = ((IncludeSourceFileNode) node).getResource();
                if ( resource != null ) {
                    parseResult.includedSources.add( resource );
                }
                return; /* RETURN */
            }
            throw new ParseException("Failed to parse .includesource directive in "+unit,-1);            
        }
        
        if ( lexer.peek().hasType( TokenType.ORIGIN ) ) 
        {
            final ASTNode node = new OriginNode().parse( context );
            if ( node instanceof OriginNode  && ! unit.hasErrors() ) 
            {
                final Address addr = ((OriginNode) node).getAddress();
                if ( addr != null ) {
                    if ( WordAddress.ZERO.equals( unit.getObjectCodeStartOffset() ) ) 
                    {
                        unit.setObjectCodeStartOffset( addr );
                        parseResult.objectCodeStartingAddress = addr;
                    }
                    return;
                }
            }       
            throw new ParseException("Failed to parse .org directive in "+unit,-1);
        }
        
        throw new RuntimeException("Unreachable code reached");
    }
    
}
