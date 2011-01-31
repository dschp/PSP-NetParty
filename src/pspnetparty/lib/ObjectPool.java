/*
Copyright (C) 2011 monte

This file is part of PSP NetParty.

PSP NetParty is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package pspnetparty.lib;

import java.util.EmptyStackException;
import java.util.Stack;

public class ObjectPool<Type> {
	public interface Factory<Type> {
		public Type createObject();
	}
	
	private Stack<Type> pool = new Stack<Type>();
	private Factory<Type> factory;
	
	public ObjectPool(Factory<Type> factory) {
		this.factory = factory;
	}
	
	public Type borrowObject() {
		try {
			return pool.pop();
		} catch (EmptyStackException e) {
			return factory.createObject();
		}
	}
	
	public void returnObject(Type object) {
		pool.push(object);
	}
}
