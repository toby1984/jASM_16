package de.codesourcery.jasm16.emulator;

import java.io.PrintStream;

public class PrintStreamLogger implements ILogger {

	private final PrintStream stream;
	
	public PrintStreamLogger(PrintStream stream) {
		if (stream == null) {
			throw new IllegalArgumentException("stream must not be null");
		}
		this.stream = stream;
	}
	
	@Override
	public void info(String message, Throwable cause) {
		synchronized ( stream ) {
			stream.println( "INFO: "+message );
			logException(cause);
		}		
	}

	@Override
	public void info(String message) {
		synchronized ( stream ) {
			stream.println( "INFO: "+message );
		}
	}

	@Override
	public void warn(String message) {
		synchronized ( stream ) {
			stream.println( "WARN: "+message );
		}		
	}

	@Override
	public void warn(String message, Throwable cause) {
		synchronized ( stream ) {
			stream.println( "WARN: "+message );
			logException(cause);
		}
	}

	private void logException(Throwable cause) {
		if ( cause != null ) {
			cause.printStackTrace( stream );
		}
	}
	
	@Override
	public void error(String message) {
		synchronized ( stream ) {
			stream.println( "ERROR: "+message );
		}
	}

	@Override
	public void error(String message, Throwable cause) {
		synchronized ( stream ) {
			stream.println( "ERROR: "+message );
			logException(cause);			
		}
	}

	@Override
	public void debug(String message) {
		synchronized ( stream ) {
			stream.println( "DEBUG: "+message );
		}		
	}

	@Override
	public void debug(String message, Throwable cause) {
		synchronized ( stream ) {
			stream.println( "DEBUG: "+message );
			logException(cause);			
		}		
	}
}