package de.codesourcery.jasm16.compiler;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.AddressRange;
import de.codesourcery.jasm16.compiler.io.AbstractResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.utils.ITextRegion;

public abstract class Executable extends AbstractResource 
{
	private final String identifier;
	
	public Executable(String identifier) 
	{
		super(ResourceType.EXECUTABLE);
		
		if (StringUtils.isBlank(identifier)) {
			throw new IllegalArgumentException(
					"identifier must not be blank/null");
		}
		this.identifier =identifier;
	}
	
	public final ICompilationUnit getCompilationUnitFor(Address address) 
	{
		for ( Map.Entry<AddressRange,ICompilationUnit> entry : getCompilationUnits().entrySet() ) {
			if ( entry.getKey().contains( address ) ) {
				return entry.getValue();
			}
		}
		return null;
	}
	
	protected abstract Map<AddressRange,ICompilationUnit> getCompilationUnits();

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
		
		for ( Map.Entry<AddressRange,ICompilationUnit> entry : getCompilationUnits().entrySet() ) {
			if ( entry.getValue().getResource().getIdentifier().equals( r.getIdentifier() ) ) {
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

	@Override
	public long getAvailableBytes() throws IOException {
		throw new UnsupportedOperationException("Not implemented");
	}

}
