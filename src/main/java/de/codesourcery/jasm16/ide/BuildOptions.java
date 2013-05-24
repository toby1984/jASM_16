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
     * - loadBuildOptions()
     * - saveBuildOptions()
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
    private boolean generateSelfRelocatingCode = false;
    private boolean inlineShortLiterals = true;    

    public BuildOptions() {
    }
    
    public BuildOptions(BuildOptions other) {
        this.generateSelfRelocatingCode = other.generateSelfRelocatingCode;
        this.inlineShortLiterals = other.inlineShortLiterals;
    }

    public void saveBuildOptions(Element element,Document document) 
    {
        if ( generateSelfRelocatingCode ) {
            element.setAttribute("generateSelfRelocatingCode" , "true" );
        }
        if ( inlineShortLiterals ) {
            element.setAttribute("inlineShortLiterals" , "true" );            
        }
    }
    
    public boolean isInlineShortLiterals()
    {
        return inlineShortLiterals;
    }
    
    public void setInlineShortLiterals(boolean inlineShortLiterals)
    {
        this.inlineShortLiterals = inlineShortLiterals;
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
        result.generateSelfRelocatingCode = isSet(element,"generateSelfRelocatingCode" , false );
        result.inlineShortLiterals = isSet(element,"inlineShortLiterals" , true );        
        return result;
    }   
    
    @SuppressWarnings("unused")
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

    private static boolean isSet(Element element,String attribute,boolean defaultValue) {
        final String value = element.getAttribute(attribute);
        return "true".equals( value );
    }
}