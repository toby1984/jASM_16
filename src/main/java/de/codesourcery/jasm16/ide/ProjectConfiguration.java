package de.codesourcery.jasm16.ide;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

import de.codesourcery.jasm16.utils.Misc;

/**
 * XML file describing a JASM16 project's information like
 * source folders,output folder, project name etc.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ProjectConfiguration 
{
	private static final Logger LOG = Logger.getLogger(ProjectConfiguration.class);
	
	public static final String PROJECT_CONFIG_FILE = "jasm_project.xml";
	
	private final File baseDir;
	
	private final List<String> sourceFolders = new ArrayList<String>();
	
	private String outputFolder;
	private String projectName;

	/**
	 * Create instance.
	 * 
	 * @param baseDir the top-level directory of this project.
	 * @throws IOException
	 */
	public ProjectConfiguration(File baseDir) throws IOException 
	{
		this.baseDir = baseDir;
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
		
		final Element srcFolderNode = createElement("sourceFolders",document);
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
	 * </project>
	 */
	private void parseXML(Document doc) throws XPathExpressionException {

		final XPathFactory factory = XPathFactory.newInstance();
		final XPath xpath = factory.newXPath();

		final XPathExpression nameExpr = xpath.compile("/project/name");
		final XPathExpression outputFolderExpr = xpath.compile("/project/outputFolder");		
		final XPathExpression srcFoldersExpr = xpath.compile("/project/sourceFolders/sourceFolder");
		
		this.outputFolder = getValue( outputFolderExpr , doc );
		this.projectName = getValue( nameExpr , doc );
		this.sourceFolders.clear();
		this.sourceFolders.addAll( getValues( srcFoldersExpr , doc ) );
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
	
	private List<String> getValues(XPathExpression expr, Document doc) throws XPathExpressionException {
		
		final NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
		
		final List<String> result = new ArrayList<String>();
		for ( int i = 0 ; i < nodes.getLength() ; i++ ) {
			final Node node = nodes.item( i );
			final String value = node.getNodeValue().trim();
			if ( StringUtils.isBlank( value ) ) {
				LOG.error("getValues(): Invalid project XML - blank/empty value");
				throw new XPathExpressionException("Invalid project XML - blank/empty value");
			}
			result.add( value );
		}
		return result;
	}

	private DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
		final DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
		return fac.newDocumentBuilder();
	}
	
	private Document loadXML(File xmlFile) throws ParserConfigurationException, SAXException, IOException 
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
		
		if ( sourceFolders.isEmpty() ) {
			sourceFolders.add( "src" );
		}
		
		if ( outputFolder == null ) {
			outputFolder = "bin";
		}
		
		Misc.checkFileExistsAndIsDirectory( baseDir , true );
		for ( String src :  sourceFolders ) 
		{
			final File absPath = resolveRelativePath( src );
			Misc.checkFileExistsAndIsDirectory( absPath , true );
		}
		final File absOutputFolder = resolveRelativePath( outputFolder );
		Misc.checkFileExistsAndIsDirectory( absOutputFolder , true );
		save();
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
}