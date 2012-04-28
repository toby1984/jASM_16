package de.codesourcery.jasm16.ide;

import java.io.File;
import java.io.IOException;

public interface IApplicationConfig {

	public File getWorkspaceDirectory();
	
	public void setWorkspaceDirectory(File dir) throws IOException;

	void saveConfiguration();
}
