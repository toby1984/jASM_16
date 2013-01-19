package de.codesourcery.jasm16.compiler;

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

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.WordAddress;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.IncludeSourceFileNode;
import de.codesourcery.jasm16.ast.OriginNode;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResourceMatcher;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
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

/**
 * Analyzes dependencies between compilation units (source files) 
 * created by include directives.
 *
 * <p>This class does not do full parsing using a {@link IParser} but instead just uses an {@link ILexer}
 * and looks for {@link TokenType#INCLUDE_SOURCE} tokens,parsing only those.</p>
 * 
 * @author tobias.gierke@voipfuture.com
 */
public class SourceFileDependencyAnalyzer 
{
    // debugging only
    private static final AtomicLong DOT_IDS = new AtomicLong(0);    
    
    private static class ParsingResult 
    {
        public final List<IResource> includedSources=new ArrayList<IResource>();
        public Address objectCodeStartingAddress=Address.wordAddress( 0 );
    }    
    
    /**
     * Calculates the root set for a given set of <code>ICompilationUnit</code>s.
     * 
     * <p>The root set consists of the dependency graph's root nodes (=the compilation
     * units that no other files depend on and thus may be the starting point of a compilation run).</p>
     * 
     * <p>Use {@link linearize(DependencyNode)} to determine the order in which all compilation units 
     * of a root node need to be visited</p>.
     * 
     * @param units
     * @param resolver
     * @param resourceMatcher
     * @return
     * @throws UnknownCompilationOrderException
     * @throws ResourceNotFoundException
     */
    public List<DependencyNode> calculateRootSet(List<ICompilationUnit> units,IResourceResolver resolver,IResourceMatcher resourceMatcher) throws UnknownCompilationOrderException, ResourceNotFoundException
    {
        if ( units.isEmpty() ) {
            return new ArrayList<>();
        }
        
        // create dependency graph from units
        final List<DependencyNode> graph = new ArrayList<>();
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
            
            for ( IResource r : sourceFile ) 
            {
                final DependencyNode dependency = getOrCreateGraphNode( findCompilationUnit( r , resourceMatcher , units ) , graph );
                node.dependencies.add( dependency ); // A -> B
                dependency.dependentNodes.add( node ); // B <- A
            }
        }
        
        // linearize graph root set
        return determineRootSet( graph );
    }
    
    private ICompilationUnit findCompilationUnit(IResource resource , IResourceMatcher matcher , List<ICompilationUnit> units) 
    {
        for ( ICompilationUnit unit : units ) {
            if ( matcher.isSame( unit.getResource() , resource ) ) {
                return unit;
            }
        }
        throw new RuntimeException("Unable to find resource "+resource+" in "+units);
    }    
    
    /**
     * Extracts compilation units from a dependency graph so that dependencies come before the
     * unit that depends on them.
     *      
     * @param dependencyNode
     * @return
     */
    public List<ICompilationUnit> linearize(DependencyNode dependencyNode)
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
            public boolean visit(DependencyNode node)
            {
                if ( node.dependentNodes.isEmpty() ) {
                    result.add( node );
                }
                return true;
            }
        };
        n.visitNodeAndDirectDependenciesOnly( visitor );
        return result;
    }
    
    public interface NodeVisitor {
        public boolean visit(DependencyNode node);
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
    
    /**
     * (DEBUG) Creates a .dot graph description from a dependency graph.
     * 
     * <p>The generated graph description can be visualized using the fantastic GraphViz package by AT&T.</p>
     * 
     * @param graphName
     * @param graph
     * @return
     */
    public static String toDOTGraph(String graphName, DependencyNode graph) 
    {
        final IdentityHashMap<DependencyNode, String> dotIds = new IdentityHashMap<>();
        
        final StringBuilder edgeBuilder = new StringBuilder();
        
        visitGraph( graph , edgeBuilder , dotIds );
        
        final StringBuilder graphBuilder = new StringBuilder("digraph \""+graphName+"\" {");
        for (Map.Entry<DependencyNode, String> entry : dotIds.entrySet() )
        {
            graphBuilder.append( entry.getValue()+" [label=\""+entry.getKey().getCompilationUnit().getResource().getIdentifier()+"\"]\n");
        }
        graphBuilder.append( edgeBuilder.toString() );
        graphBuilder.append("\n}");
        return graphBuilder.toString();
    }
    
    private static void visitGraph(DependencyNode graph, StringBuilder edgeBuilder, IdentityHashMap<DependencyNode, String> dotIds)
    {
        if ( dotIds.containsKey( graph ) ) {
            return;
        }
        
        final String nodeId = getDOTIdentifier( graph , dotIds );
        for ( DependencyNode child : graph.getDependencies() ) {
            visitGraph( child , edgeBuilder , dotIds );
            edgeBuilder.append( nodeId+" -> "+getDOTIdentifier( child , dotIds )+"\n");            
        }
    }

    private static String getDOTIdentifier(DependencyNode node,IdentityHashMap<DependencyNode, String> map) {
        String result = map.get(node);
        if ( result == null ) {
            result = Long.toString( DOT_IDS.incrementAndGet() );
            map.put( node , result );
        }
        return result;
    }
    
    /**
     * A node in the dependency graph.
     *
     * @author tobias.gierke@code-sourcery.de
     */
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
            if ( node instanceof IncludeSourceFileNode && ! unit.hasErrors()  ) 
            {
                final IResource resource = ((IncludeSourceFileNode) node).getResource();
                if ( resource != null ) {
                    parseResult.includedSources.add( resource );
                }
                return; /* RETURN */
            }
            String errorMsg = "Failed to parse .includesource directive in "+unit+", got node: "+node+" , unit.hasErrors = "+unit.hasErrors();
            throw new ParseException(errorMsg,-1);            
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
