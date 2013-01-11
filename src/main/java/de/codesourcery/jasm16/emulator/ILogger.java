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

public interface ILogger {
	
    public static final ILogger NOP_LOGGER = new ILogger() {

        @Override
        public void setDebugEnabled(boolean yesNo) {}

        @Override
        public boolean isDebugEnabled() {return false;}

        @Override
        public void info(String message) {}

        @Override
        public void info(String message, Throwable cause) {}

        @Override
        public void warn(String message) {}

        @Override
        public void warn(String message, Throwable cause) {}

        @Override
        public void error(String message) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message) {}

        @Override
        public void debug(String message, Throwable cause) {}
    };
    
	public void setDebugEnabled(boolean yesNo);
	
	public boolean isDebugEnabled();

	public void info(String message);
	
	public void info(String message,Throwable cause);
	
	public void warn(String message);
	
	public void warn(String message,Throwable cause);
	
	public void error(String message);
	
	public void error(String message,Throwable cause);
	
	public void debug(String message);
	
	public void debug(String message,Throwable cause);
}
