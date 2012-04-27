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

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import de.codesourcery.jasm16.ast.AST;
import de.codesourcery.jasm16.ast.ASTNode;

/**
 * Wraps an {@link AST} with a {@link TreeModel}.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ASTTableModelWrapper implements TreeModel {

	private AST ast;
	
	public ASTTableModelWrapper(AST ast) {
		this.ast = ast;
	}

	@Override
	public void addTreeModelListener(TreeModelListener l) {
	}

	@Override
	public Object getChild(Object parent, int index) 
	{
		return ((ASTNode) parent).child(index);
	}

	@Override
	public int getChildCount(Object parent) {
		return ((ASTNode) parent).getChildCount();
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) 
	{
		return ((ASTNode) parent).indexOf( (ASTNode) child );
	}

	@Override
	public Object getRoot() {
		return ast;
	}

	@Override
	public boolean isLeaf(Object node) {
		return ((ASTNode) node).getChildCount() == 0;
	}

	@Override
	public void removeTreeModelListener(TreeModelListener l) {
	}

	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {
	}
	
}
