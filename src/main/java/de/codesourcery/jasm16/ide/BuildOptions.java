package de.codesourcery.jasm16.ide;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Project build options.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class BuildOptions {

    /* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * Adjust the following locations when
     * adding/removing configuration options:
     * 
     * - copy constructor
     * - loadEmulationOptions()
     * - saveEmulationOptions()
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
    private boolean generateSelfRelocatingCode = false;

    public BuildOptions() {
    }
    
    public BuildOptions(BuildOptions other) {
        this.generateSelfRelocatingCode = other.generateSelfRelocatingCode;
    }

    public void saveBuildOptions(Element element,Document document) 
    {
        if ( generateSelfRelocatingCode ) {
            element.setAttribute("generateSelfRelocatingCode" , "true" );
        }
    }
    
    public void setGenerateSelfRelocatingCode(boolean generateSelfRelocatingCode)
    {
        this.generateSelfRelocatingCode = generateSelfRelocatingCode;
    }
    
    public boolean isGenerateSelfRelocatingCode()
    {
        return generateSelfRelocatingCode;
    }

    public static BuildOptions loadBuildOptions(Element element) 
    {
        final BuildOptions result = new BuildOptions();
        result.generateSelfRelocatingCode = isSet(element,"generateSelfRelocatingCode" );
        return result;
    }   
    
    private static Element getChildElement(Element parent,String tagName) 
    {
        final NodeList nodeList = parent.getElementsByTagName( tagName );
        if ( nodeList.getLength() == 1 ) 
        {
            return (Element) nodeList.item(0);
        } 
        if ( nodeList.getLength() > 1 ) {
            throw new RuntimeException("Parse error, more than one <disks/> node in file?");
        }
        return null;
    }

    private static boolean isSet(Element element,String attribute) {
        final String value = element.getAttribute(attribute);
        return "true".equals( value );
    }
}