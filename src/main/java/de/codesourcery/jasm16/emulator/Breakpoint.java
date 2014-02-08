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
package de.codesourcery.jasm16.emulator;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.Register;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ExpressionNode;
import de.codesourcery.jasm16.ast.TermNode;
import de.codesourcery.jasm16.compiler.CompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.ICompilationUnitResolver;
import de.codesourcery.jasm16.compiler.ISymbolTable;
import de.codesourcery.jasm16.compiler.SymbolTable;
import de.codesourcery.jasm16.compiler.io.FileResourceResolver;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.compiler.io.IResourceResolver;
import de.codesourcery.jasm16.compiler.io.StringResource;
import de.codesourcery.jasm16.exceptions.ParseException;
import de.codesourcery.jasm16.lexer.ILexer;
import de.codesourcery.jasm16.lexer.Lexer;
import de.codesourcery.jasm16.parser.IParseContext;
import de.codesourcery.jasm16.parser.IParser.ParserOption;
import de.codesourcery.jasm16.parser.ParseContext;
import de.codesourcery.jasm16.scanner.Scanner;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Debugger breakpoint.
 * 
 * <p>Implementations MUST be thread-safe.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Breakpoint
{
	private static final Logger LOG = Logger.getLogger(Breakpoint.class);
	
    // Address MUST be immutable !!!
    private final Address address;
    private volatile boolean enabled = true;
    
    private String condition;
    
    public Breakpoint(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("address must not be NULL.");
        }
        this.address = address;
    }
    
    public Breakpoint(Address address,String condition) throws ParseException 
    {
        if (address == null) {
            throw new IllegalArgumentException("address must not be NULL.");
        }
		if (StringUtils.isBlank(condition)) {
			throw new IllegalArgumentException(
					"condition must not be blank/null");
		}
		
		setCondition( condition );
		
        this.address = address;
        this.condition = condition;
    }    
    
    public Address getAddress()
    {
        return address;
    }
    
    public boolean isEnabled() {
		return enabled;
	}
    
    public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
    
    public boolean hasCondition() {
    	return StringUtils.isNotBlank( condition );
    }
    
    public boolean isOneShotBreakpoint() {
    	return false;
    }
    
    public void setCondition(String newCondition) throws ParseException 
    {
    	if ( StringUtils.isBlank( newCondition ) ) {
    		this.condition = null;
    	} 
    	else 
    	{
    		final String oldValue = this.condition;
    		
    		this.condition = newCondition;
    		
    		boolean success = false;
    		try 
    		{
        		calculateConditionValue( new Emulator() );   
        		success = true;
    		} 
    		finally {
    			if ( ! success ) {
    				this.condition = oldValue;
    			}
    		}
    	}
	}
    
    public String getCondition() {
		return condition;
	}
    
    public boolean matches(IEmulator emulator) 
    {
    	if ( ! enabled ) {
    		return false;
    	}
    	if ( hasCondition() ) {
    		return conditionMatches( emulator );
    	}
    	return true;
    }

	private boolean conditionMatches(IEmulator emulator) 
	{
		try {
			return calculateConditionValue( emulator ) != 0;
		} catch (ParseException e) {
			LOG.error("conditionMatches(): Failed to evaluate condition '"+condition+"'",e);
			return false;
		}
	}
	
	private long calculateConditionValue(IEmulator emulator) throws ParseException 
	{
		final StringBuilder trimmed = new StringBuilder();
		for ( char c : condition.toCharArray() ) {
			if ( ! Character.isWhitespace( c ) ) {
				trimmed.append( c );
			}
		}
		final String expanded = substitutePlaceholders( emulator , trimmed.toString() );
		
		final TermNode expression = parseCondition( expanded );
		final Long value = expression.calculate( new SymbolTable("calculateConditionValue(IEmulator)") );
		if ( value == null ) {
			throw new ParseException("Failed to evaluate condition '"+expanded+"'",0,expanded.length());
		}
		return value.longValue();
	}
	
	private String substitutePlaceholders(final IEmulator emulator, String condition) 
	{
		final String[] registers = new String[] { "pc" , "ex" , "sp" , "a","b","c","x","y","z","i","j"};
		
		String registerExpression = "";
		for ( int i = 0 ; i < registers.length ; i++) 
		{
			final String reg = registers[i];
			registerExpression+= reg;
			if ( (i+1) < registers.length ) {
				registerExpression+="|";
			}
		}
		final Pattern registerIndirectRegEx = Pattern.compile("\\[("+registerExpression+")\\]",
				Pattern.CASE_INSENSITIVE);
		
		final Pattern registerImmediateRegEx = Pattern.compile("("+	registerExpression+")",
				Pattern.CASE_INSENSITIVE);
		
		final Pattern memoryIndirectRegEx = Pattern.compile("(\\[[ ]*(0x[0-9a-f]+)[ ]*\\])");
		
		final Pattern hexPattern = Pattern.compile("(0x[a-f0-9]+)",Pattern.CASE_INSENSITIVE);

		final StringBuilder result = new StringBuilder( condition );
		
		// first, replace all memory references with the memory's value
		// at the specified address

		final IPatternReplacer replacer1 = new IPatternReplacer() {

			@Override
			public String replace(Matcher matcher, String context) 
			{
				final String hexString = matcher.group(2);
				final int address = (int) Misc.parseHexString( hexString );
				@SuppressWarnings("deprecation")
				final int decValue = emulator.getMemory().read( address );
				return context.replaceAll( Pattern.quote( matcher.group(1) ) , Integer.toString( decValue ) );
			}
		};
		
		substitutePatterns( result , memoryIndirectRegEx, replacer1 );		
		
		// second, substitute all hexadecimal values (0x1234) with their
		// decimal counterparse so we don't accidently replace a,b,c with their
		// register values		
		
		final IPatternReplacer replacer2 = new IPatternReplacer() {

			@Override
			public String replace(Matcher matcher, String context) 
			{
				final String hexString = matcher.group(1);
				final long decValue = Misc.parseHexString( hexString );
				return context.replaceAll( Pattern.quote( hexString ) , Long.toString( decValue ) );
			}
		};
		
		substitutePatterns( result , hexPattern, replacer2 );
		
		// third, replace all register indirect [ <REG> ] expressions with their respective
		// memory value
		
		final IPatternReplacer replacer3 = new IPatternReplacer() {

			@Override
			public String replace(Matcher matcher, String context) 
			{
				final String register = matcher.group(1);
				final Register reg = Register.fromString( register );
				final int registerValue = emulator.getCPU().getRegisterValue( reg );
				@SuppressWarnings("deprecation")
				final int memoryValue = emulator.getMemory().read( registerValue );
				final String toReplace = Pattern.quote("["+register+"]");
				return context.replaceAll( toReplace , Integer.toString( memoryValue ) );
			}
		};
		
		substitutePatterns( result , registerIndirectRegEx , replacer3 );
		
		// fourth , replace all register immediate values with their respective
		// register values
		final IPatternReplacer replacer4 = new IPatternReplacer() {

			@Override
			public String replace(Matcher matcher, String context) 
			{
				final String register = matcher.group(1);
				final Register reg = Register.fromString( register );
				final int decValue = emulator.getCPU().getRegisterValue( reg );
				final String toReplace = Pattern.quote( register );
				return context.replaceAll( toReplace , Integer.toString( decValue ) );
			}
		};
		
		substitutePatterns( result , registerImmediateRegEx, replacer4 );
		
		return result.toString();
	}
	
	private void substitutePatterns(StringBuilder result,Pattern pattern,IPatternReplacer replacer) 
	{
		boolean replaced = false;
		do {
			replaced = false;
			final Matcher m = pattern.matcher( result.toString() );
			if ( m.find() ) 
			{
				final String newString = replacer.replace( m , result.toString() );
				result.setLength( 0 );
				result.append( newString );
				replaced = true;
			}
		} while ( replaced );
	}
	
	protected interface IPatternReplacer 
	{
		public String replace(Matcher matcher , String context);
	}
	
	private TermNode parseCondition(String condition) throws ParseException 
	{
		final ICompilationUnit unit = CompilationUnit.createInstance("dummy" , 
				new StringResource( "dummy", condition , ResourceType.SOURCE_CODE ) );
		final ISymbolTable symbolTable = new SymbolTable("parseCondition(String) in IEmulator");
		final ILexer lexer = new Lexer( new Scanner( condition ) );
		final IResourceResolver resourceResolver=new FileResourceResolver() {

			@Override
			protected ResourceType determineResourceType(File file) {
				return ResourceType.UNKNOWN;
			}
		};
		
		final ICompilationUnitResolver unitResolver = new ICompilationUnitResolver() {

			@Override
			public ICompilationUnit getOrCreateCompilationUnit(
					IResource resource) throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public ICompilationUnit getCompilationUnit(IResource resource)
					throws IOException 
			{
				throw new UnsupportedOperationException();
			}
		};
		
		final Set<ParserOption> parserOptions = new HashSet<ParserOption>();
		final IParseContext context = new ParseContext(unit,symbolTable,lexer,resourceResolver,unitResolver,parserOptions,null);
		
		final ASTNode node = new ExpressionNode().parse( context );
		if ( node.hasErrors() ) 
		{
			throw new ParseException("Invalid condition: '"+this.condition+"'",0,this.condition.length());
		}		
		return (TermNode) node;
	}
	
	@Override
	public String toString() {
		return getAddress()+( hasCondition() ? ", "+getCondition()+" " : "" )+( isEnabled() ? "" : "[DISABLED]");
	}
	
}
