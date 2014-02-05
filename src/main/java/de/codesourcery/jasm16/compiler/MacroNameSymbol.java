package de.codesourcery.jasm16.compiler;

import de.codesourcery.jasm16.ast.StartMacroNode;
import de.codesourcery.jasm16.parser.Identifier;
import de.codesourcery.jasm16.utils.ITextRegion;
import de.codesourcery.jasm16.utils.TextRegion;

public class MacroNameSymbol extends AbstractSymbol 
{
	private final StartMacroNode macroDefinition;
	
	public MacroNameSymbol( StartMacroNode macroDefinition, ICompilationUnit unit, ITextRegion location, Identifier identifier) {
		super(unit, location, identifier, null );
		if ( macroDefinition == null ) {
			throw new IllegalArgumentException("macroDefinition must not be NULL");
		}
		this.macroDefinition = macroDefinition;
	}

	@Override
	public ISymbol createCopy() {
		return new MacroNameSymbol( this.macroDefinition  , this.getCompilationUnit() , this.getLocation() , this.getName()  );
	}

	@Override
	public ISymbol withIdentifier(Identifier newIdentifier) 
	{
		final TextRegion newLocation = new TextRegion( getLocation().getStartingOffset() , newIdentifier.getRawValue().length() );
		return new MacroNameSymbol( this.macroDefinition , getCompilationUnit() , newLocation , newIdentifier );
	}

	@Override
	public ISymbol withScope(ISymbol newScope) 
	{
		if ( newScope != null ) {
			throw new IllegalArgumentException("Cannot set scope on macro name "+this);
		}
		return new MacroNameSymbol( this.macroDefinition ,  getCompilationUnit() , getLocation() , getName() );
	}
	
	public StartMacroNode getMacroDefinition() {
		return macroDefinition;
	}
}
