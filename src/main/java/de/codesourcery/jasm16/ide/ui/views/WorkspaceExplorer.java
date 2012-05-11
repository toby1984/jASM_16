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
import java.util.regex.Pattern;

import javax.swing.JMenuItem;
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

import de.codesourcery.jasm16.compiler.io.FileResource;
import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.compiler.io.IResource.ResourceType;
import de.codesourcery.jasm16.ide.EditorFactory;
import de.codesourcery.jasm16.ide.IApplicationConfig;
import de.codesourcery.jasm16.ide.IAssemblyProject;
import de.codesourcery.jasm16.ide.IWorkspace;
import de.codesourcery.jasm16.ide.IWorkspaceListener;
import de.codesourcery.jasm16.ide.ui.viewcontainers.DebuggingPerspective;
import de.codesourcery.jasm16.ide.ui.viewcontainers.EditorContainer;
import de.codesourcery.jasm16.ide.ui.viewcontainers.IViewContainer;
import de.codesourcery.jasm16.ide.ui.viewcontainers.ViewContainerManager;
import de.codesourcery.jasm16.utils.Misc;

public class WorkspaceExplorer extends AbstractView {

    private static final Logger LOG = Logger.getLogger(WorkspaceExplorer.class);

    private final ViewContainerManager perspectivesManager;
    private final IWorkspace workspace;
    private final IApplicationConfig applicationConfig;
    
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

    public WorkspaceExplorer(IWorkspace workspace,ViewContainerManager perspectivesManager,IApplicationConfig appConfig) 
    {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be NULL");
        }
        if ( perspectivesManager == null ) {
            throw new IllegalArgumentException("perspectivesManager must not be NULL.");
        }
        if ( appConfig == null ) {
            throw new IllegalArgumentException("appConfig must not be NULL.");
        }
        this.applicationConfig = appConfig;
        this.perspectivesManager = perspectivesManager;
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
        setColors( tree );
        tree.setRootVisible( false );
        
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        
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

                if ( value instanceof WorkspaceTreeNode) 
                {
                    if ( value instanceof ProjectNode ) 
                    {
                        final ProjectNode projectNode = (ProjectNode) value;
                        final IAssemblyProject project = projectNode.getValue();

                        setText( project.getName() );
                    } 
                    else if ( value instanceof FileNode) 
                    {
                        final FileNode resourceNode = (FileNode) value;
                        final IResource projectResource = getResourceForFile( resourceNode );	                       
                        if ( projectResource != null && projectResource.hasType( ResourceType.EXECUTABLE ) ) 
                        {
                            final File resource = resourceNode.getValue();
                            setText( resource.getName()+" [EXECUTABLE]");
                        } else {
                            setText( resourceNode.getValue().getName() );
                        }
                    } else {
                        setText("");	
                    }
                } else {
                    setText("");
                }
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
    
    private IResource getResourceForFile(FileNode resourceNode) 
    {
        final IAssemblyProject project = getProject( resourceNode );
        return project != null ?  project.getResourceForFile( resourceNode.getValue() ) : null;
    }

    protected void onTreeNodeLeftClick(IAssemblyProject project, WorkspaceTreeNode selected) throws IOException 
    {
        if ( project == null ) {
            throw new IllegalArgumentException("project must not be NULL");
        }

        if ( selected instanceof FileNode) 
        {
            final File file = ((FileNode) selected).getValue();
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

        EditorContainer editorContainer = (EditorContainer ) getViewContainer().getViewByID( EditorContainer.VIEW_ID );

        if ( editorContainer == null ) {
            editorContainer = new EditorContainer("Editors",getViewContainer());
            getViewContainer().addView( editorContainer );
        }

        final IEditorView editor = EditorFactory.createEditor( project , resource );
        editor.openResource( project , resource );
        editorContainer.addView( editor );
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

    protected JPopupMenu createPopupMenu(final WorkspaceTreeNode selectedNode) 
    {
        final JPopupMenu popup = new JPopupMenu();

        // open debugger
        if ( selectedNode instanceof FileNode) 
        {
            final IResource resource = getResourceForFile( (FileNode) selectedNode );
            
            if ( resource != null && resource.hasType( ResourceType.EXECUTABLE ) ) 
            {
                addMenuEntry( popup , "Open in debugger", new ActionListener() {
        
                    @Override
                    public void actionPerformed(ActionEvent e) 
                    {
                        final IAssemblyProject project = getProject( selectedNode);
                        try {
                            openDebugPerspective( project , resource );
                        } 
                        catch (IOException e1) {
                            LOG.error("Failed to open debug perspective for "+project+" , resource "+resource,e1);
                        }
                    }

                });
            }
        }

        final IAssemblyProject project = getProject( selectedNode );
        if ( project != null ) 
        {
            addMenuEntry( popup , "Build project", new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        project.getBuilder().build();
                    } catch (IOException e1) {
                        LOG.error("Failed to build "+project,e1);
                    }
                }
            });		
        }

        return popup;
    }	

    private void openDebugPerspective(IAssemblyProject project, IResource executable) throws IOException
    {
        if ( project == null ) {
            throw new IllegalArgumentException("project must not be NULL.");
        }
        if ( executable == null ) {
            throw new IllegalArgumentException("executable must not be NULL.");
        }

        final List<? extends IViewContainer> perspectives = perspectivesManager.getPerspectives( DebuggingPerspective.ID );
        
        for ( IViewContainer existing : perspectives ) {
            if ( existing instanceof DebuggingPerspective) {
                final DebuggingPerspective p = (DebuggingPerspective) existing;
                if ( p.getCurrentProject() != null && p.getCurrentProject().getName().equals( project.getName() ) ) {
                    p.openExecutable( project , executable );
                    p.toFront();
                    return;
                }
            }
        }
        
        // perspective not visible yet, create it
        final DebuggingPerspective p=new DebuggingPerspective( applicationConfig );
        p.openExecutable( project , executable );
        p.setVisible( true );
        p.pack();
        p.toFront();            
        perspectivesManager.addViewContainer( p );        
    }
    
    private void addMenuEntry(JPopupMenu menu,String title, final ActionListener listener) 
    {
        final JMenuItem menuItem = new JMenuItem(title);
        menuItem.addActionListener( listener );
        menu.add(menuItem);		
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
                if ( path != null ) 
                {
                    final WorkspaceTreeNode node = (WorkspaceTreeNode) path.getLastPathComponent();
                    final JPopupMenu menu = createPopupMenu( node );
                    if ( menu != null ) {
                        menu.show(e.getComponent(),e.getX(), e.getY());
                    }
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
        public void projectDeleted(IAssemblyProject project) {
            treeStructureChanged();
        }

        @Override
        public void resourceChanged(IAssemblyProject project, IResource resource) {
            treeStructureChanged();			
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
                final FileNode dirNode = new FileNode( current );
                n.addChild( dirNode );
                addDirectory(  current , dirNode );
            }

            for ( File f : files ) 
            {
                n.addChild( new FileNode( f  ) );
            }
        }		
    }

    private WorkspaceTreeModel createTreeModel() {
        return new WorkspaceTreeModel();
    }

    protected static class WorkspaceTreeNode implements javax.swing.tree.TreeNode 
    {
        private WorkspaceTreeNode parent;
        private final List<WorkspaceTreeNode> children = new ArrayList<WorkspaceTreeNode>();
        private final Object value;

        protected WorkspaceTreeNode(Object value) {
            this.value = value;
        }

        public void removeChild(FileNode fn) {
            children.remove( fn );
        }

        public Object[] getPathToRoot() 
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

        public void addChild(WorkspaceTreeNode child) {
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

    protected static final class FileNode extends WorkspaceTreeNode {

        public FileNode(File file) {
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

    @Override
    public String getID() {
        return "workspace-explorer";
    }

    @Override
    public boolean mayBeDisposed() {
        return false;
    }
}