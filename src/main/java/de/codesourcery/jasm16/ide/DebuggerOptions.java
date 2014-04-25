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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import de.codesourcery.jasm16.Address;
import de.codesourcery.jasm16.emulator.Breakpoint;
import de.codesourcery.jasm16.emulator.OneShotBreakpoint;
import de.codesourcery.jasm16.exceptions.ParseException;

/**
 * Per-project debugger options.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class DebuggerOptions 
{
	public static final Logger LOG = Logger.getLogger(DebuggerOptions.class);
	
	private final Map<Address,Breakpoint> breakpoints=new HashMap<>();
	
	public DebuggerOptions() {
	}
	
	public void reset() {
		breakpoints.clear();
	}
	
	public void addBreakpoint(Breakpoint bp) 
	{
		if (bp == null) {
			throw new IllegalArgumentException("bp must not be NULL");
		}
		// one-shot BPs are for internal use only
		if ( !(bp instanceof OneShotBreakpoint) ) {
			this.breakpoints.put(bp.getAddress(),bp);
		}
	}
	
	public void deleteBreakpoint(Breakpoint bp) {
		if (bp == null) {
			throw new IllegalArgumentException("bp must not be NULL");
		}
		// one-shot BPs are for internal use only		
		if ( !(bp instanceof OneShotBreakpoint) ) {		
			this.breakpoints.remove(bp.getAddress());
		}
	}	
	
	public void breakpointChanged(Breakpoint bp) {
		if (bp == null) {
			throw new IllegalArgumentException("bp must not be NULL");
		}
		// one-shot BPs are for internal use only		
		if ( !(bp instanceof OneShotBreakpoint) && this.breakpoints.containsKey( bp.getAddress() ) ) 
		{		
			this.breakpoints.put(bp.getAddress(),bp);
		}
	}
	
	public void saveDebuggerOptions(Element parentNode,Document doc) {
		
		Element root = doc.createElement("breakpoints");
		parentNode.appendChild(root);
		
		for ( Breakpoint bp : breakpoints.values() ) 
		{
			Element bpNode = doc.createElement("breakpoint");
			root.appendChild( bpNode );
			bpNode.setAttribute("enabled", bp.isEnabled() ? "true" : "false" );
			bpNode.setAttribute( "address", Integer.toString( bp.getAddress().toWordAddress().getValue() ) );
			if ( bp.hasCondition() ) {
				bpNode.setAttribute( "condition", bp.getCondition() );
			}
		}
	}
	
	public void loadDebuggerOptions(Element parentNode) 
	{
		reset();
		
		NodeList list = parentNode.getElementsByTagName("breakpoints");
		if ( list.getLength() == 1 ) 
		{
			final NodeList children = list.item(0).getChildNodes();
			final int len = children.getLength();
			for ( int i = 0 ; i < len ; i++ ) 
			{
				Element bp = (Element) children.item(i);
				
				final Address address = Address.wordAddress( Integer.parseInt( bp.getAttribute( "address" ) ) );
				
				final Breakpoint newBp = new Breakpoint( address );
				final String cond = bp.getAttribute("condition");
				if ( StringUtils.isNotBlank( cond ) ) 
				{
					try {
						newBp.setCondition( cond );
					} 
					catch (ParseException e) {
						LOG.error("populateFromXml(): Breakpoint at "+address+" has unparseable condition >"+cond+"<");
						e.printStackTrace();
						continue;
					}
				}
				newBp.setEnabled( "true".equalsIgnoreCase( bp.getAttribute("enabled") ) );
				addBreakpoint( newBp );
			}
		}
	}

	public List<Breakpoint> getBreakpoints() {
		return new ArrayList<>( this.breakpoints.values() );
	}
}