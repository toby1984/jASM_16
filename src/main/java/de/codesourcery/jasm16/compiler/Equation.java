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

import java.util.Iterator;
import java.util.LinkedHashMap;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTUtils;
import de.codesourcery.jasm16.ast.ISimpleASTNodeVisitor;
import de.codesourcery.jasm16.ast.SymbolReferenceNode;
import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * A variable assignment (.equ).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Equation extends AbstractSymbol implements IValueSymbol {

	private static Logger LOG = Logger.getLogger(Equation.class);
	
	private TermNode expression;
	
	public Equation(ICompilationUnit unit, 
			ITextRegion location,
			Identifier identifier,
			TermNode expression) 
	{
		super(unit, location, identifier,null);
		if ( expression == null ) {
			throw new IllegalArgumentException("expression must not be NULL");
		}
		this.expression = expression;
	}
	
	@Override
	public ISymbol createCopy() 
	{
		return new Equation( getCompilationUnit() , getLocation() , getName() , expression == null ? null : (TermNode) expression.createCopy(false) );
	}
	
	@Override
	public ISymbol withIdentifier(Identifier newIdentifier) 
	{
		final TextRegion newLocation = new TextRegion( getLocation().getStartingOffset() , newIdentifier.getRawValue().length() );
		return new Equation( getCompilationUnit() , newLocation , newIdentifier , expression );
	}
	
	@Override
	public ISymbol withScope(ISymbol newScope) 
	{
		if ( newScope != null ) {
			throw new IllegalArgumentException(".equ definitions always have global scope");
		}
		return new Equation( getCompilationUnit() , getLocation() , getName() , expression );
	}	
	
	/**
	 * Invoked by ASTValidationPhase1 to prevent later
	 * compilation phases from choking on circular equation dependencies.
	 */
	public void clearExpression() {
		expression = null;
	}

	@Override
	public Long getValue(ISymbolTable symbolTable) 
	{
		if ( expression == null ) {
			return null;
		}
		return expression.calculate( symbolTable );
	}
	
	public TermNode getExpression() {
		return expression;
	}

	@Override
	public void setValue(Long value) {
		throw new UnsupportedOperationException( "cannot set value of constant equation");
	}
	
    private static class CircularEquationsException extends RuntimeException {
        
    	public CircularEquationsException(String message) {
    		super(message);
    	}
    }
    
	public static void checkCyclicDependencies(final Identifier id,ISymbolTable symbolTable) 
	{
		final LinkedHashMap<Identifier, Equation> symbolsSeen = new LinkedHashMap<Identifier, Equation>();
		checkCyclicDependencies( id , symbolTable , symbolsSeen );
	}

	private static void checkCyclicDependencies(final Identifier id,
			ISymbolTable symbolTable, LinkedHashMap<Identifier, Equation> symbolsSeen) 
	{
		final ISymbol symbol = symbolTable.getSymbol( id , null );
		if ( symbol instanceof Equation ) 
		{
			try {
				checkCyclicDependencies( (Equation) symbol , symbolTable , symbolsSeen );
			} 
			catch (CircularEquationsException e) 
			{
				((Equation) symbol).clearExpression();
				final ICompilationUnit unit = symbol.getCompilationUnit();
				unit.addMarker(
					new CompilationError( "Equation '"+symbol.getFullyQualifiedName()+"' has circular dependency: "+
							e.getMessage() , unit , symbol.getLocation() )
				);
			}
		}
	}

	private static void checkCyclicDependencies(Equation symbol,final ISymbolTable symbolTable,
			final LinkedHashMap<Identifier,Equation> symbolsSeen) throws CircularEquationsException 
	{
		if ( symbolsSeen.containsKey( symbol.getName() ) ) 
		{
			symbol.clearExpression();
			failWithException(symbolsSeen);
		}
		symbolsSeen.put( symbol.getName()  , symbol );
		
		if ( symbol.getExpression() != null ) 
		{
			final ISimpleASTNodeVisitor<ASTNode> checkingVisitor = new ISimpleASTNodeVisitor<ASTNode>() 
			{
				@Override
				public boolean visit(ASTNode node) 
				{
					if ( node instanceof SymbolReferenceNode) 
					{
						final SymbolReferenceNode refNode = (SymbolReferenceNode) node;
						if ( refNode.getIdentifier() != null ) {
							checkCyclicDependencies( refNode.getIdentifier() , symbolTable , symbolsSeen );
						}
					}
					return true;
				}
			};
			ASTUtils.visitInOrder( symbol.getExpression() , checkingVisitor );
		}
	}

	private static void failWithException(LinkedHashMap<Identifier, Equation> symbolsSeen) throws CircularEquationsException 
	{
		final StringBuilder cycle = new StringBuilder();
		for (Iterator<Identifier> it = symbolsSeen.keySet().iterator(); it.hasNext();) {
			Identifier id = it.next();
			cycle.append( id );
			if ( it.hasNext() ) {
				cycle.append(" <-> ");
			}
		}
		cycle.append(" <-> ").append( symbolsSeen.keySet().iterator().next() );
		final String errorMsg ="Equations have circular dependency: "+cycle;
		LOG.error("createParseContextForInclude(): "+errorMsg);
		throw new CircularEquationsException( cycle.toString() );
	}
}
