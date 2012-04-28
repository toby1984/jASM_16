package de.codesourcery.jasm16.ide;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.utils.Misc;

public class ApplicationConfig implements IApplicationConfig {

	private static final Logger LOG = Logger.getLogger(ApplicationConfig.class);

	public static final String FILE_NAME = ".jasm_workspace.properties";

	private static final String KEY_WORKSPACE_DIRECTORY = "workspace.dir";

	private final File configFile;
	private final Map<String,String> configProperties = new HashMap<String,String>();
	private final AtomicBoolean configLoaded = new AtomicBoolean( false );

	public ApplicationConfig(File configFile) 
	{
		if (configFile == null) {
			throw new IllegalArgumentException("configFile must not be NULL");
		}
		this.configFile = configFile;
	}

	@Override
	public File getWorkspaceDirectory() 
	{
		loadConfiguration();
		return getFile( KEY_WORKSPACE_DIRECTORY );
	}

	protected File getFile(String key) {
		return new File( configProperties.get( key ) );
	}

	protected void loadConfiguration() {

		if ( configLoaded.compareAndSet( false , true ) )
		{
			configProperties.clear();
			
			boolean success = false;
			if ( configFile.exists() ) {

				final Properties props = new Properties();
				try {
					final InputStream in = new FileInputStream( configFile );
					try {
						props.load( in );
						for ( String key : props.stringPropertyNames() ) {
							configProperties.put( key , props.getProperty( key ) );
						}
						success = true;
					} finally {
						IOUtils.closeQuietly( in );
					}
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
	}

	protected Map<String,String> createDefaultConfiguration() {

		final Map<String,String> result = new HashMap<String,String> ();

		final File homeDirectory = new File( Misc.getUserHomeDirectory() , "jasm_workspace" );
		result.put( KEY_WORKSPACE_DIRECTORY , homeDirectory.getAbsolutePath() );
		return result;
	}

	@Override
	public void saveConfiguration() {

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
		}
	}

	@Override
	public void setWorkspaceDirectory(File dir) throws IOException 
	{
		if (dir == null) {
			throw new IllegalArgumentException("dir must not be NULL");
		}
		loadConfiguration();
		Misc.checkFileExistsAndIsDirectory( dir , true );
		this.configProperties.put( KEY_WORKSPACE_DIRECTORY , dir.getAbsolutePath() );
		saveConfiguration();
	}

}
