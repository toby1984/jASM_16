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
package de.codesourcery.jasm16.ide.ui.utils;

import java.awt.Dimension;
import java.awt.Point;

public class SizeAndLocation 
{
	private Point location;
	private Dimension dimension;
	
	public SizeAndLocation(Point location, Dimension dimension) {
		this.location = location;
		this.dimension = dimension;
	}
	
	public Point getLocation() {
		return location;
	}
	
	public Dimension getSize() {
		return dimension;
	}
	
	public void setSize(Dimension dimension) {
		if (dimension == null) {
			throw new IllegalArgumentException("dimension must not be null");
		}
		this.dimension = dimension;
	}
	
	public void setLocation(Point location) {
		if (location == null) {
			throw new IllegalArgumentException("location must not be null");
		}
		this.location = location;
	}
	
	@Override
	public String toString() {
		return "x="+location.x+",y="+location.y+",width="+dimension.width+",height="+dimension.height;
	}
	
}