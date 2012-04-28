package de.codesourcery.jasm16.compiler.io;

/**
 * Abstract resource base-class.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AbstractResource implements IResource {

	private ResourceType type;

	protected AbstractResource(ResourceType type) {
		if (type == null) {
			throw new IllegalArgumentException("type must not be NULL");
		}
		this.type = type;
	}
	
	@Override
	public final void setType(ResourceType type) 
	{
		if (type == null) {
			throw new IllegalArgumentException("type must not be NULL");
		}
		this.type = type;
	}
	
	@Override
	public final ResourceType getType() {
		return type;
	}

	@Override
	public boolean hasType(ResourceType t) {
		if (t == null) {
			throw new IllegalArgumentException("t must not be NULL");
		}
		return getType().equals( t );
	}

}
