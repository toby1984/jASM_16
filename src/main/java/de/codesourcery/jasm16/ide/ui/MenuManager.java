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
package de.codesourcery.jasm16.ide.ui;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

public abstract class MenuManager 
{
	// @GuardedBy( entries )
	private final List<MenuEntry> entriesList = new ArrayList<MenuEntry>();

	private final Object menuBarLock  = new Object();

	// @GuardedBy( menuBarLock )
	private JMenuBar menuBar;
	// @GuardedBy( menuBarLock )	
	private ItemWatchdog watchdogThread;	
	
	private static final class MenuPath 
	{
		private final String[] path;

		public MenuPath(String path) {
			this.path = path.split("/");
		}

		public int length() {
			return path.length;
		}

		public MenuPath[] getAllPaths() 
		{
			final List<MenuPath> result = new ArrayList<MenuPath>();
			for ( int len = 1 ; len <= path.length ; len++ ) {
				result.add( new MenuPath( (String[]) ArrayUtils.subarray( this.path , 0 , len ) ) );
			}

			return result.toArray( new MenuPath[ result.size() ] );
		}		

		@Override
		public int hashCode() {
			return toString().hashCode();
		}

		@Override
		public boolean equals(Object obj) 
		{
			if ( obj == this ) {
				return true;
			}
			if ( obj instanceof MenuPath) {
				MenuPath that = (MenuPath) obj;
				if ( this.path.length != that.path.length ) {
					return false;
				}
				for ( int i = 0 ; i < this.path.length ; i++ ) {
					if ( ! this.path[i].equals( that.path[i] ) ) {
						return false;
					}
				}
				return true;
			}
			return false;
		}

		public MenuPath(String[] path) {
			this.path = path;
		}

		public String getLastPathComponent() {
			return path[ path.length - 1 ];
		}

		public String toString() {
			return StringUtils.join( path , "/" );
		}

		public MenuPath getParentPath() 
		{
			if ( path.length == 1 ) {
				return null;
			}
			return new MenuPath( (String[]) ArrayUtils.subarray( path , 0 , path.length - 1 ) ); 
		}
	}

	public static abstract class MenuEntry {

		private final MenuPath path;
		private final int mnemonic;
		private JMenuItem menuItem;

		public MenuEntry(String path) {
			this( path , Integer.MAX_VALUE );
		}

		public JMenuItem getMenuItem() {
			return menuItem;
		}

		public void setMenuItem(JMenuItem menuItem) {
			if (menuItem == null) {
				throw new IllegalArgumentException("menuItem must not be null");
			}
			this.menuItem = menuItem;
		}

		public MenuEntry(String path,int mnemonic) {
			this.path = new MenuPath( path );
			if ( this.path.length() == 1 ) {
				throw new IllegalArgumentException("Path is too short: "+path);
			}
			this.mnemonic = mnemonic;
		}

		public boolean hasMnemonic() {
			return mnemonic != Integer.MAX_VALUE;
		}
		public int getMnemonic() {
			return mnemonic;
		}

		public MenuPath getPath() {
			return path;
		}

		public String getParentPath() {
			MenuPath parentPath = path.getParentPath();
			return parentPath == null ? "" : parentPath.toString();
		}

		public String getLabel() {
			return path.getLastPathComponent();
		}

		public boolean isVisible() {
			return true;
		}

		public boolean isEnabled() {
			return true;
		}

		public abstract void onClick();
	}

	public void addEntry(MenuEntry entry) 
	{
		if (entry == null) {
			throw new IllegalArgumentException("entry must not be null");
		}
		synchronized( entriesList ) {
			this.entriesList.add( entry );
		}
		
		notifyMenuBarChanged();
	}
	
	private void notifyMenuBarChanged() 
	{
		final boolean notifyChange;
		synchronized( menuBarLock ) 
		{
			if ( menuBar != null ) {
				menuBar = null;
				notifyChange = true;
			} else {
				notifyChange = false;
			}
		}
		
		if ( notifyChange ) 
		{
			System.out.println("Menu bar has changed!");
			menuBarChanged();
		}
	}

	public void removeEntry(MenuEntry entry) {
		if (entry == null) {
			throw new IllegalArgumentException("entry must not be null");
		}
		synchronized( entriesList ) 
		{
			if ( this.entriesList.remove( entry ) == false ) {
				return;
			}
		}

		notifyMenuBarChanged();
	}

	public JMenuBar getMenuBar() 
	{
		synchronized( menuBarLock ) 
		{
			if ( menuBar == null ) 
			{
				menuBar = createMenuBar();
				if ( watchdogThread == null || ! watchdogThread.isAlive() ) 
				{
					watchdogThread = new ItemWatchdog();
					watchdogThread.start();
				} else {
					watchdogThread.clearCache();
				}
			}
			return menuBar;
		}
	}
	
	private class ItemWatchdog extends Thread {

		// @GuardedBy( stateCache )
		private final IdentityHashMap<MenuEntry, Boolean> stateCache = 
				new IdentityHashMap<MenuEntry, Boolean>();

		public ItemWatchdog() {
			setDaemon( true );
			setName("menuitem-watchdog-thread");			
		}
		
		public void clearCache() {
			synchronized(stateCache) {
				stateCache.clear();
			}
		}
		
		@Override
		public void run() 
		{
			while( true ) 
			{
				try { Thread.sleep( 500 ); } 
				catch (InterruptedException e) { }
				
				synchronized( menuBarLock ) 
				{
					if ( menuBar == null ) {
						continue;
					}
				}

				final List<MenuEntry> entriesCopy;
				synchronized( entriesList ) {
					entriesCopy = new ArrayList<MenuEntry>( entriesList );
				}						
				
				boolean cacheModified = false;
				final Map<MenuEntry, Boolean> cacheCopy;
				synchronized( stateCache ) {
					cacheCopy = new IdentityHashMap<MenuEntry, Boolean>( this.stateCache );
				}
				
				for ( final MenuEntry entry : entriesCopy ) 
				{
					final Boolean currentState = Boolean.valueOf( entry.isEnabled() );							
					final Boolean oldState = cacheCopy.get( entry );
					if ( oldState == null ) {
						cacheCopy.put( entry , currentState );
						cacheModified = true;
					} 
					else 
					{
						if ( ! oldState.equals( currentState ) ) 
						{
							cacheCopy.put( entry , currentState );									
							cacheModified = true;
							SwingUtilities.invokeLater( new Runnable() {

								@Override
								public void run() 
								{
									System.out.println("Item state changed: "+entry.getLabel()+"  "+oldState+" -> "+currentState);
									entry.getMenuItem().setEnabled( currentState.booleanValue() );
								}
							});
						}
					}
				}

				if ( cacheModified ) 
				{
					synchronized( stateCache ) 
					{
						this.stateCache.clear();
						this.stateCache.putAll( cacheCopy );
					}					
				}
			}
		}
			
	}

	protected JMenuBar createMenuBar() 
	{
		final List<MenuEntry> copy;
		synchronized( entriesList ) {
			copy = new ArrayList<MenuEntry>( entriesList );
		}

		// collect distinct parent paths
		final List<MenuPath> paths = new ArrayList<MenuPath>();
		for ( MenuEntry e : copy ) 
		{
			if ( ! e.isVisible() ) {
				continue;
			}
			final MenuPath parentPath = e.getPath().getParentPath();
			if ( parentPath == null ) {
				continue;
			}
			for ( MenuPath p : parentPath.getAllPaths() ) 
			{
				if ( ! paths.contains( p ) ) {
					paths.add( p );
				}
			}
		}

		// sort paths ascending by length
		Collections.sort( paths , new Comparator<MenuPath>() {

			@Override
			public int compare(MenuPath o1, MenuPath o2) 
			{
				final int len1 = o1.toString().length();
				final int len2 = o2.toString().length();
				if ( len1 < len2 ) {
					return -1;
				} else if ( len1 > len2 ) {
					return 1;
				}
				return 0;
			}
		});

		/*
		 * - a
		 *   |
		 *   +-- b
		 *       |
		 *       + c  
		 */

		// create menu for each path
		final Map<MenuPath,JMenu> menuesByPath = new HashMap<MenuPath,JMenu>(); 
		for ( MenuPath path : paths ) 
		{
			final JMenu menu = new JMenu( path.getLastPathComponent() );
			menuesByPath.put( path , menu );
			JMenu parentMenu = menuesByPath.get( path.getParentPath() );
			if ( parentMenu != null ) {
				parentMenu.add( menu );
			}
		}

		// setup menu bar

		//Where the GUI is created:
		final JMenuBar menuBar = new JMenuBar();

		final Set<MenuPath> topLevelMenues = new HashSet<MenuPath>();

		for ( final MenuEntry e : copy  ) {

			if ( ! e.isVisible() ) {
				continue;
			}

			final MenuPath parentPath = e.getPath().getParentPath();
			final JMenu menu = menuesByPath.get( parentPath );
			if ( menu == null ) {
				throw new RuntimeException("Internal error, failed to create menu for path: "+e.getPath());
			}

			// register top-level menues
			if ( parentPath.length() == 1 ) { 
				if ( ! topLevelMenues.contains( parentPath ) ) 
				{
					menuBar.add( menu );
					topLevelMenues.add( parentPath );
				}
			}

			final Action action;
			action = new AbstractAction( e.getLabel() ) {

				@Override
				public void actionPerformed(ActionEvent event) {
					e.onClick();
				}
				
				@Override
				public boolean isEnabled() {
					return e.isEnabled();
				}

			};

			final JMenuItem item = new JMenuItem( action ) {

				@Override
				public boolean isEnabled() {
					return action.isEnabled();
				}
			};

			if ( e.hasMnemonic() ) {
				item.setMnemonic( e.getMnemonic() );
			}
			e.setMenuItem( item );
			menu.add( item );
		}

		return menuBar;
	}

	public abstract void menuBarChanged();
}
