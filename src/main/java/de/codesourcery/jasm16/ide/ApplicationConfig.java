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
package de.codesourcery.jasm16.ide;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.ide.ui.utils.SizeAndLocation;
import de.codesourcery.jasm16.utils.Misc;

/**
 * Global IDE configuration.
 * 
 * <p>Currently , the IDE configuration only holds the path
 * to the current's workspace base directory and is stored 
 * as file {@link #FILE_NAME} in the user's home directory.</p>
 *   
 * @author tobias.gierke@code-sourcery.de
 */
public class ApplicationConfig implements IApplicationConfig {

	private static final Logger LOG = Logger.getLogger(ApplicationConfig.class);

	public static final String FILE_NAME = ".jasm_workspace.properties";

	protected static final String KEY_VIEW_COORDINATES_PREFIX = "view.";
	
	protected static final String KEY_WORKSPACE_DIRECTORY = "workspace.dir";

	private final File configFile;
	private final Map<String,String> configProperties = new HashMap<String,String>();

	public ApplicationConfig(File configFile) throws IOException 
	{
		if (configFile == null) {
			throw new IllegalArgumentException("configFile must not be NULL");
		}
		this.configFile = configFile;
		loadConfig();
	}

	@Override
	public File getWorkspaceDirectory() 
	{
		return getFile( KEY_WORKSPACE_DIRECTORY );
	}

	protected File getFile(String key) {
		return new File( configProperties.get( key ) );
	}

	protected void loadConfig() throws IOException
	{
		boolean success = false;
		if ( configFile.exists() ) 
		{
			LOG.info("loadConfig(): Loading application configuration");
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

		final File homeDirectory = new File( Misc.getUserHomeDirectory() , "jasm_workspace" );
		if ( ! homeDirectory.exists() ) 
		{
			LOG.info("createDefaultConfiguration(): Creating workspace folder "+homeDirectory.getAbsolutePath());
			if ( ! homeDirectory.mkdirs() ) {
				LOG.error("createDefaultConfiguration(): Unable to create workspace directory "+homeDirectory.getAbsolutePath());
				throw new IOException("Unable to create workspace directory "+homeDirectory.getAbsolutePath());
			}
		}
		if ( ! homeDirectory.isDirectory() ) {
			LOG.error("createDefaultConfiguration(): Workspace directory is no directory: "+homeDirectory.getAbsolutePath());
			throw new IOException("Workspace directory is no directory: "+homeDirectory.getAbsolutePath());
		}
		result.put( KEY_WORKSPACE_DIRECTORY , homeDirectory.getAbsolutePath() );
		return result;
	}

	@Override
	public void saveConfiguration() throws IOException {

		final Properties props = new Properties();
		
		LOG.info("saveConfiguration(): Saving application config.");
		
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

	@Override
	public void setWorkspaceDirectory(File dir) throws IOException 
	{
		if (dir == null) {
			throw new IllegalArgumentException("dir must not be NULL");
		}
		Misc.checkFileExistsAndIsDirectory( dir , true );
		this.configProperties.put( KEY_WORKSPACE_DIRECTORY , dir.getAbsolutePath() );
		saveConfiguration();
	}

	@Override
	public void storeViewCoordinates(String viewID, SizeAndLocation loc) 
	{
		if (StringUtils.isBlank(viewID)) {
			throw new IllegalArgumentException(
					"viewID must not be blank/null");
		}
		
		if ( loc == null ) {
			throw new IllegalArgumentException("loc must not be null");
		}
		
		final String posKey =  KEY_VIEW_COORDINATES_PREFIX+viewID+".position";
		final String sizeKey =  KEY_VIEW_COORDINATES_PREFIX+viewID+".size";
		
		final String posValue = loc.getLocation().x+","+loc.getLocation().y;
		final String sizeValue = loc.getSize().width+","+loc.getSize().height;
		this.configProperties.put( posKey , posValue );
		this.configProperties.put( sizeKey , sizeValue );
	}

	@Override
	public SizeAndLocation getViewCoordinates(String viewId) 
	{
		final String posKey =  KEY_VIEW_COORDINATES_PREFIX+viewId+".position";
		final String sizeKey =  KEY_VIEW_COORDINATES_PREFIX+viewId+".size";
		
		final String posValue = this.configProperties.get( posKey );
		final String sizeValue = this.configProperties.get( sizeKey );
		
		if ( StringUtils.isBlank( posValue ) || StringUtils.isBlank( sizeValue ) ) {
			return null;
		}
		
		String[] parts = posValue.split(",");
		final int x = Integer.parseInt( parts[0] );
		final int y = Integer.parseInt( parts[1] );
		
		parts = sizeValue.split(",");
		final int width = Integer.parseInt( parts[0] );
		final int height = Integer.parseInt( parts[1] );
		
		return new SizeAndLocation( new Point( x , y ) , new Dimension( width , height ) );
	}

}
