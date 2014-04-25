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
package de.codesourcery.jasm16.emulator;

import java.util.*;

import de.codesourcery.jasm16.emulator.devices.IInterrupt;

public final class CopyOnWriteList<T> implements List<T> {

	private ArrayList<T> list = new ArrayList<T>();
	private boolean copyBeforeModify;
	
	public CopyOnWriteList() {
		this.copyBeforeModify = false;
	}
	
	public CopyOnWriteList(CopyOnWriteList<T> other) {
		this.list = other.list;
		this.copyBeforeModify = true;
	}	
	
	@Override
	public int size() {
		return list.size();
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return list.contains(o);
	}

	@Override
	public Iterator<T> iterator() {
		return list.iterator();
	}

	@Override
	public Object[] toArray() {
		return list.toArray();
	}

	@Override
	public <X> X[] toArray(X[] a) {
		return list.toArray(a);
	}

	@Override
	public boolean add(T e) 
	{
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}
		return list.add( e );
	}

	@Override
	public boolean remove(Object o) 
	{
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}
		return list.remove( o );
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return list.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}		
		return this.list.addAll(c);
	}

	@Override
	public boolean addAll(int index, Collection<? extends T> c) 
	{
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}		
		return this.list.addAll(index,c);
	}

	@Override
	public boolean removeAll(Collection<?> c) 
	{
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}		
		return this.list.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) 
	{
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}		
		return this.list.retainAll(c);
	}

	@Override
	public void clear() {
		this.list = new ArrayList<>( this.list );
		copyBeforeModify = false;
	}

	@Override
	public T get(int index) {
		return list.get(index);
	}

	@Override
	public T set(int index, T element) 
	{
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}		
		return list.set(index,element);
	}

	@Override
	public void add(int index, T element) {
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}	
		this.list.add(index,element);
	}

	@Override
	public T remove(int index) {
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}		
		return this.list.remove(index);
	}

	@Override
	public int indexOf(Object o) {
		return list.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return list.lastIndexOf(o);
	}

	@Override
	public ListIterator<T> listIterator() 
	{
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}	
		return this.list.listIterator();
	}

	@Override
	public ListIterator<T> listIterator(int index) {
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}	
		return this.list.listIterator(index);
	}

	@Override
	public List<T> subList(int fromIndex, int toIndex) {
		if ( copyBeforeModify ) {
			this.list = new ArrayList<>( this.list );
			copyBeforeModify = false;
		}	
		return this.list.subList(fromIndex,toIndex);
	}

	public CopyOnWriteList<T> createCopy() {
		this.copyBeforeModify = true;
		return this;
	}
}