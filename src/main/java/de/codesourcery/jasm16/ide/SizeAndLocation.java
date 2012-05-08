package de.codesourcery.jasm16.ide;

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