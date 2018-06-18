package io.simple.util;

import java.lang.reflect.Constructor;

/**
 * Reflection utils.
 * 
 * @author little-pan
 * @since 0.0.1 2018-06-17
 *
 */
@SuppressWarnings("unchecked")
public final class ReflectUtil {
	
	private ReflectUtil(){}
	
	/**
	 * New the instance of class.
	 */
	public final static <T> T newObject(final String className) {
		try {
			final Class<?> clazz = Class.forName(className);
			return (T)(clazz.newInstance());
		} catch (final Exception e) {
			throw new RuntimeException("Can't new instance of class " + className, e);
		}
	}
	
	public final static <T> T newObject(final Class<?> clazz) {
		try {
			return (T)(clazz.newInstance());
		} catch (final Exception e) {
			throw new RuntimeException("Can't new instance of class " + clazz.getName(), e);
		}
	}

	/**
	 * New the instance of class.
	 * 
	 * @param className
	 * @param classes
	 * @param params
	 * @return the instance of class
	 */
	public static <T> T newObject(String className, Class<?>[] classes, Object[] params) {
		try {
			final Class<?> clazz = Class.forName(className);
			final Constructor<?> construct = clazz.getDeclaredConstructor(classes);
			return (T)(construct.newInstance(params));
		} catch (final Exception e) {
			throw new RuntimeException("Can't new instance of class " + className, e);
		}
	}

}
