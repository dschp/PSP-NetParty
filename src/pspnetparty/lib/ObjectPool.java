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
