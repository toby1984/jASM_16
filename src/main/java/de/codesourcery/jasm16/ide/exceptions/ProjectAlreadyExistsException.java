package de.codesourcery.jasm16.ide.exceptions;

public class ProjectAlreadyExistsException extends Exception {

	private final String projectName;
	
	public ProjectAlreadyExistsException(String projectName) 
	{
		this( projectName , "A project with name '"+projectName+"' already exists in this workspace");
	}
	
	public ProjectAlreadyExistsException(String projectName,String message) 
	{
		super( message );
		this.projectName=projectName;
	}
		
	
	public String getProjectName() {
		return projectName;
	}
}
