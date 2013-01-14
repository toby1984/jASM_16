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

import org.apache.commons.lang.ObjectUtils;

import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.IToken;
import de.codesourcery.jasm16.lexer.TokenType;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.Operator;

/**
 * AST node that represents a register reference (identifier).
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class RegisterReferenceNode extends TermNode
{
	private Register register;
	private boolean hasPreDecrement;
	private boolean hasPostIncrement;
	
	@Override
	public boolean equals(Object obj)
	{
	    if ( obj == this ) {
	        return true;
	    }
	    if ( obj instanceof RegisterReferenceNode) 
	    {
	        final RegisterReferenceNode other = (RegisterReferenceNode) obj;
	        return ObjectUtils.equals( this.register , other.register ) &&
	               this.hasPostIncrement == other.hasPostIncrement &&
	               this.hasPreDecrement == other.hasPreDecrement;
	    }
	    return false; 
	}
	
    @Override
	protected RegisterReferenceNode parseInternal(IParseContext context) throws ParseException
    {
        /* 0x18: POP / [SP++]                                            
         * 0x19: PEEK / [SP]                                             
         * 0x1a: PUSH / [--SP] */       	 
    	IToken token = context.peek();
    	if ( token.hasType( TokenType.PICK ) ||
    		 token.hasType( TokenType.PUSH ) ||
    	     token.hasType( TokenType.PEEK ) ||
    	     token.hasType( TokenType.POP )    	     
    	   ) 
    	{
    		register = Register.SP;
    		
    	    token = context.read();
    	    switch( token.getType() ) {
    	    	case PICK:
    	    	break;
	    	    case PUSH:
	    	    	hasPreDecrement = true;
	    	    	break;
	    	    case PEEK:
	    	    	break;
	    	    case POP:
	    	    	hasPostIncrement = true;
	    	    	break;
	    	    default: 
	    	    	throw new RuntimeException("Unreachable code reached");
    	    }
            mergeWithAllTokensTextRegion( token );
    	    return this; 
    	}  
    	
        final int startOffset = context.currentParseIndex();
 	
    	if ( token.hasType( TokenType.OPERATOR ) ) 
    	{
    		final Operator op = Operator.fromString( token.getContents() );
    		if ( op != Operator.DECREMENT ) 
    		{
    			throw new ParseException("Operator "+op+" not supported with register", token );
    		}
   			mergeWithAllTokensTextRegion( context.read() );
   			hasPreDecrement = true;
    	}
    	
    	token = context.read( "expected a register identifier",TokenType.CHARACTERS );
    	
    	if ( ! Register.isRegisterIdentifier( token.getContents() ) ) {
    		throw new ParseException("Not a valid register: '"+token.getContents()+"'",token );
    	}

    	register = Register.fromString( token.getContents() );
   		mergeWithAllTokensTextRegion( token );
    	
    	if ( ! context.eof() && context.peek().hasType( TokenType.OPERATOR ) ) 
    	{
    		final Operator op = Operator.fromString( context.peek().getContents() );
    		if ( op == Operator.INCREMENT ) 
    		{
    			mergeWithAllTokensTextRegion( context.read() );
    			hasPostIncrement = true;
    		} 
    	}
    	
    	if ( hasPreDecrement || hasPostIncrement ) 
    	{
    		if ( hasPreDecrement && hasPostIncrement ) {
    			throw new ParseException("Register cannot have both pre-decrement and post-increment",startOffset, context.currentParseIndex()-startOffset);
    		}
    		if ( hasPreDecrement && ! register.supportsIndirectWithPreDecrement() ) {
    			throw new ParseException("Register "+register+" does not support pre-decrement",startOffset, context.currentParseIndex()-startOffset);    			
    		}
    		if ( hasPostIncrement && ! register.supportsIndirectWithPostIncrement() ) {
    			throw new ParseException("Register "+register+" does not support post-increment",startOffset, context.currentParseIndex()-startOffset); 
    		}
    	}
        return this;
    }

	@Override
	public TermNode reduce(ICompilationContext context) 
	{
		return (TermNode) createCopy(false);
	}

    @Override
	protected RegisterReferenceNode copySingleNode()
    {
    	final RegisterReferenceNode result = new RegisterReferenceNode();
    	result.register = register;
    	result.hasPostIncrement = hasPostIncrement;
    	result.hasPreDecrement = hasPreDecrement;
    	return result;
    }

    public boolean hasPreDecrement() 
    {
    	return hasPreDecrement;
    }
    
    public boolean hasPostIncrement()
    {
    	return hasPostIncrement;
    }    
    
    public Register getRegister()
    {
        return register;
    }    
    
    @Override
    public String toString() 
    {
    	String result="";
    	if ( hasPreDecrement ) {
    		result = "--";
    	}
    	result = register != null ? register.toString() : "<null register?>";
    	if ( hasPostIncrement ) {
    		result = result + "++";
    	}
    	return result;
    }
    
    @Override
    public boolean supportsChildNodes() {
        return false;
    }

	@Override
	public Long calculate(ISymbolTable symbolTable) {
		return Long.valueOf( 0 );
	}    
}
