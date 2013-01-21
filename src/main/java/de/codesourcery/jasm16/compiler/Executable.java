package de.codesourcery.jasm16.compiler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.utils.ITextRegion;

public abstract class Executable extends FileResource 
{
	private final String identifier;
	private final DebugInfo debugInfo;
	
	public Executable(String identifier,DebugInfo debugInfo) 
	{
		super(new File(identifier),ResourceType.EXECUTABLE);
		
		if (StringUtils.isBlank(identifier)) {
			throw new IllegalArgumentException(
					"identifier must not be blank/null");
		}
		if ( debugInfo == null ) {
            throw new IllegalArgumentException("debugInfo must not be NULL.");
        }
		this.identifier =identifier;
		this.debugInfo = debugInfo;
	}

	public DebugInfo getDebugInfo()
    {
        return debugInfo;
    }

	@Override
	public String getIdentifier() {
		return identifier;
	}
	
	public boolean refersTo(IResource r) 
	{
		if ( getIdentifier().equals( r.getIdentifier() ) ) 
		{
			return true;
		}
		
		for ( ICompilationUnit unit : debugInfo.getCompilationUnits() ) 
		{
			if ( unit.getResource().getIdentifier().equals( r.getIdentifier() ) ) {
				return true;
			}
		}		
		return false;
	}

	@Override
	public String readText(ITextRegion range) throws IOException 
	{
		throw new UnsupportedOperationException("Not implemented");
	}
}
