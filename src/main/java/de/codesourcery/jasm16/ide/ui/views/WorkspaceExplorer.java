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
package de.codesourcery.jasm16.ide.ui.views;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.IWorkspaceListener;
import de.codesourcery.jasm16.utils.Misc;

public class WorkspaceExplorer extends AbstractView {

	private static final Logger LOG = Logger.getLogger(WorkspaceExplorer.class);
	
	private final IWorkspace workspace;
	private WorkspaceTreeModel treeModel;
	
	private JPanel panel = null;
	private final JTree tree = new JTree();
	
	private final IWorkspaceListener listener = new IWorkspaceListener() {
		
		@Override
		public void resourceChanged(IAssemblyProject project , IResource resource) {
			if ( treeModel != null ) {
				treeModel.resourceChanged(project, resource);
			}
		}

		@Override
		public void projectCreated(IAssemblyProject project) {
			if ( treeModel != null ) {
				treeModel.projectCreated(project);
			}			
		}

		@Override
		public void projectDeleted(IAssemblyProject project) {
			if ( treeModel != null ) {
				treeModel.projectDeleted(project);
			}			
		}

		@Override
		public void resourceCreated(IAssemblyProject project, IResource resource) 
		{
			if ( treeModel != null ) {
				treeModel.resourceCreated(project, resource);
			}			
		}

		@Override
		public void resourceDeleted(IAssemblyProject project, IResource resource) 
		{
			if ( treeModel != null ) {
				treeModel.resourceDeleted(project,resource);
			}			
		}
	};
	
	public WorkspaceExplorer(IWorkspace workspace) 
	{
		if (workspace == null) {
			throw new IllegalArgumentException("workspace must not be NULL");
		}
		this.workspace = workspace;
	}
	
	@Override
	public void dispose() 
	{
		if ( panel != null ) {
			panel = null;
			workspace.removeWorkspaceListener( listener );
		}
	}

	@Override
	public JPanel getPanel() 
	{
		if ( panel == null ) {
			panel = createPanel();
			workspace.addWorkspaceListener( listener );
		}
		return panel;
	}

	private JPanel createPanel() 
	{
		treeModel = createTreeModel();
		tree.setModel( treeModel );
		
		final JPanel panel = new JPanel();
		setColors( panel );
		setColors( tree );
		tree.setRootVisible( false );
		
		tree.addMouseListener( new MouseAdapter() {
			
			public void mouseClicked(java.awt.event.MouseEvent e) 
			{
				if ( e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2 )
				{
					final TreePath path = tree.getPathForLocation( e.getX() , e.getY() );
					if ( path != null ) 
					{
						final WorkspaceTreeNode selected = (WorkspaceTreeNode) path.getLastPathComponent();
						final IAssemblyProject project = getProject( selected );
						try {
							if ( project != null ) {
								onTreeNodeLeftClick( project , selected );
							} else {
								System.err.println("Unable to locate project for "+selected);
							}
						} catch (IOException e1) {
							LOG.error("mouseClicked(): Internal error",e1);
						}
					}
				}
			}
		} );
		
		final TreeSelectionListener selectionListener = new TreeSelectionListener() {
			
			@Override
			public void valueChanged(TreeSelectionEvent e) {


			}
		};
		
		tree.getSelectionModel().addTreeSelectionListener( selectionListener );
		
		tree.setCellRenderer( new DefaultTreeCellRenderer() {
			
			public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
				
				java.awt.Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
				
				if ( value instanceof WorkspaceTreeNode) 
				{
					if ( value instanceof ProjectNode ) 
					{
						final ProjectNode projectNode = (ProjectNode) value;
						final IAssemblyProject project = projectNode.getValue();
						
						setText( project.getName() );
					} 
					else if ( value instanceof ResourceNode) 
					{
						final ResourceNode resourceNode = (ResourceNode) value;
						final File resource = resourceNode.getValue();
						setText( resource.getName() );
					} else {
						setText("");	
					}
				} else {
					setText("");
				}
				return result;
			};
			
		} );
		
		final JScrollPane pane = new JScrollPane( tree );
		setColors( pane );
		panel.add( pane );
		return panel;
	}
	
	protected void onTreeNodeLeftClick(IAssemblyProject project, WorkspaceTreeNode selected) throws IOException 
	{
		if ( project == null ) {
			throw new IllegalArgumentException("project must not be NULL");
		}
		
		if ( selected instanceof ResourceNode) 
		{
			final File file = ((ResourceNode) selected).getValue();
			if ( file.isFile() && Misc.isSourceFile( file ) ) 
			{
				openSourceFileEditor( project , file );
			}
		}
	}
	
	private void openSourceFileEditor(IAssemblyProject project,File sourceFile) throws IOException {
		
		final IResource resource = project.lookupResource( sourceFile.getAbsolutePath() );
		if ( ! resource.hasType( ResourceType.SOURCE_CODE ) ) {
			return;
		}
		
		final List<IView> views = getViewContainer().getViews();
		
		for (IView view : views) 
		{
			if ( view instanceof SourceEditorView ) 
			{
				((SourceEditorView) view).openFile( project , resource );
				return;
			}
		}
		final SourceEditorView view = new SourceEditorView();
		getViewContainer().addView( view );
		view.openFile( project , resource );
	}

	@Override
	public void refreshDisplay() {
		treeModel = createTreeModel();
		tree.setModel( treeModel );
	}
	
	protected IAssemblyProject getProject(WorkspaceTreeNode node) 
	{
		if ( node instanceof ProjectNode) {
			return ((ProjectNode) node).getValue();
		}
		if ( node instanceof ResourceNode) {
			WorkspaceTreeNode current = node.getParent();
			while( current != null ) 
			{
				if ( current instanceof ProjectNode) {
					return ((ProjectNode) current).getValue();
				}
				current = current.getParent();
			}
		}
		return null;
	}
	
	private static final class WorkspaceTreeModel extends DefaultTreeModel implements IWorkspaceListener {

		public WorkspaceTreeModel(TreeNode root) {
			super(root);
		}

		@Override
		public void projectCreated(IAssemblyProject project) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void projectDeleted(IAssemblyProject project) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void resourceChanged(IAssemblyProject project, IResource resource) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void resourceCreated(IAssemblyProject project, IResource resource) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void resourceDeleted(IAssemblyProject project, IResource resource) {
			// TODO Auto-generated method stub
			
		}
	}
	
	private WorkspaceTreeModel createTreeModel() {
		
		WorkspaceTreeNode result = new WorkspaceTreeNode(null);
		final List<IAssemblyProject> projects = workspace.getAllProjects();
		
		Collections.sort( projects , new Comparator<IAssemblyProject>() {

			@Override
			public int compare(IAssemblyProject o1, IAssemblyProject o2) {
				return o1.getName().compareTo( o2.getName() );
			}
		} );
		
		for ( IAssemblyProject p : projects ) {
			addProject( p , result );
		}
		return new WorkspaceTreeModel( result );
	}
	
	private void addProject(IAssemblyProject p, WorkspaceTreeNode result) 
	{
		final ProjectNode n = new ProjectNode( p );
		result.addChild( n );
		
		addDirectory( p.getConfiguration().getBaseDirectory() , n ); 
	}

	private void addDirectory(File dir, WorkspaceTreeNode n) 
	{
		final List<File> files = new ArrayList<File>();
		final List<File> dirs = new ArrayList<File>();
		
		for ( File f : dir.listFiles() ) 
		{
			if ( f.isFile() ) {
				files.add( f);
			} else if ( f.isDirectory() ) {
				dirs.add( f );
			}
		}
		
		final Comparator<File> BY_NAME_COMP = new Comparator<File>() {
			
			@Override
			public int compare(File o1, File o2) 
			{
				return o1.getName().compareTo( o2.getName() );
			}
		};
		
		Collections.sort( files , BY_NAME_COMP );
		Collections.sort( dirs , BY_NAME_COMP );
		
		// always add directores before files 
		for ( File current : dirs ) 
		{
			final ResourceNode dirNode = new ResourceNode( current );
			n.addChild( dirNode );
			addDirectory(  current , dirNode );
		}
		
		for ( File f : files ) 
		{
			n.addChild( new ResourceNode( f  ) );
		}
	}

	protected static class WorkspaceTreeNode implements javax.swing.tree.TreeNode 
	{
		private WorkspaceTreeNode parent;
		private final List<WorkspaceTreeNode> children = new ArrayList<WorkspaceTreeNode>();
		private final Object value;
		
		protected WorkspaceTreeNode(Object value) {
			this.value = value;
		}
		
		public Object getValue() {
			return value;
		}
		
		public void addChild(WorkspaceTreeNode child) {
			children.add( child );
			child.setParent( this );
		}
		
		public WorkspaceTreeNode getParent() {
			return parent;
		}
		
		public void setParent(WorkspaceTreeNode parent) {
			this.parent = parent;
		}

		@Override
		public Enumeration children() 
		{
			final Iterator<WorkspaceTreeNode> iterator = children.iterator();
			return new Enumeration<WorkspaceTreeNode>() {

				@Override
				public boolean hasMoreElements() { return iterator.hasNext(); }

				@Override
				public WorkspaceTreeNode nextElement() { return iterator.next(); }
			};
		}

		@Override
		public boolean getAllowsChildren() 
		{
			if ( this instanceof ResourceNode) 
			{
				return ((ResourceNode) this).isDirectory();
			}
			return true;
		}

		@Override
		public javax.swing.tree.TreeNode getChildAt(int childIndex) 
		{
			return children.get( childIndex );
		}

		@Override
		public int getChildCount() {
			return children.size();
		}

		@Override
		public int getIndex(javax.swing.tree.TreeNode node) {
			return children.indexOf( node );
		}

		@Override
		public boolean isLeaf() 
		{
			if ( this instanceof ResourceNode) {
				final File file = ((ResourceNode) this).getValue();
				if ( file.isFile() ) {
					return true;
				}
				return getChildCount() == 0;
			}
			return false;
		}
	}
	
	protected static class ProjectNode extends WorkspaceTreeNode
	{
		public ProjectNode(IAssemblyProject project) {
			super( project );
			if (project== null) {
				throw new IllegalArgumentException("project must not be NULL");
			}
		}
		
		@Override
		public String toString() {
			return "Project "+getValue().getName();
		}
		
		@Override
		public IAssemblyProject getValue() {
			return (IAssemblyProject) super.getValue();
		}
	}	
	
	protected static final class ResourceNode extends WorkspaceTreeNode {
		
		public ResourceNode(File file) {
			super( file );
			if (file == null) {
				throw new IllegalArgumentException("file must not be NULL");
			}
		}
		
		@Override
		public File getValue() {
			return (File) super.getValue();
		}
		
		@Override
		public String toString() {
			return "Resource "+getValue().getAbsolutePath();
		}
		
		public boolean isFile() {
			return getValue().isFile();
		}
		
		public boolean isDirectory() {
			return getValue().isDirectory();
		}
		
	}

	@Override
	public String getTitle() {
		return "Workspace view";
	}
}
