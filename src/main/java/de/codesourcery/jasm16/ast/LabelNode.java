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

import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.compiler.CompilationError;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.exceptions.DuplicateSymbolException;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRange;
import de.codesourcery.jasm16.utils.TextRange;

/**
 * An AST node that represents a label definition.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class LabelNode extends ASTNode
{
    private Label label;
    
    public Identifier getIdentifier()
    {
        return label.getIdentifier();
    }
    
    public Label getLabel()
    {
    	return label; 
    }
    
    @Override
	protected ASTNode parseInternal(IParseContext context) throws ParseException
    {
        final int startIndex = context.currentParseIndex();
        
    	ITextRange range = null;
    	boolean leadingCharacterFound = false;
    	if ( context.peek().hasType( TokenType.COLON ) || context.peek().hasType( TokenType.DOT ) ) {
    	    range = new TextRange( context.read() );
    	    leadingCharacterFound = true;
    	} 
        
        final Identifier identifier = context.parseIdentifier( range );
        if ( range == null ) {
            range = new TextRange( startIndex , context.currentParseIndex() - startIndex );
        }
        
        this.label = new Label( context.getCompilationUnit() , range , identifier );
        
        if ( ! leadingCharacterFound  ) 
        {
            if ( context.peek().hasType( TokenType.COLON ) ) {
                range.merge( context.read( TokenType.COLON ) );   
                mergeWithAllTokensTextRange( range );                 
            } else {
                mergeWithAllTokensTextRange( range );                 
                context.addCompilationError( "Label lacks trailing colon", this );
            }
        } else {
            mergeWithAllTokensTextRange( range );              
        }
        
        if ( Register.isRegisterIdentifier( label.getIdentifier().getRawValue() ) ) {
            throw new ParseException("Label name clashes with register identifier, not allowed",getTextRange());
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
    public LabelNode copySingleNode()
    {
        final LabelNode result = new LabelNode();
        result.label = label;
        return result;
    }
    
    @Override
    public boolean supportsChildNodes() {
        return false;
    }    
}
