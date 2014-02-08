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
package de.codesourcery.jasm16.ast;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.ISymbol;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.exceptions.DuplicateSymbolException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

/**
 * An AST node that represents a label definition.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class LabelNode extends ASTNode
{
    @SuppressWarnings("unused")
	private static final Logger LOG = Logger.getLogger(LabelNode.class);
    
    private Identifier identifier;
    private ISymbol scope;
    
    private Label label;
    
    public LabelNode() {
    }
    
    public Identifier getIdentifier()
    {
        return identifier;
    }
    
    public ISymbol getScope() {
    	return scope;
    }
    
    public Label getLabel()
    {
    	return label; 
    }
    
    public void setLabel(Label newLabel) 
    {
    	if (newLabel == null) {
			throw new IllegalArgumentException("newLabel must not be null");
		}
    	this.label = newLabel;
    	this.identifier = newLabel.getName();
    	this.scope = newLabel.getScope();
	}
    
    @Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
    	final boolean localLabelsAllowed = context.hasParserOption( ParserOption.LOCAL_LABELS_SUPPORTED );
    	
        final int startIndex = context.currentParseIndex();
        
    	ITextRegion range = null;
    	boolean leadingCharacterFound = false;
    	
    	final IToken current = context.peek();
    	final boolean isLocalLabel;
    	if ( current.hasType( TokenType.DOT ) ) 
    	{
    		if ( ! localLabelsAllowed ) {
    			context.addCompilationError("Local label not permitted here",this);
    		}
			range = new TextRegion( context.read() );
			leadingCharacterFound=true;
			isLocalLabel = true;
    	} 
    	else if ( current.hasType( TokenType.COLON ) ) 
    	{
			range = new TextRegion( context.read() );
			leadingCharacterFound=true;  
			isLocalLabel = false;
    	} 
    	else 
    	{
    		isLocalLabel = false;
    	}
    	
    	final int identifierStartIndex = context.currentParseIndex();
    	
        identifier = context.parseIdentifier( range , localLabelsAllowed );
        
        if ( range == null ) {
            range = new TextRegion( startIndex , context.currentParseIndex() - startIndex );
        }
        
        final ITextRegion symbolRange = new TextRegion(identifierStartIndex, identifier.getRawValue().length() );
        
        final ISymbol scope;
        if ( isLocalLabel ) 
        {
        	scope = context.getPreviousGlobalSymbol();
        	if ( scope == null ) {
        		throw new RuntimeException("Internal error, encountered local label "+identifier+" without any previous global label ?");
        	}        	
        } else {
        	scope = null;
        } 
        this.label = new Label( context.getCompilationUnit() , symbolRange , identifier , scope );
        this.scope = scope;
        
        if ( ! leadingCharacterFound  ) 
        {
            if ( context.peek().hasType( TokenType.COLON ) ) {
                range.merge( context.read( TokenType.COLON ) );   
                mergeWithAllTokensTextRegion( range );                 
            } else {
                mergeWithAllTokensTextRegion( range );                 
                context.addCompilationError( "Label lacks trailing colon", this );
            }
        } else {
            mergeWithAllTokensTextRegion( range );              
        }

        try 
        {
            context.getSymbolTable().defineSymbol( this.label );
            
            // only keep track of the previous global symbol if we need to
            if ( ! isLocalLabel ) {
            	context.storePreviousGlobalSymbol( this.label );
            }             
        } 
        catch(DuplicateSymbolException e) 
        {
            final String message = "Duplicate symbol '"+identifier+"' found at "+range+" in "+context.getCompilationUnit()+" , " +
            		"previous definition found in "+e.getExistingDefinition().getCompilationUnit();
            addCompilationErrorAndAdvanceParser(
            		new CompilationError(message,context.getCompilationUnit(),this,e) , context );
        }    
        
        return this;
    }

    @Override
	protected LabelNode copySingleNode()
    {
        final LabelNode result = new LabelNode();
        result.label = label;
        result.identifier = identifier;
        return result;
    }
    
    @Override
    public boolean supportsChildNodes() {
        return false;
    }    
}
