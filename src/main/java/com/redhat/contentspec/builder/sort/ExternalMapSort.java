package com.redhat.contentspec.builder.sort;

import java.util.List;
import java.util.Map;

/**
 * Defines a class that can be used to sort a List<V> against a Map<T, U>
 */
public interface ExternalMapSort <T, U, V>
{
	void sort (final Map<T, U> map, List<V> list);
}
