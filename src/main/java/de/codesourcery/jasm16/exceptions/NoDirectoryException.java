package de.codesourcery.jasm16.exceptions;

import java.io.File;
import java.io.IOException;

/**
 * Thrown if some {@link File} was expected to be a directory
 * but was not.
 * 
 * @author tobias.gierke@code-sourcery.de
 *
 */
public class NoDirectoryException extends IOException {

	private final File file;
	
	public NoDirectoryException(File file) {
		super("Expected "+file.getAbsolutePath()+" to be a directory but was not");
		this.file = file;
	}
	
	public File getFile() {
		return file;
	}
}
