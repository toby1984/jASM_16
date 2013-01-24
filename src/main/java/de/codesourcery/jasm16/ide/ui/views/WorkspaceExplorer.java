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

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.codesourcery.jasm16.compiler.ICompilationUnit;
import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.EditorFactory;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.IWorkspaceListener;
import de.codesourcery.jasm16.ide.exceptions.ProjectAlreadyExistsException;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils;
import de.codesourcery.jasm16.ide.ui.utils.UIUtils.DialogResult;
import de.codesourcery.jasm16.ide.ui.viewcontainers.DebuggingPerspective;
import de.codesourcery.jasm16.ide.ui.viewcontainers.EditorContainer;
import de.codesourcery.jasm16.ide.ui.viewcontainers.ViewContainerManager;
import de.codesourcery.jasm16.utils.Misc;

public class WorkspaceExplorer extends AbstractView {

	private static final Logger LOG = Logger.getLogger(WorkspaceExplorer.class);

	private final ViewContainerManager perspectivesManager;
	private final IWorkspace workspace;
	private final EditorFactory editorFactory;

	private WorkspaceTreeModel treeModel;

	private JPanel panel = null;
	private final JTree tree = new JTree();

	private final FileFilter fileFilter = new FileFilter() {

		private final Set<String> ignore_suffix = new HashSet<String>( 
				Arrays.asList( new String[]{".bat" , ".exe" , ".sh" , ".xml" , ".10csln" , ".10csuo" ,  ".10cproj"} ) );

		@Override
		public boolean accept(File file) 
		{
			if ( file.isHidden() || file.getName().startsWith(".") ) {
				return false;
			}

			final String name = file.getName().toLowerCase();
			for ( String toIgnore : ignore_suffix ) {
				if ( name.endsWith( toIgnore ) ) {
					return false;
				}
			}
			return true;
		}
	};

	public WorkspaceExplorer(IWorkspace workspace, ViewContainerManager perspectivesManager, EditorFactory editorFactory) 
	{
		if (workspace == null) {
			throw new IllegalArgumentException("workspace must not be NULL");
		}
		if ( perspectivesManager == null ) {
			throw new IllegalArgumentException("perspectivesManager must not be NULL.");
		}
		if ( editorFactory == null ) {
			throw new IllegalArgumentException("editorFactory must not be null");
		}
		this.editorFactory = editorFactory;
		this.perspectivesManager = perspectivesManager;
		this.workspace = workspace;
	}

	@Override
	public void disposeHook() 
	{
		if ( panel != null ) {
			panel = null;
			if ( treeModel != null ) {
				workspace.removeWorkspaceListener( treeModel);
			}
		}
	}

	@Override
	public JPanel getPanel() 
	{
		if ( panel == null ) {
			panel = createPanel();
			workspace.addWorkspaceListener( treeModel );
		}
		return panel;
	}

	protected WorkspaceTreeNode getSelectedNode() {
		TreePath path = tree.getSelectionPath();
		if ( path != null ) {
			return (WorkspaceTreeNode) path.getLastPathComponent();
		}
		return null;
	}

	private JPanel createPanel() 
	{
		treeModel = createTreeModel();
		tree.setModel( treeModel );
		setColors( tree );
		tree.setRootVisible( false );

		//		for (int i = 0; i < tree.getRowCount(); i++) {
		//			tree.expandRow(i);
		//		}

		tree.addKeyListener( new KeyAdapter() 
		{
			@Override
			public void keyPressed(KeyEvent e) {
				if ( e.getKeyCode() == KeyEvent.VK_DELETE ) 
				{
					final WorkspaceTreeNode selection = getSelectedNode();
					if ( selection != null ) {
						deleteResource( selection );
					}
				} else if ( e.getKeyCode() == KeyEvent.VK_F5) {
					refreshWorkspace(null);
				}
			}
		});

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

			public Color getTextSelectionColor() {
				return Color.GREEN;
			};

			public java.awt.Color getTextNonSelectionColor() {
				return Color.GREEN;
			};			

			public Color getBackgroundSelectionColor() {
				return Color.BLACK;
			};

			public Color getBackgroundNonSelectionColor() {
				return Color.BLACK;
			};

			public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

				java.awt.Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

				String label = value.toString();
				Color color = getTextColor();
				if ( value instanceof WorkspaceTreeNode) 
				{
					if ( value instanceof ProjectNode ) 
					{
						final ProjectNode projectNode = (ProjectNode) value;
						final IAssemblyProject project = projectNode.getValue();
                        label = project.getName();
                        
						if ( project.isClosed() ) {
						    color = Color.LIGHT_GRAY;
						} else {
						    if ( projectNode.hasCompilationErrors() ) {
						        color = Color.RED;
						    }
						}
					} 
					else if ( value instanceof FileNode) 
					{
						FileNode fn = (FileNode) value;
						label = fn.getValue().getName();
						switch( fn.type ) 
						{
							case DIRECTORY:
								if ( fn.hasCompilationErrors() )
								{
									color = Color.RED;
								} 
								break;
							case OBJECT_FILE:  
								label = "[O] "+label;
								break;
							case EXECUTABLE:
								label = "[E] "+label;
								break;
							case SOURCE_CODE: 
								label = "[S] "+label;
								if ( fn.hasCompilationErrors() )
								{
									color = Color.RED;
								} 
								break;
							default:
								// ok
						}
					}
				} 
				setForeground( color );
				setText( label );
				return result;
			};

		} );

		setupPopupMenu();

		final JScrollPane pane = new JScrollPane( tree );
		setColors( pane );

		final JPanel result = new JPanel();
		result.setLayout( new GridBagLayout() );
		GridBagConstraints cnstrs = constraints(0 , 0 , true , true , GridBagConstraints.BOTH);
		setColors( result );		
		result.add( pane , cnstrs );
		return result;
	}

	protected void deleteResource(WorkspaceTreeNode selection) 
	{
		try {
			if ( selection instanceof FileNode ) 
			{
				final File file = ((FileNode) selection).getValue();
				final String title = "Delete "+file.getName()+" ? ";
				final String message = "Do you really want to delete this "+( file.isFile() ? "file ?" : "directory ?" );

				final DialogResult outCome = UIUtils.showConfirmationDialog( panel , title , message );
				if ( outCome != DialogResult.YES ) {
					return;
				}			

				final IAssemblyProject project = getProject( selection );
				workspace.deleteFile( project , file );
				return;
			} 

			if ( selection instanceof ProjectNode ) 
			{
				final IAssemblyProject project = ((ProjectNode) selection).getValue();
				final Boolean result = UIUtils.showDeleteProjectDialog( project );
				if ( result == null ) { // user CANCEL
					return;
				}
				workspace.deleteProject( project , result );
				return;
			} 
		} catch(IOException e) {
			LOG.error("deleteResource(): Failed to delete "+selection,e);
		}

		throw new RuntimeException("Internal error,unhandled node type "+selection);
	}

	protected void onTreeNodeLeftClick(IAssemblyProject project, WorkspaceTreeNode selected) throws IOException 
	{
		if ( project == null ) {
			throw new IllegalArgumentException("project must not be NULL");
		}

		if ( selected instanceof FileNode) 
		{
			final File file = ((FileNode) selected).getValue();
			if ( file.isFile() && project.getConfiguration().isSourceFile( file ) ) 
			{
				openSourceFileEditor( project , file );
			}
		}
	}

	private void openSourceFileEditor(IAssemblyProject project,File sourceFile) throws IOException {

		final IResource resource = project.lookupResource( sourceFile.getAbsolutePath() );

		EditorContainer editorContainer = (EditorContainer ) getViewContainer().getViewByID( EditorContainer.VIEW_ID );

		if ( editorContainer == null ) {
			editorContainer = new EditorContainer("Editors",workspace,getViewContainer(),editorFactory);
			getViewContainer().addView( editorContainer );
		}
		editorContainer.openResource( workspace , project , resource , 0 );
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
		if ( node instanceof FileNode) {
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

	protected void setupPopupMenu() 
	{
		tree.addMouseListener( new PopupListener() );
	}
	
	private void openAllProjects() 
	{
        for ( IAssemblyProject p : workspace.getAllProjects() ) {
            if ( p.isClosed() ) {
                workspace.openProject( p );
            }
        }
	}
	
    private void closeAllProjects() 
    {
        for ( IAssemblyProject p : workspace.getAllProjects() ) {
            if ( p.isOpen() ) {
                workspace.closeProject( p );
            }
        }        
    }	

	protected JPopupMenu createPopupMenu(final WorkspaceTreeNode selectedNode) 
	{
		final JPopupMenu popup = new JPopupMenu();

		int openProjects = 0;
		int closedProjects = 0;
        for ( IAssemblyProject p : workspace.getAllProjects() ) {
            if ( p.isOpen() ) {
                openProjects++;
            } else {
                closedProjects++;
            }
        }
        
		// open debugger
		final IAssemblyProject project = getProject( selectedNode );
		if ( project != null ) 
		{
			if ( project.isOpen() ) {
				addMenuEntry( popup , "Close project", new ActionListener() {

					@Override
					public void actionPerformed(ActionEvent e) {
						workspace.closeProject( project );
					}
				});	
			} else {
				addMenuEntry( popup , "Open project", new ActionListener() {

					@Override
					public void actionPerformed(ActionEvent e) {
						workspace.openProject( project );
					}
				});					
			}

			addMenuEntry( popup , "Build project", new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						project.getProjectBuilder().build();
					} catch (Exception ex) {
						LOG.error("Failed to build "+project,ex);
						UIUtils.showErrorDialog(null,"Building project '"+project.getName()+"' failed" , "Building the project failed: "+ex.getMessage(),ex);
					}
				}
			});		

			if ( project.getProjectBuilder() == null ) {
				throw new RuntimeException("Internal error, project "+project.getName()+" has no builder ??");
			}
			final IResource executable = project.getProjectBuilder().getExecutable();
			if ( executable != null ) 
			{
				addMenuEntry( popup , "Open in debugger", new ActionListener() {

					@Override
					public void actionPerformed(ActionEvent e) 
					{
						try {
							openDebugPerspective( project , executable , perspectivesManager);
						} 
						catch (IOException e1) {
							LOG.error("Failed to open debug perspective for "+project+" , resource "+executable,e1);
						}
					}
				}); 	
			}
		}

		if ( canCreateFileIn( selectedNode ) ) {
			addMenuEntry( popup , "New source file...", new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) 
				{
					try {
						createNewSourceFile( selectedNode );
					} catch (IOException e1) {
						LOG.error("actionPerformed(): ",e1);
					}
				}
			});				
		}

		addMenuEntry( popup , "New project...", new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) 
			{
				try {
					createNewProject();
				} 
				catch (Exception e1) {
					LOG.error("actionPerformed(): ",e1);
					UIUtils.showErrorDialog( panel ,
							"Failed to create project",
							"Project creation failed: "+e1.getMessage()
							);
				}				
			}
		});			

		if ( selectedNode != null ) {
			addMenuEntry( popup , "Delete...", new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) 
				{
					deleteResource( selectedNode );
				}

			});	
		}

		addMenuEntry( popup , "Refresh", new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) 
			{
				refreshWorkspace( project );
			}
		}); 	

		addMenuEntry( popup , "Import existing project...", new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) 
			{
				final JFileChooser fc = new JFileChooser( workspace.getBaseDirectory() );
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				final int returnVal = fc.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) 
				{
					final File directory = fc.getSelectedFile();
					try {
						workspace.importProject( directory );
					} catch (Exception e1) {
						UIUtils.showErrorDialog( null , "Failed to import project","Error while importing project: "+e1.getMessage());
					}
				} 
			}

		});         

		if ( project != null ) 
		{
			addMenuEntry( popup , "Project properties...", new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) 
				{
					ProjectConfigurationView view = (ProjectConfigurationView) getViewContainer().getViewByID( ProjectConfigurationView.ID );
					if ( view == null ) {
						view = new ProjectConfigurationView() {

							@Override
							protected void onSave()
							{
								apply( project.getConfiguration() );

								try {
									project.getConfiguration().save();
								} 
								catch (IOException e) {
									UIUtils.showErrorDialog( null , "Error" , "Failed to save project options" , e);
								} finally {
									getViewContainer().disposeView( this );                                    
								}
							}

							@Override
							protected void onCancel()
							{
								getViewContainer().disposeView( this );
							}
						};
						getViewContainer().addView( view );
					}

					view.setProject( project );
					view.getViewContainer().toFront( view );
				}

			});     		
		}
		
        if ( closedProjects > 0 ) {
            addMenuEntry( popup , "Open ALL projects", new ActionListener() {

                  @Override
                  public void actionPerformed(ActionEvent e) {
                      openAllProjects();
                  }
              }); 
      }
        
        if ( openProjects > 0 ) {
              addMenuEntry( popup , "Close ALL projects", new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        closeAllProjects();
                    }
                }); 
        }		
		return popup;
	}	

	protected void refreshWorkspace(IAssemblyProject project) {
		try {
			workspace.refreshProjects( project != null ? Collections.singletonList( project ) : workspace.getAllProjects() );
		} catch (IOException e1) {
			LOG.error("rescanWorkspace(): Failed ",e1);
		}
	}

	protected void createNewProject() throws IOException, ProjectAlreadyExistsException {

		final String projectName = UIUtils.showInputDialog( panel , "Create new project", "Project name" );
		if ( projectName == null ) {
			return;
		}		

		workspace.createNewProject( projectName );
	}

	protected void createNewSourceFile(WorkspaceTreeNode selection ) throws IOException 
	{
		File parentDir = ((FileNode) selection).getValue();
		if ( parentDir.isFile() ) {
			parentDir = parentDir.getParentFile();
		}

		final String fileName = UIUtils.showInputDialog( panel , "Create source file", "File name" );
		if ( fileName == null ) {
			return;
		}

		final File file = new File(parentDir , fileName );
		Misc.writeFile( file , "" );

		final IAssemblyProject project = getProject( selection );
		workspace.resourceCreated( project , new FileResource( file , ResourceType.SOURCE_CODE ) );
	}

	protected boolean canCreateFileIn( WorkspaceTreeNode selection ) 
	{
		if ( selection instanceof ProjectNode ) {
			return true;
		}
		if ( selection instanceof FileNode) 
		{
			final File file = ((FileNode) selection).getValue();
			final IAssemblyProject project = getProject( selection );

			if ( isInProjectSourceFolder( project , file ) ) 
			{
				return true;
			} 
		}
		return false;
	}

	private boolean isInProjectSourceFolder(IAssemblyProject project,File file) {

		for ( File srcFolder : project.getConfiguration().getSourceFolders() ) {
			if ( file.getAbsolutePath().startsWith( srcFolder.getAbsolutePath() ) ) {
				return true;
			}
		}
		return false;
	}

	public static boolean canOpenInDebugPerspective(IAssemblyProject project) throws IOException 
	{
		// source level view depends on AST being available for  
		// compilation units and we want the debugger to run
		// the latest changes anyway... rebuild if necessary
		if ( project.getProjectBuilder().isBuildRequired() ) 
		{
			if ( ! project.getProjectBuilder().build() ) 
			{
				return false;
			}
		}
		return true;
	}

	public static void openDebugPerspective(IAssemblyProject project, IResource executable,ViewContainerManager perspectivesManager) throws IOException
	{
		if ( project == null ) {
			throw new IllegalArgumentException("project must not be NULL.");
		}
		if ( executable == null ) {
			throw new IllegalArgumentException("executable must not be NULL.");
		}

		if ( canOpenInDebugPerspective( project ) ) 
		{
			final DebuggingPerspective p= perspectivesManager.getOrCreateDebuggingPerspective();
			p.openExecutable( project , executable );
			p.setVisible( true );
			p.toFront();            
		}
	}

	protected class PopupListener extends MouseAdapter 
	{
		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
		}

		private void maybeShowPopup(MouseEvent e) 
		{
			if (e.isPopupTrigger()) 
			{
				final TreePath path = tree.getPathForLocation( e.getX() , e.getY() );
				final WorkspaceTreeNode node;
				if ( path != null ) 
				{
					node = (WorkspaceTreeNode) path.getLastPathComponent();
				} else {
					node = null;
				}
				final JPopupMenu menu = createPopupMenu( node );
				if ( menu != null ) {
					menu.show(e.getComponent(),e.getX(), e.getY());
				}
			}
		}
	}

	private final class WorkspaceTreeModel extends DefaultTreeModel implements IWorkspaceListener {

		private WorkspaceTreeNode root;

		public WorkspaceTreeModel() {
			super( null );
		}

		@Override
		public WorkspaceTreeNode getRoot() 
		{
			if ( root == null ) {
				root = createRootNode();
			}
			return root;
		}

		@Override
		public void projectCreated(IAssemblyProject project) {
			treeStructureChanged();			
		}

		@Override
		public void projectConfigurationChanged(IAssemblyProject project) {
			treeStructureChanged();				
		}

		@Override
		public void projectDisposed(IAssemblyProject project)
		{
		}

		@Override
		public void projectDeleted(IAssemblyProject project) {
			treeStructureChanged();
		}

		private Object[] getPathForResource(final IAssemblyProject project, final IResource resource) {

			final WorkspaceTreeNode[] match ={null};
			root.visit( new TreeNodeVisitor() {

				@Override
				public Outcome visit(WorkspaceTreeNode node) 
				{
					if ( node instanceof ProjectNode) 
					{
						ProjectNode projectNode = (ProjectNode) node;
						if ( projectNode.getValue() != project &&
								! projectNode.getValue().getName().equals( project.getName() ) ) 
						{
							return Outcome.DONT_GO_DEEPER;
						}
					} else if ( node instanceof FileNode ) {
						FileNode  fn = (FileNode ) node;
						if ( fn.getValue().getAbsolutePath().equals( resource.getIdentifier() ) ) {
							match[0] = node;
							return Outcome.STOP;
						}
					}
					return Outcome.CONTINUE;
				}
			} );
			return match[0] == null ? null : match[0].getPathToRoot();
		}

		@Override
		public void resourceChanged(IAssemblyProject project, IResource resource) 
		{
			Object[] path = getPathForResource( project , resource);
			if ( path == null ) 
			{
				return;
			}
			
			WorkspaceTreeNode child = (WorkspaceTreeNode) path[ path.length - 1 ];
			if ( child instanceof FileNode ) 
			{
				for ( int i = path.length-1 ; i >= 0 ; i--) 
				{
					WorkspaceTreeNode node = (WorkspaceTreeNode) path[i];
					if ( node instanceof FileNode) 
					{
						FileNode fn = (FileNode) path[i];
						final boolean oldFlag = fn.hasCompilationErrors();
	
						fn.refresh();
						
						ICompilationUnit unit;
						if ( fn.hasCompilationErrors() && ( unit = fn.getCompilationUnit() ) != null ) 
						{
							try {					
								Misc.printCompilationErrors( unit , unit.getResource() , true );
							} catch (Exception e) {
							    System.err.println( "printCompilationErrors() failed: "+e.getMessage() );							    
							    e.printStackTrace();
							}
						}
						if ( oldFlag != fn.hasCompilationErrors() || fn == child ) 
						{
							notifyNodeChanged( path );
						}
					} 
					else if ( node instanceof ProjectNode) 
					{
						ProjectNode pn = (ProjectNode) node;
						if ( pn.recalculateHasCompilationErrorsFlag() ) {
							notifyNodeChanged( node.getPathToRoot() );
						}
					}
				}
			} else {
				notifyNodeChanged( path );					
			}
		}

		private void notifyNodeChanged(Object[] childPath) 
		{
			if ( childPath == null || childPath.length == 0 ) {
				treeStructureChanged();
			} 
			else 
			{
				final Object[] parentPath = new Object[ childPath.length - 1 ];
				System.arraycopy( childPath , 0 , parentPath , 0 , childPath.length - 1 );
				final WorkspaceTreeNode child = (WorkspaceTreeNode) childPath[ childPath.length - 1 ];
				final WorkspaceTreeNode parent = (WorkspaceTreeNode) parentPath[ parentPath.length - 1 ];
				final int childIndex = parent.getIndex( child );

				fireTreeNodesChanged( this , parentPath, new int[] { childIndex } , new Object[] { child });  
			}
		}

		@Override
		public void resourceCreated(IAssemblyProject project, IResource resource) 
		{
			final FileResource fr = (FileResource) resource;
			final WorkspaceTreeNode nearestNode = findNearestFileNode( project , fr );

			if ( !( nearestNode instanceof FileNode) ) 
			{
				treeStructureChanged();
				return;
			}

			final FileNode fn = (FileNode) nearestNode;

			final String existingPath = fn.getValue().getAbsolutePath();
			final String actualPath = fr.getFile().getAbsolutePath();
			if ( existingPath.equals( actualPath ) )
			{
				LOG.warn("resourceCreated(): Duplicate creation event?");
				return; // ...we already know about this node...strange...
			} 

			// create intermediate nodes
			// existingPath = /a/b
			// actualPath   = /a/b/c/d
			// delta        =     /c/d
			String delta = actualPath.substring( existingPath.length() , actualPath.length() );
			while ( delta.startsWith( File.separator ) ) {
				delta = delta.substring( 1 , delta.length() );
			}
			final String[] deltaPath = delta.split( Pattern.quote( File.separator ) );

			FileNode previousNode = fn;
			FileNode newRoot = null;
			for ( int i = 0 ; i < deltaPath.length ; i++ ) 
			{
				final String path = i == 0 ? deltaPath[i] : 
					StringUtils.join( deltaPath , File.separator , 0 , i );

				final File currentPath = new File( fn.getValue().getAbsoluteFile() , path); 
				FileNode newNode = new FileNode( currentPath );
				if ( newRoot == null ) {
					newRoot = newNode;
				}
				previousNode.addChild( newNode );
				previousNode = newNode;
			}

			fireTreeNodesInserted( this , 
					newRoot.getParent().getPathToRoot() ,
					new int[] { newRoot.getParent().getIndex( newRoot ) } ,
					new Object[] { newRoot } ); 			
		}

		@Override
		public void resourceDeleted(IAssemblyProject project, IResource resource) 
		{
			final FileResource fr = (FileResource) resource;
			final WorkspaceTreeNode nearestNode = findNearestFileNode( project , fr );

			if ( !( nearestNode instanceof FileNode) ) 
			{
				treeStructureChanged();
				return;
			}

			final FileNode fn = (FileNode) nearestNode;

			if ( ! fn.getValue().getAbsolutePath().equals( fr.getFile().getAbsolutePath() ) ) 
			{
				treeStructureChanged();
				return;
			} 

			// perfect match
			final Object[] pathToRoot = fn.getParent().getPathToRoot();
			final int index = fn.getParent().getIndex( fn );

			fn.getParent().removeChild( fn );

			fireTreeNodesRemoved( this , 
					pathToRoot , 
					new int[] { index } ,
					new Object[] { fn } ); 
		}

		private void treeStructureChanged() {
			root = createRootNode();
			fireTreeStructureChanged( this , new Object[] { root } , new int[0] , new Object[0] );
		}

		private WorkspaceTreeNode findNearestFileNode(IAssemblyProject project,FileResource fileResource) 
		{
			final ProjectNode parent = findProjectNode( project );
			if ( parent == null ) {
				return null;
			}

			return findNearestFileNode( parent , fileResource.getFile() );
		}

		private FileNode findNearestFileNode(ProjectNode parent,File file) 
		{
			File currentPath = file;
			do {
				WorkspaceTreeNode result = parent.findNodeByValue( currentPath );
				if ( result != null ) {
					return (FileNode) result;
				}
				currentPath = currentPath.getParentFile();
			} while ( currentPath != null );

			return null;
		}
		
		private ProjectNode findProjectNode(IAssemblyProject project) {

			for ( WorkspaceTreeNode child : getRoot().children ) {
				if ( child instanceof ProjectNode ) {
					if ( child.getValue() == project ) {
						return (ProjectNode) child;
					}
				}
			}
			return null;
		}

		private WorkspaceTreeNode createRootNode() {

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
			return result;
		}

		private void addProject(IAssemblyProject p, WorkspaceTreeNode result) 
		{
			final ProjectNode n = new ProjectNode( p );
			result.addChild( n );

			if ( p.isOpen() ) {
				addDirectory( p.getConfiguration().getBaseDirectory() , n );
			}
		}

		private void addDirectory(File dir, WorkspaceTreeNode n) 
		{
			final List<File> files = new ArrayList<File>();
			final List<File> dirs = new ArrayList<File>();

			for ( File f : dir.listFiles( fileFilter ) ) 
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
				final FileNode dirNode = new FileNode( current );
				n.addChild( dirNode );
				addDirectory(  current , dirNode );
			}

			for ( File f : files ) 
			{
				n.addChild( new FileNode( f  ) );
			}
		}

		@Override
		public void buildStarted(IAssemblyProject project) { /* no-op */ }

		@Override
		public void compilationFinished(IAssemblyProject project, ICompilationUnit unit) 
		{
		 	ProjectNode pn = findProjectNode( project );
		 	if ( pn != null ) {
		 		FileNode fn = findNearestFileNode( pn , new File( unit.getResource().getIdentifier() ) );
		 		if ( fn != null ) {
		 			resourceChanged( project , unit.getResource() );
		 		}
		 	}
		}
		
		@Override
		public void buildFinished(IAssemblyProject project, boolean success) 
		{
			List<IResource> resources = project.getResources(ResourceType.SOURCE_CODE);
			for ( IResource r : resources ) {
				resourceChanged( project , r );
			}
		}

		@Override
		public void projectClosed(IAssemblyProject project) {

			ProjectNode node = findProjectNode( project );
			if ( node != null ) 
			{
				List<WorkspaceTreeNode> formerChildren = node.removeChildren();
				final int [] indices = new int[ formerChildren.size() ];
				for ( int i = 0 ; i < indices.length ; i++ ) {
					indices[i]=i;
				}
				fireTreeNodesRemoved( this , node.getPathToRoot() , indices , 
						formerChildren.toArray( new Object[ formerChildren.size() ] ) );
			}
		}
		
		@Override
		public void projectLoaded(IAssemblyProject project)
		{
		    projectOpened(project);
		}

		@Override
		public void projectOpened(IAssemblyProject project) 
		{
			ProjectNode node = findProjectNode( project );
			if ( node != null ) 
			{
				if ( node.getChildCount() != 0 ) {
					return;
				}
				addDirectory( project.getConfiguration().getBaseDirectory() , node ); 

				final int [] indices = new int[ node.getChildCount() ];
				for ( int i = 0 ; i < indices.length ; i++ ) {
					indices[i]=i;
				}
				final Object[] children = node.getChildren().toArray();
				fireTreeNodesInserted( this , node.getPathToRoot() , indices , children );
			} else {
				projectCreated( project );
			}
		}
	}

	private WorkspaceTreeModel createTreeModel() {
		return new WorkspaceTreeModel();
	}

	private static enum Outcome {
		STOP,
		CONTINUE,
		DONT_GO_DEEPER;
	}

	protected interface TreeNodeVisitor {

		public Outcome visit(WorkspaceTreeNode node);
	}

	protected static class WorkspaceTreeNode implements javax.swing.tree.TreeNode 
	{
		private WorkspaceTreeNode parent;
		private final List<WorkspaceTreeNode> children = new ArrayList<WorkspaceTreeNode>();
		private final Object value;

		protected WorkspaceTreeNode(Object value) {
			this.value = value;
		}

		public final void removeChild(FileNode fn) {
			children.remove( fn );
		}

		public final List<WorkspaceTreeNode> getChildren() {
			return children;
		}

		public final ProjectNode getProjectNode() {

			WorkspaceTreeNode current = this;
			while ( current != null && !( current instanceof ProjectNode ) ) 
			{ 
				current = current.getParent();
			}
			return current == null ? null : (ProjectNode) current;
		}

		public final Outcome visit(TreeNodeVisitor v) 
		{
			Outcome result = v.visit( this );
			if ( result == Outcome.STOP) {
				return result;
			}
			if ( result == Outcome.DONT_GO_DEEPER ) {
				return Outcome.CONTINUE;
			}
			for ( WorkspaceTreeNode child : children ) 
			{
				result = child.visit( v );
				if ( result == Outcome.STOP ) {
					return result;
				}
			}
			return Outcome.CONTINUE;
		}

		public final List<WorkspaceTreeNode> removeChildren() {
			List<WorkspaceTreeNode> copy = new ArrayList<WorkspaceTreeNode>( children );
			children.clear();
			return copy;
		}

		public boolean isProjectNode() {
			return false;
		}

		public boolean isFileNode() {
			return false;
		}		

		public final Object[] getPathToRoot() 
		{
			final List<WorkspaceTreeNode> result = new ArrayList<WorkspaceTreeNode>();

			WorkspaceTreeNode current = this;
			do {
				result.add( current );
				current = current.getParent();
			} while ( current != null );

			Collections.reverse( result );
			return result.toArray( new Object[ result.size() ] );
		}

		public Object getValue() {
			return value;
		}

		public final void addChild(WorkspaceTreeNode child) {
			children.add( child );
			child.setParent( this );
		}

		public WorkspaceTreeNode findNodeByValue(Object expected) {

			if ( ObjectUtils.equals( this.value , expected ) ) {
				return this;
			}

			for ( WorkspaceTreeNode child : children ) {
				WorkspaceTreeNode result = child.findNodeByValue( expected );
				if ( result != null ) {
					return result;
				}
			}
			return null;
		}
		public WorkspaceTreeNode getParent() {
			return parent;
		}

		public void setParent(WorkspaceTreeNode parent) {
			this.parent = parent;
		}

		@Override
		public Enumeration<WorkspaceTreeNode> children() 
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
			if ( this instanceof FileNode) 
			{
				return ((FileNode) this).isDirectory();
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
			if ( this instanceof FileNode) {
				final File file = ((FileNode) this).getValue();
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
		private boolean hasCompilationErrors = false; 

		public boolean hasCompilationErrors()
		{
			return hasCompilationErrors;
		}

		public boolean recalculateHasCompilationErrorsFlag() 
		{
			final boolean[] newFlag = { false };
			final TreeNodeVisitor visitor = new TreeNodeVisitor() {

				@Override
				public Outcome visit(WorkspaceTreeNode node) 
				{
					if ( node instanceof FileNode) {
						if ( ((FileNode) node).hasCompilationErrors() ) {
							newFlag[0] = true;
							return Outcome.STOP;
						}
					}
					return Outcome.CONTINUE;
				}
			};

			visit( visitor );

			boolean flagChanged = this.hasCompilationErrors != newFlag[0];
			this.hasCompilationErrors = newFlag[0];
			return flagChanged;
		}

		public ProjectNode(IAssemblyProject project) {
			super( project );
			if (project== null) {
				throw new IllegalArgumentException("project must not be NULL");
			}
		}

		public boolean isProjectNode() {
			return true;
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

	protected static final class FileNode extends WorkspaceTreeNode {

		private boolean hasCompilationErrors = false;        

		private ResourceType type = ResourceType.UNKNOWN;

		public FileNode(File file) {
			super( file );
			if (file == null) {
				throw new IllegalArgumentException("file must not be NULL");
			}
			if ( file.isDirectory() ) {
				this.type = ResourceType.DIRECTORY;
			}
		}

		public boolean hasType(ResourceType t) {
			return t.equals( type );
		}

		public boolean hasCompilationErrors()
		{
			return hasCompilationErrors;
		}

		public void refresh() 
		{
			this.type = ResourceType.UNKNOWN;
			this.hasCompilationErrors = false;
			
			if ( getValue().isDirectory() ) 
			{
				this.type = ResourceType.DIRECTORY;
				for ( WorkspaceTreeNode child : getChildren() ) 
				{
					if ( child instanceof FileNode && ((FileNode) child).hasCompilationErrors() ) {
						this.hasCompilationErrors = true;
						return;
					}
				}
				return;
			}
			
			final ICompilationUnit compUnit = getCompilationUnit();
			this.hasCompilationErrors = compUnit == null ? false : compUnit.hasErrors();
		}
		
		public ICompilationUnit getCompilationUnit() 
		{
			final File file = this.getValue();

			final IAssemblyProject project = getProjectNode().getValue();
			final IResource projectResource = project.getResourceForFile(  file );

			if ( projectResource != null ) 
			{
				this.type = projectResource.getType();
				if ( projectResource.hasType( ResourceType.SOURCE_CODE) ) 
				{
					return  project.getProjectBuilder().getCompilationUnit( projectResource );
				}
			}  
			return null;
		}

		public boolean isFileNode() {
			return true;
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

	@Override
	public String getID() {
		return "workspace-explorer";
	}

	@Override
	public boolean mayBeDisposed() {
		return false;
	}
}