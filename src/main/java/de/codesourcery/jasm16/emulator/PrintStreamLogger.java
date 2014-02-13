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

import java.io.PrintStream;

public class PrintStreamLogger implements ILogger {

	private final PrintStream stream;
	
	private volatile LogLevel logLevel=LogLevel.INFO;

	public PrintStreamLogger(PrintStream stream) {
		if (stream == null) {
			throw new IllegalArgumentException("stream must not be null");
		}
		this.stream = stream;
	}
	
	@Override
	public void setLogLevel(LogLevel logLevel)
	{
	    if (logLevel == null) {
            throw new IllegalArgumentException("logLevel must not be NULL.");
        }
	    this.logLevel=logLevel;
        synchronized ( stream ) {
            stream.println( "Loglevel changed to "+logLevel);
        }   	    
	}

	@Override
	public boolean isDebugEnabled() {
		return logLevel.isEnabled( LogLevel.DEBUG );
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
		if ( isDebugEnabled() ) {
			synchronized ( stream ) {
				stream.println( "DEBUG: "+message );
			}		
		}
	}

	@Override
	public void debug(String message, Throwable cause) {
		if ( isDebugEnabled()  ) {
			synchronized ( stream ) {
				stream.println( "DEBUG: "+message );
				logException(cause);			
			}		
		}
	}
}