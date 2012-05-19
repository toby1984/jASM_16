package de.codesourcery.jasm16.emulator;

public interface ILogger {

	public void info(String message);
	
	public void info(String message,Throwable cause);
	
	public void warn(String message);
	
	public void warn(String message,Throwable cause);
	
	public void error(String message);
	
	public void error(String message,Throwable cause);
	
	public void debug(String message);
	
	public void debug(String message,Throwable cause);
}
