package de.codesourcery.jasm16.ide.ui.viewcontainers;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import de.codesourcery.jasm16.compiler.io.IResource;
import de.codesourcery.jasm16.ide.ui.views.AbstractView;
import de.codesourcery.jasm16.ide.ui.views.IEditorView;
import de.codesourcery.jasm16.ide.ui.views.IView;

public class EditorContainer extends AbstractView implements IViewContainer {

	private JPanel panel;
	private final String title;
	
	private final List<ViewWithPanel> views = new ArrayList<ViewWithPanel>();
	private final JTabbedPane tabbedPane = new JTabbedPane();

	protected final class ViewWithPanel 
	{
		public final IView view;
		public final JPanel panel;
		
		public ViewWithPanel(IView view) 
		{
			this.view = view;
			this.panel = view.getPanel( EditorContainer.this );
		}
	}
	
	public EditorContainer(String title, IViewContainer parent) {
		this.title = title;
	}
	
	@Override
	protected JPanel getPanel() {
		if ( panel == null ) {
			panel = createPanel();
		}
		return panel;
	}

	private JPanel createPanel() 
	{
		final JPanel result = new JPanel();
		result.setLayout( new GridBagLayout() );
		
		GridBagConstraints cnstrs = constraints(0 , 0 , true , true , GridBagConstraints.BOTH );
		
		setColors( result );
		setColors( tabbedPane );
		result.add( tabbedPane ,cnstrs );
		return result;
	}

	@Override
	public void addView(IView view) 
	{
		final ViewWithPanel newView = new ViewWithPanel( view );
		views.add( newView );
		tabbedPane.add( view.getTitle() , newView.panel );
	}

	@Override
	public void dispose() 
	{
		final List<ViewWithPanel> copy = new ArrayList<ViewWithPanel>(this.views);
		for ( ViewWithPanel v : copy ) {
			v.view.dispose();
		}
		this.views.clear();
	}

	@Override
	public List<IView> getViews() 
	{
		final List<IView> result = new ArrayList<IView>();
		for ( ViewWithPanel p : this.views ) {
			result.add( p.view );
		}
		return result;
	}

	@Override
	public void removeView(IView view) 
	{
		if (view == null) {
			throw new IllegalArgumentException("view must not be NULL");
		}
		
		final List<ViewWithPanel> copy = new ArrayList<ViewWithPanel>(this.views);
		for (Iterator<ViewWithPanel> it = copy.iterator(); it.hasNext();) 
		{
			final ViewWithPanel viewWithPanel = (ViewWithPanel) it.next();
			if ( viewWithPanel.view == view ) {
				this.tabbedPane.remove( viewWithPanel.panel );
				viewWithPanel.view.dispose();
				return;
			}
		}
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public void refreshDisplay() 
	{
		for ( ViewWithPanel p : this.views ) {
			p.view.refreshDisplay();
		}
	}

	public IEditorView getEditor(IResource resource) 
	{
		if (resource == null) {
			throw new IllegalArgumentException("resource must not be NULL");
		}
		
		for ( ViewWithPanel p : this.views ) 
		{
			if ( p.view instanceof IEditorView) {
				if ( ((IEditorView) p.view).getCurrentResource() == resource ) {
					return (IEditorView) p.view;
				}
			}
		}
		return null;
	}

	@Override
	public boolean mayBeDisposed() 
	{
		boolean result = false;
		for ( ViewWithPanel p : this.views ) 
		{
			if ( p.view instanceof IEditorView) {
				result |= ((IEditorView) p.view).hasUnsavedContent();
			}
		}
		return result;
	}

	@Override
	public String getID() {
		return "editor-container";
	}

}
