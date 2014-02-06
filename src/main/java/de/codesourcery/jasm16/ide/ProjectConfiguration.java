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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.codesourcery.jasm16.emulator.EmulationOptions;
import de.codesourcery.jasm16.emulator.IEmulationOptionsProvider;
import de.codesourcery.jasm16.utils.Misc;

/**
 * XML file describing a JASM16 project's information like
 * source folders,output folder, project name etc.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ProjectConfiguration implements IEmulationOptionsProvider
{
    public static final Set<String> DEFAULT_SOURCEFILENAME_PATTERNS = new HashSet<>(
            Arrays.asList(".*?\\.dasm",".*?\\.dasm16",".*?\\.asm")
    );
    
	public static final String DEFAULT_OUTPUT_FOLDER = "bin";

    public static final String DEFAULT_SOURCE_FOLDER = "src";

    private static final Logger LOG = Logger.getLogger(ProjectConfiguration.class);

	public static final String PROJECT_CONFIG_FILE = "jasm_project.xml";

	public static final String DEFAULT_EXECUTABLE_NAME = "a.out";

	private final File baseDir;

	private final List<String> sourceFolders = new ArrayList<String>();

	private String outputFolder;
	private String projectName;
	private String executableName;
	
	private String compilationRoot; // path relative to project basedir, points to source file that should be compiled first (and that will
	// include all other files that need to be compiled) 
	
	private final Set<String> sourceFilenamePatterns = new HashSet<>();
	private final List<Pattern> sourceFilenameRegexPatterns = new ArrayList<>();
	
	private BuildOptions buildOptions = new BuildOptions();
	private EmulationOptions emulationOptions=new EmulationOptions();
	private final DebuggerOptions debuggerOptions = new DebuggerOptions();

	public void populateFrom(ProjectConfiguration other) {
		if ( ! this.baseDir.getAbsolutePath().equals( other.baseDir.getAbsolutePath() ) ) {
			throw new IllegalArgumentException("Project base directories do not match.");
		}
		this.sourceFolders.clear();
		this.sourceFolders.addAll(other.sourceFolders);
		this.outputFolder = other.outputFolder;
		this.projectName = other.projectName;
		this.executableName = other.executableName;
		this.compilationRoot = other.compilationRoot;
		this.setSourceFilenamePatterns( other.sourceFilenamePatterns );
		setBuildOptions( other.getBuildOptions() );
		setEmulationOptions( other.getEmulationOptions() );
	}
	
	/**
	 * Create instance.
	 * 
	 * @param baseDir the top-level directory of this project.
	 * @throws IOException
	 */
	public ProjectConfiguration(File baseDir) throws IOException 
	{
		this.baseDir = baseDir;
		setSourceFilenamePatterns( DEFAULT_SOURCEFILENAME_PATTERNS );
	}
	
    public EmulationOptions getEmulationOptions() {
    	return new EmulationOptions(emulationOptions);
    }
    
    public void setEmulationOptions(EmulationOptions emulationOptions) 
    {
    	if (emulationOptions == null) {
			throw new IllegalArgumentException(
					"emulationOptions must not be null");
		}
		this.emulationOptions = new EmulationOptions( emulationOptions );
	}	

	public String getExecutableName() {
		return executableName;
	}

	public void setExecutableName(String executableName) {

		if (StringUtils.isBlank(executableName)) {
			throw new IllegalArgumentException(
					"executableName must not be NULL/blank");
		}

		this.executableName = executableName;
	}

	/**
	 * (Re-)populates this project configuration instance from
	 * it's XML file.
	 *  
	 * @throws IOException
	 */
	public void load() throws IOException 
	{
		final File xmlFile = resolveRelativePath( PROJECT_CONFIG_FILE );
		if ( ! xmlFile.exists() ) {
			LOG.error("load(): File "+xmlFile.getAbsolutePath()+" does not exist?");
			throw new IOException("File "+xmlFile.getAbsolutePath()+" does not exist?");
		}

		LOG.info("load(): Loading project configuration from "+xmlFile.getAbsolutePath());

		final Document doc;
		try {
			doc = loadXML(xmlFile);
		} 
		catch (Exception e) 
		{
			LOG.error("Failed to load project description from "+
					xmlFile.getAbsolutePath() );
			throw new IOException("Failed to load project description from "+
					xmlFile.getAbsolutePath(),e);
		}

		try {
			parseXML( doc );
		} 
		catch (Exception e) 
		{
			throw new IOException("Failed to load project configuration",e);
		}
	}

	/**
	 * Stores this project's configuration as an XML file.
	 * 
	 * @throws IOException
	 */
	public void save() throws IOException {

		if ( ! baseDir.exists() ) {
			LOG.error("save(): Project base directory "+baseDir.getAbsolutePath()+" does not exist ?");
			throw new IOException("Project base directory "+baseDir.getAbsolutePath()+" does not exist ?");
		}
		Document document;
		try {
			document = createDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			LOG.error("Failed to save project configuration",e);
			throw new IOException("Failed to save project configuration",e);
		}

		final Element root = document.createElement("project");
		document.appendChild( root );

		root.appendChild( createElement("name" , projectName , document ) );
		root.appendChild( createElement("outputFolder" , outputFolder , document ) );
		root.appendChild( createElement("executableName" , executableName , document ) );		
		
		// compilation root
		if ( compilationRoot != null ) {
		    root.appendChild( createElement("compilationRoot" , compilationRoot , document ) );
		}
		
		// source filename patterns
		final Element srcFilePatterns = createElement("sourceFilenamePatterns",document);
		root.appendChild( srcFilePatterns );
		
		for ( String pat : sourceFilenamePatterns ) {
		    srcFilePatterns.appendChild( createElement("sourceFilenamePattern" , pat , document ) );
		}
		
		// build options
	    final Element buildOptions = document.createElement("buildOptions");
	    root.appendChild( buildOptions );
	    
	    this.buildOptions.saveBuildOptions( buildOptions , document );
	    
	    // debugger options
	    final Element debugOptions = document.createElement("debuggerOptions");
	    root.appendChild( debugOptions );
	    
	    this.debuggerOptions.saveDebuggerOptions(debugOptions,document);
		
		// emulation options
		final Element options = document.createElement("emulationOptions");
		this.emulationOptions.saveEmulationOptions( options , document );
		root.appendChild( options );

		final Element srcFolderNode = createElement("sourceFolders",document);
		root.appendChild( srcFolderNode );

		for ( String folder : sourceFolders ) {
			srcFolderNode.appendChild( createElement("sourceFolder" , folder , document ) );
		}

		try {
			writeXML( document , resolveRelativePath( PROJECT_CONFIG_FILE ) );
		} catch (Exception e) {
			LOG.error("Failed to save project configuration",e);
			throw new IOException("Failed to save project configuration",e);
		}
	}
	
	private void writeXML(Document doc,File file) throws TransformerFactoryConfigurationError, TransformerException 
	{
		final Source source = new DOMSource(doc);
		final Result result = new StreamResult(file);
		final Transformer xformer = TransformerFactory.newInstance().newTransformer();
		xformer.transform(source, result);		
	}

	private Element createElement(String tagName,String value,Document doc) 
	{
		Element result = createElement( tagName , doc );
		result.appendChild( doc.createTextNode( value ) );
		return result;
	}

	private Element createElement(String tagName,Document doc) {
		return doc.createElement( tagName );
	}	

	/*
	 * <project>
	 *   <name>myProject</name>
	 *   <sourceFolders>
	 *     <sourceFolder>src</sourceFolder>
	 *   </sourceFolders>
	 *   <outputFolder>bin</outputFolder>
	 *   <executableName>a.out</executableName>
	 * </project>
	 */
	private void parseXML(Document doc) throws XPathExpressionException 
	{
		final XPathFactory factory = XPathFactory.newInstance();
		final XPath xpath = factory.newXPath();

		final XPathExpression nameExpr = xpath.compile("/project/name");
		final XPathExpression outputFolderExpr = xpath.compile("/project/outputFolder");
		final XPathExpression executableNameExpr = xpath.compile("/project/executableName");
		final XPathExpression srcFoldersExpr = xpath.compile("/project/sourceFolders/sourceFolder");
		final XPathExpression emulationOptionsExpr = xpath.compile("/project/emulationOptions");
        final XPathExpression buildOptionsExpr = xpath.compile("/project/buildOptions");   
        final XPathExpression srcFilePatternsExpr = xpath.compile("/project/sourceFilenamePatterns/sourceFilenamePattern");           
        final XPathExpression compilationRootExpr = xpath.compile("/project/compilationRoot");
        final XPathExpression debuggerOptionsExpr = xpath.compile("/project/debuggerOptions");
        
		this.outputFolder = getValue( outputFolderExpr , doc );
		this.projectName = getValue( nameExpr , doc );
		this.executableName = getValue( executableNameExpr , doc );
		this.sourceFolders.clear();
		this.sourceFolders.addAll( getValues( srcFoldersExpr , doc ) );
		
		// compilation root
        final List<String> roots = getValues( compilationRootExpr , doc );
        if ( ! roots.isEmpty() ) 
        {
            if ( roots.size() > 1 ) {
                throw new RuntimeException("Parse error, more than one compilation root in project config XML ?");
            }
            setCompilationRoot( new File( roots.get(0) ) );
        }		
        
		// parse srcfile name patterns
		final List<String> patterns = getValues( srcFilePatternsExpr , doc );
		if ( ! patterns.isEmpty() ) {
		    setSourceFilenamePatterns( new HashSet<>(patterns) );
		}
		
		// parse emulation options
		Element element = getElement(emulationOptionsExpr,doc);
		if ( element == null ) {
			this.emulationOptions = new EmulationOptions();
		} else {
			this.emulationOptions = EmulationOptions.loadEmulationOptions( element );
		}
		
		// parse build options
        element = getElement(buildOptionsExpr,doc);
        if ( element == null ) {
            this.buildOptions = new BuildOptions();
        } else {
            this.buildOptions = BuildOptions.loadBuildOptions( element );
        }
        
		// parse build options
        element = getElement(debuggerOptionsExpr,doc);
        if ( element == null ) {
            this.debuggerOptions.reset();
        } else {
            this.debuggerOptions.loadDebuggerOptions( element );
        }	        
	}

	private String getValue(XPathExpression expr, Document doc) throws XPathExpressionException 
	{
		List<String> values = getValues( expr , doc );
		if ( values.isEmpty() ) {
			throw new XPathExpressionException("Project XML lacks node matching "+expr);
		}
		if ( values.size() != 1 ) {
			throw new XPathExpressionException("Project XML contains more than one node matching "+expr);
		}
		return values.get(0);
	}
	
	private Element getElement(XPathExpression expr,Document doc) throws XPathExpressionException 
	{
		return (Element) expr.evaluate(doc, XPathConstants.NODE);
	} 

	private List<String> getValues(XPathExpression expr, Document doc) throws XPathExpressionException {

		final NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

		final List<String> result = new ArrayList<String>();
		for ( int i = 0 ; i < nodes.getLength() ; i++ ) {
			final Node node = nodes.item( i );
			final String value = node.getTextContent();
			if ( value == null || StringUtils.isBlank( value ) ) {
				LOG.error("getValues(): Invalid project XML - blank/empty value");
				throw new XPathExpressionException("Invalid project XML - blank/empty value for "+
						" expression "+expr);
			}
			result.add( value.trim() );
		}
		return result;
	}

	protected DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
		final DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
		return fac.newDocumentBuilder();
	}

	protected Document loadXML(File xmlFile) throws ParserConfigurationException, SAXException, IOException 
	{
		return createDocumentBuilder().parse( xmlFile );
	}

	/**
	 * Creates this project's base folder along with all 
	 * source folders and output folder and saves the configuration
	 * to XML file {@link #PROJECT_CONFIG_FILE}.
	 * 
	 * <p>
	 * If this project config has no source and/or output folder 
	 * set, the source folder will be set to 'src' and the output
	 * folder will be set to 'bin'.
	 * </p>
	 * @throws IOException
	 */
	public void create() throws IOException 
	{
		if ( StringUtils.isBlank( this.projectName ) ) {
			LOG.error("create(): Cannot create project without a name");
			throw new IllegalStateException("Cannot create project without a name");
		}

		if ( sourceFolders.isEmpty() ) 
		{
			sourceFolders.add( DEFAULT_SOURCE_FOLDER );
		}

		if ( outputFolder == null ) {
			outputFolder = DEFAULT_OUTPUT_FOLDER;
		}

		if ( executableName == null ) {
			executableName = DEFAULT_EXECUTABLE_NAME;
		}

		// used to keep track of created folders so we're 
		// able to remove them if something goes wrong on the way...
		final List<File> createdFolders = new ArrayList<File>();

		// create / check base directory
		if ( Misc.checkFileExistsAndIsDirectory( baseDir , true ) ) {
			createdFolders.add( baseDir );
		}
		try 
		{	
			// create source folders
			for ( String src :  sourceFolders ) 
			{
				final File absPath = resolveRelativePath( src );
				if ( Misc.checkFileExistsAndIsDirectory( absPath , true ) ) 
				{
					createdFolders.add( absPath );
				}
			}

			// create output folder
			final File absOutputFolder = resolveRelativePath( outputFolder ); // note: creates missing directory
			if ( Misc.checkFileExistsAndIsDirectory( absOutputFolder , true ) ) {
				createdFolders.add( absOutputFolder );
			}

			save();
		} 
		catch(IOException e) 
		{
			for ( File folder : createdFolders ) 
			{
				Misc.deleteRecursively( folder );
			}
			throw e;
		}
	}

	private File resolveRelativePath(String path) {
		return new File( baseDir , path );
	}

	/**
	 * Returns absolute locations of source folders of this project.
	 * 
	 * @return
	 */
	public List<File> getSourceFolders() {
		final List<File> result = new ArrayList<File>();
		for ( String srcFolder : sourceFolders ) {
			result.add( resolveRelativePath( srcFolder ) );
		}
		return result;
	}

	/**
	 * Adds a source folder.
	 * 
	 * @param file
	 */
	public void addSourceFolder(File file) 
	{
		if (file == null) {
			throw new IllegalArgumentException("file must not be NULL");
		}
		if ( ! sourceFolders.contains( file.getName() ) ) {
			sourceFolders.add( file.getName() );
		}
	}

	/**
	 * Set's this project's name.
	 * 
	 * @param projectName
	 */
	public void setProjectName(String projectName) {

		if (StringUtils.isBlank(projectName)) {
			throw new IllegalArgumentException(
					"projectName must not be NULL/blank");
		}
		this.projectName = projectName;
	}

	/**
	 * Returns this project's name.
	 * 
	 * @return
	 */
	public String getProjectName() {
		return projectName;
	}

	/**
	 * Sets this project's binary output folder.
	 * 
	 * @param outputFolder
	 */
	public void setOutputFolder(File outputFolder) {
		if (outputFolder == null) {
			throw new IllegalArgumentException("outputFolder must not be NULL");
		}
		this.outputFolder = outputFolder.getName();
	}

	/**
	 * Returns this project's binary output folder.
	 * 
	 * @return
	 */
	public File getOutputFolder() {
		return resolveRelativePath( outputFolder );
	}

	public File getBaseDirectory() {
		return baseDir;
	}
	
	public DebuggerOptions getDebuggerOptions() {
		return this.debuggerOptions;
	}

    public BuildOptions getBuildOptions()
    {
        return new BuildOptions( buildOptions );
    }
    
    public void setBuildOptions(BuildOptions buildOptions)
    {
        this.buildOptions = new BuildOptions( buildOptions );
    }
    
    public static boolean isProjectConfigurationFile(File file) {
        return file.isFile() && ProjectConfiguration.PROJECT_CONFIG_FILE.equals( file.getName() );
    }
    
    public Set<String> getSourceFilenamePatterns()
    {
        return sourceFilenamePatterns;
    }
    
    public void setSourceFilenamePatterns(Set<String> patterns)
    {
        final List<Pattern> newPatterns = new ArrayList<>();
        for ( String pat : patterns ) {
            newPatterns.add( Pattern.compile( pat ) );
        }
        this.sourceFilenamePatterns.clear();
        this.sourceFilenameRegexPatterns.clear();
        
        this.sourceFilenamePatterns.addAll( patterns );
        this.sourceFilenameRegexPatterns.addAll( newPatterns );
    }
    
    public boolean isSourceFile(File file) 
    {
    	if ( ! file.isFile() ) {
    		return false;
    	} 
    	final String fileName = file.getName() ;
    	for ( Pattern p : sourceFilenameRegexPatterns ) {
    		if ( p.matcher( fileName ).matches() ) {
    			return true;
    		}
    	}
    	return false;
    }
    
    /**
     * Returns this project's compilation root.
     * @return
     */
    public File getCompilationRoot()
    {
        return ( compilationRoot == null ) ? null : new File(getBaseDirectory() , compilationRoot );
    }
    
    private static boolean isRelativePath(File f) {
        return ! f.getPath().startsWith( File.separator );
    }
    
    public void setCompilationRoot(File compilationRoot)
    {
        if ( compilationRoot == null ) 
        {
            this.compilationRoot = null;
            return;
        }
        
        if ( ! isRelativePath( compilationRoot ) && ! compilationRoot.getAbsolutePath().startsWith( getBaseDirectory().getAbsolutePath() ) ) 
        {
            throw new IllegalArgumentException("File "+compilationRoot.getAbsolutePath()+" is not inside this project's folder "+getBaseDirectory().getAbsolutePath());
        }
        
        // convert to path relative to base dir
        String stripped = compilationRoot.getPath(); 
        if ( ! isRelativePath( compilationRoot ) ) {
            stripped =compilationRoot.getAbsolutePath().substring( getBaseDirectory().getAbsolutePath().length() );
            while  ( stripped.startsWith(File.separator ) ) {
                stripped = stripped.substring(1);
            }
        }
        this.compilationRoot = stripped;
    }
}