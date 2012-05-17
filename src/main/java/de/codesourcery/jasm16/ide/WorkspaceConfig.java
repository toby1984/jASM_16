package de.codesourcery.jasm16.ide;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

/**
 * Workspace configuration.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class WorkspaceConfig {

	private static final Logger LOG = Logger.getLogger(WorkspaceConfig.class);

	public static final String FILE_NAME = ".workspace.properties";

	private static final String KEY_PROJECT = "project.";
	
	private static final String KEY_SUFFIX_PROJECT_STATE = ".state";
	private static final String KEY_SUFFIX_PROJECT_BASEDIR = ".basedir";	
	
	private final File configFile;
	private final Map<String,String> configProperties = new HashMap<String,String>();

	public WorkspaceConfig(File configFile) throws IOException 
	{
		if (configFile == null) {
			throw new IllegalArgumentException("configFile must not be NULL");
		}
		this.configFile = configFile;
		loadConfig();
	}
	
	public List<File> getProjectsBaseDirectories() {
		List<File> result = new ArrayList<File> ();
		for ( String key : configProperties.keySet() ) {
			if ( key.startsWith( KEY_PROJECT ) && key.endsWith( KEY_SUFFIX_PROJECT_BASEDIR ) ) {
				result.add( new File( configProperties.get( key ) ) );
			}
		}
		return result;
	}
	
	public void projectAdded(IAssemblyProject project) 
	{
		final String key = KEY_PROJECT+project.getName()+KEY_SUFFIX_PROJECT_BASEDIR;
		configProperties.put( key , project.getConfiguration().getBaseDirectory().getAbsolutePath() );
	}
	
	public void projectDeleted(IAssemblyProject project) 
	{
		final String prefix = KEY_PROJECT+project.getName();
		for ( String key : new ArrayList<String>( configProperties.keySet() ) ) {
			if ( key.startsWith( prefix ) ) {
				configProperties.remove( key );
			}
		}
	}	
	
	public void projectOpened(IAssemblyProject project) 
	{
		configProperties.put( createProjectStateKey( project ) , Boolean.toString( true ) ); 
	}

	public void projectClosed(IAssemblyProject project) 
	{
		configProperties.put( createProjectStateKey( project ) , Boolean.toString( false ) ); 
	}
	
	private static String createProjectStateKey(IAssemblyProject project) {
		return createProjectStateKey( project.getName() );
	}	
	
	private static String createProjectStateKey(String projectName) {
		return KEY_PROJECT+projectName+KEY_SUFFIX_PROJECT_STATE;
	}
	
	public boolean isProjectOpen(String projectName) 
	{
		final String key = createProjectStateKey( projectName );
		String value = configProperties.get( key );
		if ( StringUtils.isBlank( value ) ) 
		{
			value = Boolean.toString(true);
		} 
		return Boolean.parseBoolean( value );
	}
	
	protected void loadConfig() throws IOException
	{
		boolean success = false;
		if ( configFile.exists() ) 
		{
			try {
				final Properties props = loadPropertiesFile( configFile );
				for ( String key : props.stringPropertyNames() ) {
					configProperties.put( key , props.getProperty( key ) );
				}
				success = true;
			} catch (IOException e) {
				LOG.error("loadConfiguration(): Failed to load workspace configuration");
			}
		}

		if ( ! success ) 
		{
			configProperties.clear();
			configProperties.putAll( createDefaultConfiguration() );
			saveConfiguration();
		}
	}

	protected Properties loadPropertiesFile(File configFIle) throws IOException 
	{
		final Properties props = new Properties();
		final InputStream in = new FileInputStream( configFile );
		try {
			props.load( in );
			return props;
		} finally {
			IOUtils.closeQuietly( in );
		}
	}

	protected Map<String,String> createDefaultConfiguration() throws IOException {

		final Map<String,String> result = new HashMap<String,String> ();
		return result;
	}

	public void saveConfiguration() throws IOException {

		final Properties props = new Properties();
		for ( Map.Entry<String,String> keyValue : configProperties.entrySet() ) {
			props.put( keyValue.getKey() , keyValue.getValue() );
		}

		final String comments =  "jASM16 workspace configuration -- automatically generated, do NOT edit";
		try {
			final FileOutputStream out = new FileOutputStream( configFile );
			try {
				props.store( out , comments );
			} finally {
				IOUtils.closeQuietly( out );
			}
		} catch (IOException e) {
			LOG.fatal("createDefaultConfiguration(): Failed to save configuration to "+
					configFile.getAbsolutePath(),e);
			throw e;
		}
	}

}
