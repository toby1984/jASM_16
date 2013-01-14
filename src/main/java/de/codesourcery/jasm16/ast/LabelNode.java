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

import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.exceptions.DuplicateSymbolException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
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
    private Identifier identifier;
    private Label label;
    
    public Identifier getIdentifier()
    {
        return identifier;
    }
    
    public Label getLabel()
    {
    	return label; 
    }
    
    public void setLabel(Label newLabel) {
    	if (newLabel == null) {
			throw new IllegalArgumentException("newLabel must not be null");
		}
    	this.label = newLabel;
    	this.identifier = newLabel.getIdentifier();
	}
    
    @Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
        final int startIndex = context.currentParseIndex();
        
    	ITextRegion range = null;
    	boolean leadingCharacterFound = false;
    	if ( context.peek().hasType( TokenType.COLON ) || context.peek().hasType( TokenType.DOT ) ) {
    	    range = new TextRegion( context.read() );
    	    leadingCharacterFound = true;
    	} 
        
    	final int identifierStartIndex = context.currentParseIndex();
    	
        identifier = context.parseIdentifier( range );
        if ( range == null ) {
            range = new TextRegion( startIndex , context.currentParseIndex() - startIndex );
        }
        
        final ITextRegion symbolRange = new TextRegion(identifierStartIndex, identifier.getRawValue().length() );
        this.label = new Label( context.getCompilationUnit() , symbolRange , identifier );
        
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
        
        try {
            context.getSymbolTable().defineSymbol( this.label );
        } 
        catch(DuplicateSymbolException e) 
        {
            final String message = "Duplicate symbol '"+identifier+"' found at "+range+" in "+context.getCompilationUnit()+" , " +
            		"previous definition found in "+e.getExistingDefinition().getCompilationUnit();
            addCompilationErrorAndAdvanceParser( new CompilationError(message,context.getCompilationUnit(),this,e) , context );
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
